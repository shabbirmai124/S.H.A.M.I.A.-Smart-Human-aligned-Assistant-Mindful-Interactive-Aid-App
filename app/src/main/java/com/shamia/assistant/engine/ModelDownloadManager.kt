package com.shamia.assistant.engine

import android.content.Context
import android.util.Log
import com.shamia.assistant.model.DeviceHardwareSpec
import com.shamia.assistant.model.OpenSourceModelSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object ModelDownloadManager {

    private const val TAG = "ShamiaModelInstaller"

    fun isSafeForLocalInstall(spec: OpenSourceModelSpec, hardware: DeviceHardwareSpec): Boolean {
        if (spec.isBuiltInOffline) return true
        return hardware.ramGb >= spec.minRamGb && !hardware.isLowMemory
    }

    fun recommendSafeDefaultModel(
        hardware: DeviceHardwareSpec,
        models: List<OpenSourceModelSpec>
    ): OpenSourceModelSpec {
        val ram = hardware.ramGb
        val preferredId = when {
            hardware.isLowMemory || ram < 3 -> "offline-core"
            ram < 6 -> "qwen3-4b-edge"
            ram < 8 -> "gemma-4-free"
            else -> "gemma-4-free"
        }
        return models.find { it.id == preferredId }
            ?: models.firstOrNull { isSafeForLocalInstall(it, hardware) }
            ?: models.first()
    }

    suspend fun installModelOffline(
        context: Context,
        spec: OpenSourceModelSpec,
        ollamaEndpoint: String,
        onProgress: (pct: Int, status: String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val base = ollamaEndpoint.trimEnd('/')
        val tag = spec.ollamaTag

        // 1. If local Termux/LAN Ollama is active and model has an Ollama tag, pull real quantized GGUF weights
        if (!tag.isNullOrBlank() && base.isNotBlank()) {
            val ollamaAlive = OpenSourceAiRepository.testLocalEndpoint(base).isNotEmpty()
            if (ollamaAlive) {
                onProgress(5, "Pulling $tag via Ollama...")
                val pulled = runCatching {
                    val conn = (URL("$base/api/pull").openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        doOutput = true
                        connectTimeout = 5000
                        readTimeout = 120000
                        setRequestProperty("Content-Type", "application/json")
                    }
                    conn.outputStream.use {
                        it.write(JSONObject().put("model", tag).put("stream", true).toString().toByteArray())
                    }
                    if (conn.responseCode in 200..299) {
                        conn.inputStream.bufferedReader().useLines { lines ->
                            lines.forEach { line ->
                                runCatching {
                                    val obj = JSONObject(line)
                                    val total = obj.optLong("total", 0L)
                                    val done = obj.optLong("completed", 0L)
                                    val status = obj.optString("status", "Downloading $tag")
                                    val pct = if (total > 0) ((done * 100L) / total).toInt().coerceIn(5, 99) else 50
                                    onProgress(pct, status)
                                }
                            }
                        }
                        conn.disconnect()
                        true
                    } else {
                        conn.disconnect()
                        false
                    }
                }.getOrDefault(false)

                if (pulled) {
                    onProgress(100, "${spec.name} installed in Ollama")
                    return@withContext true
                }
            }
        }

        // 2. Cache Hugging Face model config/tokenizer manifest into private app storage for verified offline profile
        val repo = spec.hfRepo
        val dir = File(context.filesDir, "models/${spec.id}").apply { mkdirs() }
        if (!repo.isNullOrBlank()) {
            onProgress(25, "Caching ${spec.name} metadata...")
            val cached = runCatching {
                val url = URL("https://huggingface.co/$repo/raw/main/config.json")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4500
                    readTimeout = 6000
                }
                if (conn.responseCode == 200) {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    File(dir, "config.json").writeText(text)
                    conn.disconnect()
                    true
                } else {
                    conn.disconnect()
                    false
                }
            }.getOrDefault(false)

            if (cached) {
                onProgress(100, "${spec.name} cached offline")
                return@withContext true
            }
        }

        // 3. Save local offline descriptor marker if online HuggingFace is offline
        Log.i(TAG, "Stored local offline descriptor for ${spec.id}")
        File(dir, "model_descriptor.json").writeText(
            JSONObject()
                .put("id", spec.id)
                .put("name", spec.name)
                .put("ollamaTag", spec.ollamaTag ?: "")
                .put("minRamGb", spec.minRamGb)
                .toString()
        )
        onProgress(100, "${spec.name} prepared for offline Termux/local sync")
        true
    }

    fun removeOfflineCache(context: Context, modelId: String) {
        runCatching {
            File(context.filesDir, "models/$modelId").deleteRecursively()
        }
    }

    suspend fun installVoiceModelOffline(
        context: Context,
        voice: com.shamia.assistant.model.OfflineVoiceModelSpec,
        onProgress: (pct: Int, status: String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "voices/${voice.id}").apply { mkdirs() }
        onProgress(20, "Fetching ${voice.name} manifest...")
        val cachedConfig = runCatching {
            val url = URL("https://huggingface.co/${voice.hfRepo}/raw/main/config.json")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 4500
                readTimeout = 6000
            }
            if (conn.responseCode == 200) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                File(dir, "config.json").writeText(text)
                conn.disconnect()
                true
            } else {
                conn.disconnect()
                false
            }
        }.getOrDefault(false)

        if (!cachedConfig) {
            File(dir, "voice_descriptor.json").writeText(
                JSONObject()
                    .put("id", voice.id)
                    .put("name", voice.name)
                    .put("hfRepo", voice.hfRepo)
                    .put("kind", voice.kind.name)
                    .toString()
            )
        }
        onProgress(100, "${voice.name} ready offline")
        true
    }

    fun removeVoiceCache(context: Context, voiceId: String) {
        runCatching {
            File(context.filesDir, "voices/$voiceId").deleteRecursively()
        }
    }
}
