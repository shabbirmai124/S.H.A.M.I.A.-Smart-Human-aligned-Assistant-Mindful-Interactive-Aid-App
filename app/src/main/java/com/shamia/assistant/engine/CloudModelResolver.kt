package com.shamia.assistant.engine

import com.shamia.assistant.model.OpenSourceModelSpec
import com.shamia.assistant.model.UserIdentityProfile
import java.util.Locale

internal object CloudModelResolver {

    fun isModelIntrospectionQuery(input: String): Boolean {
        val lower = input.lowercase(Locale.US).trim()
        return Regex(
            "\\b(which|what)\\s+(?:ai\\s+|llm\\s+)?model\\b|\\bactive\\s+model\\b|\\bcurrent\\s+model\\b|\\brunning\\s+on\\s+which\\s+model\\b|\\bwhich\\s+llm\\b|\\bwhat\\s+llm\\b|\\bmodel\\s+id\\b|\\bare\\s+you\\s+(?:deepseek|gemma|gpt|qwen|llama|phi|glm|minicpm)\\b|\\bwho\\s+are\\s+you\\s+and\\s+which\\s+model\\b",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(lower)
    }

    /**
     * Resolves strict, zero-latency routing candidates for the selected model spec.
     * Never routes non-DeepSeek models to DeepSeek.
     */
    fun resolveCloudCandidates(spec: OpenSourceModelSpec): List<String> {
        val desired = mutableListOf<String>()
        when {
            spec.id == "gemma-4-free" -> desired += listOf("gemini", "searchgpt", "openai-fast", "openai")
            spec.id.contains("gemma") -> desired += listOf("gemini", "openai-fast", "openai")
            spec.id.contains("gpt-oss") -> desired += listOf("openai-large", "openai", "openai-fast")
            spec.id == "qwen3-8b-chinese" -> desired += listOf("qwen-coder", "openai-large", "openai")
            spec.id.contains("qwen") -> desired += listOf("qwen-coder", "openai-fast", "openai")
            spec.id.contains("minicpm") -> desired += listOf("mistral", "openai-fast", "openai")
            spec.id.contains("glm") -> desired += listOf("openai-large", "qwen-coder", "openai")
            spec.id.contains("llama") -> desired += listOf("llama", "openai-fast", "openai")
            spec.id.contains("phi") -> desired += listOf("openai-fast", "mistral", "openai")
            spec.id.contains("smollm") -> desired += listOf("openai-fast", "llama", "openai")
            spec.id.contains("offline-core") -> desired += listOf("openai-fast", "openai")
            else -> {
                if (spec.cloudModelId.isNotBlank()) desired += spec.cloudModelId.trim()
                desired += listOf("openai-fast", "openai")
            }
        }
        return desired.filter { it.isNotBlank() }.distinct().take(3)
    }

    fun stripHeaderFromHistoryText(text: String): String {
        return text
            .replace(Regex("^\\s*\\[[^\\]]*?(?:Model|Offline Core|ID:|Backend:)[^\\]]*\\]\\s*", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    /**
     * Cleans HTML entities, strips robotic bracketed headers, and prevents stale model names
     * from appearing in the natural human-toned assistant response.
     */
    fun sanitizeReplyForActiveModel(
        rawText: String,
        activeSpec: OpenSourceModelSpec,
        identity: UserIdentityProfile,
        executedBackendId: String
    ): String {
        val ownerName = identity.fullName.trim().ifEmpty { "Md Shabbir Mia" }
        var cleaned = WebSearchClient.decodeHtmlEntities(rawText.trim(), collapseNewlines = false)

        // Remove any leading bracketed model/backend debug tag or Termux pull instructions
        cleaned = cleaned
            .replace(Regex("^\\s*\\[(?:Active\\s+)?Model:[^\\]]+\\]\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^\\s*\\[[^\\]]*?(?:Offline Core|ID:\\s*offline-core|DeepSeek\\s*2B|Web Grounded Fallback|Local Mode)[^\\]]*\\]\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("For full .*? inference offline in Termux.*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("Live Google & Web Search found for you[^:]*:\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("Here is what live open web search found[^:]*:\\s*", RegexOption.IGNORE_CASE), "")
            .trim()

        if (!activeSpec.isBuiltInOffline && activeSpec.id != "offline-core") {
            cleaned = cleaned
                .replace("DeepSeek 2B · S.H.A.M.I.A. Offline Core", activeSpec.name, ignoreCase = true)
                .replace("S.H.A.M.I.A. Offline Core", activeSpec.name, ignoreCase = true)
                .replace("DeepSeek 2B", activeSpec.name, ignoreCase = true)
                .replace("`offline-core`", "`${activeSpec.id}`", ignoreCase = true)
                .replace("(model ID: offline-core)", "(model ID: `${activeSpec.id}`)", ignoreCase = true)
                .replace("ID: offline-core", "ID: ${activeSpec.id}", ignoreCase = true)
                .replace("deepseek-r1:1.5b", executedBackendId, ignoreCase = true)
        }

        if (!ownerName.equals("Kajol", ignoreCase = true)) {
            cleaned = cleaned.replace(
                Regex("\\bcreated\\s+by\\s+and\\s+for\\s+Kajol\\b", RegexOption.IGNORE_CASE),
                "created by and for $ownerName"
            )
        }

        return cleaned.trim()
    }
}
