package com.shamia.assistant.engine

import com.shamia.assistant.model.OpenSourceModelSpec
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

internal object CloudModelResolver {

    @Volatile
    private var cachedLiveCloudIds: List<String> = emptyList()
    @Volatile
    private var lastLiveFetchMs: Long = 0L

    fun isModelIntrospectionQuery(input: String): Boolean {
        val lower = input.lowercase(Locale.US)
        return Regex(
            "\\b(which|what)\\s+(?:ai\\s+)?model\\b|\\bactive\\s+model\\b|\\brunning\\s+on\\s+which\\s+model\\b|\\bwhich\\s+llm\\b",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(lower)
    }

    fun fetchLiveCloudModelIds(): List<String> {
        val now = System.currentTimeMillis()
        if (cachedLiveCloudIds.isNotEmpty() && now - lastLiveFetchMs < 600_000L) {
            return cachedLiveCloudIds
        }
        val fetched = runCatching {
            val conn = (URL("https://text.pollinations.ai/models").openConnection() as HttpURLConnection).apply {
                connectTimeout = 3500
                readTimeout = 4500
                setRequestProperty("Cache-Control", "no-cache")
            }
            val ids = mutableListOf<String>()
            if (conn.responseCode == 200) {
                val arr = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
                for (i in 0 until arr.length()) {
                    val item = arr.opt(i)
                    when (item) {
                        is String -> ids += item
                        is JSONObject -> {
                            item.optString("name").takeIf { it.isNotBlank() }?.let { ids += it }
                            val aliases = item.optJSONArray("aliases")
                            if (aliases != null) {
                                for (j in 0 until aliases.length()) {
                                    aliases.optString(j).takeIf { it.isNotBlank() }?.let { ids += it }
                                }
                            }
                        }
                    }
                }
            }
            conn.disconnect()
            ids.distinct()
        }.getOrDefault(emptyList())
        if (fetched.isNotEmpty()) {
            cachedLiveCloudIds = fetched
            lastLiveFetchMs = now
        }
        return cachedLiveCloudIds
    }

    fun resolveCloudCandidates(spec: OpenSourceModelSpec): List<String> {
        val live = fetchLiveCloudModelIds()
        val desired = mutableListOf<String>()
        desired += spec.cloudModelId.trim()
        when {
            spec.id.contains("gemma") -> desired += listOf("gemini-search", "google/gemini-2.5-flash-lite", "gemini", "gemini-fast")
            spec.id.contains("qwen") -> desired += listOf("qwen-coder", "qwen/qwen3.7-plus", "qwen")
            spec.id.contains("offline-core") -> desired += listOf("deepseek-reasoning", "deepseek/deepseek-v4.1-flash", "deepseek")
            spec.id.contains("gpt-oss") -> desired += listOf("openai-large", "openai/gpt-5-nano", "openai")
            spec.id.contains("llama") -> desired += listOf("llama", "openai-fast", "openai")
            spec.id.contains("minicpm") -> desired += listOf("mistral", "mistralai/mistral-small-4")
            spec.id.contains("phi") -> desired += listOf("phi", "openai-fast", "openai")
            spec.id.contains("glm") -> desired += listOf("qwen", "deepseek", "openai-large")
            spec.id.contains("smollm") -> desired += listOf("openai-fast", "llama", "openai")
        }
        val cleanDesired = desired.filter { it.isNotBlank() }.distinct()
        if (live.isEmpty()) return cleanDesired.take(2)
        val matchedLive = cleanDesired.filter { cand -> live.any { it.equals(cand, ignoreCase = true) } }
        return (matchedLive + cleanDesired).distinct().take(3)
    }
}
