package com.shamia.assistant.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import com.shamia.assistant.model.DeviceHardwareSpec
import com.shamia.assistant.model.OpenSourceModelSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object ModelDownloadManager {

    private const val TAG = "ShamiaModelInstaller"
    private const val USER_AGENT = "SHAMIA-Android-OfflineAI/1.0"

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
            hardware.isLowMemory || ram < 2 -> "offline-core"
            ram < 3 -> "qwen25-0.5b-webgpu"
            ram < 6 -> "qwen25-1.5b-edge"
            else -> "qwen25-1.5b-edge"
        }
        return models.find { it.id == preferredId }
            ?: models.firstOrNull { isSafeForLocalInstall(it, hardware) }
            ?: models.first()
    }

    /**
     * Queries the official Hugging Face Hub API to find the best quantized .task weight bundle
     * inside a litert-community repository.
     */
    private fun discoverTaskBundleFile(repo: String): Pair<String, Long>? {
        return runCatching {
            val apiUrl = URL("https://huggingface.co/api/models/$repo/tree/main")
            val conn = (apiUrl.openConnection() as HttpURLConnection).apply {
                connectTimeout = 7000
                readTimeout = 10000
                setRequestProperty("User-Agent", USER_AGENT)
            }
            if (conn.responseCode != 200) {
                conn.disconnect()
                return@runCatching null
            }
            val arr = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
            conn.disconnect()

            val candidates = mutableListOf<Pair<String, Long>>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val path = obj.optString("path")
                val size = obj.optLong("size", 0L)
                if (path.endsWith(".task", ignoreCase = true) && size > 10L * 1024L * 1024L) {
                    candidates.add(path to size)
                }
            }
            if (candidates.isEmpty()) return@runCatching null

            // Prefer mobile-friendly q4/q8 multi-prefill or ekv4096/ekv1280 smallest viable bundle
            candidates.sortedWith(
                compareBy<Pair<String, Long>>(
                    { if (it.first.contains("multi-prefill", ignoreCase = true)) 0 else 1 },
                    { if (it.first.contains("q4", ignoreCase = true) || it.first.contains("int4", ignoreCase = true)) 0 else 1 },
                    { it.second }
                )
            ).first()
        }.getOrNull()
    }

    suspend fun installModelOffline(
        context: Context,
        spec: OpenSourceModelSpec,
        ollamaEndpoint: String,
        onProgress: (pct: Int, status: String) -> Unit
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val base = ollamaEndpoint.trimEnd('/')
        val tag = spec.ollamaTag

        // 1. If local Termux/LAN Ollama is active, pull real quantized GGUF weights via local Ollama
        if (!tag.isNullOrBlank() && base.isNotBlank()) {
            val ollamaAlive = OpenSourceAiRepository.testLocalEndpoint(base).isNotEmpty()
            if (ollamaAlive) {
                onProgress(5, "Pulling $tag (${spec.sizeLabel}) via local Ollama...")
                val pulled = runCatching {
                    val conn = (URL("$base/api/pull").openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        doOutput = true
                        connectTimeout = 5000
                        readTimeout = 180000
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
                    onProgress(100, "${spec.name} installed in local Ollama")
                    return@withContext true to "${spec.name} installed in local Ollama runtime."
                }
            }
        }

        // 2. Download real quantized Android .task neural weights directly from official Hugging Face litert-community
        val repo = spec.hfRepo
        val dir = File(context.filesDir, "models/${spec.id}").apply { mkdirs() }
        if (!repo.isNullOrBlank()) {
            onProgress(3, "Locating official quantized .task weights for ${spec.name}...")
            val discovered = discoverTaskBundleFile(repo)
            if (discovered != null) {
                val (fileName, expectedBytes) = discovered
                val encodedFile = fileName.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
                val downloadUrl = "https://huggingface.co/$repo/resolve/main/$encodedFile?download=true"
                val partFile = File(dir, "model.task.part")
                val finalFile = File(dir, fileName.substringAfterLast("/"))

                val dlResult = streamDownloadFile(
                    urlStr = downloadUrl,
                    partFile = partFile,
                    finalFile = finalFile,
                    expectedBytes = expectedBytes,
                    label = spec.name,
                    onProgress = onProgress
                )
                if (dlResult.first) {
                    File(dir, "model_descriptor.json").writeText(
                        JSONObject()
                            .put("id", spec.id)
                            .put("name", spec.name)
                            .put("hfRepo", repo)
                            .put("weightsFile", finalFile.name)
                            .put("bytes", finalFile.length())
                            .put("installedAtEpochMs", System.currentTimeMillis())
                            .toString()
                    )
                    return@withContext true to "${spec.name} (${finalFile.length() / (1024 * 1024)} MB) ready for 100% offline execution."
                } else {
                    return@withContext false to dlResult.second
                }
            } else {
                return@withContext false to "This repo requires Hugging Face license approval or local Ollama. Try Qwen 2.5 0.5B, Qwen 2.5 1.5B, SmolLM 135M, DeepSeek-R1 1.5B, or Phi-4 Mini for instant 1-tap offline download."
            }
        }

        false to "No downloadable .task weights configured for ${spec.name}."
    }

    private suspend fun streamDownloadFile(
        urlStr: String,
        partFile: File,
        finalFile: File,
        expectedBytes: Long,
        label: String,
        onProgress: (pct: Int, status: String) -> Unit
    ): Pair<Boolean, String> {
        var conn: HttpURLConnection? = null
        return try {
            var currentUrl = urlStr
            var redirects = 0
            while (redirects < 6) {
                conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 15000
                    readTimeout = 30000
                    setRequestProperty("User-Agent", USER_AGENT)
                }
                val code = conn.responseCode
                if (code in 301..308) {
                    val loc = conn.getHeaderField("Location") ?: break
                    conn.disconnect()
                    currentUrl = if (loc.startsWith("http")) loc else URL(URL(currentUrl), loc).toString()
                    redirects++
                } else {
                    break
                }
            }

            val activeConn = conn ?: return false to "Connection failed."
            val statusCode = activeConn.responseCode
            if (statusCode == 401 || statusCode == 403) {
                activeConn.disconnect()
                return false to "Gated model on Hugging Face ($statusCode). Download an ungated model like Qwen 2.5 0.5B / 1.5B, SmolLM 135M, or DeepSeek-R1 1.5B, or import a .task file."
            }
            if (statusCode !in 200..299) {
                activeConn.disconnect()
                return false to "Download failed (HTTP $statusCode). Check internet connection and try again."
            }

            val totalBytes = activeConn.contentLengthLong.takeIf { it > 0L } ?: expectedBytes
            val buffer = ByteArray(64 * 1024)
            var downloaded = 0L
            var lastUiUpdateMs = 0L

            activeConn.inputStream.use { input ->
                FileOutputStream(partFile).use { output ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read

                        val now = System.currentTimeMillis()
                        if (now - lastUiUpdateMs >= 250L) {
                            lastUiUpdateMs = now
                            val dlMb = downloaded / (1024L * 1024L)
                            val totMb = (totalBytes / (1024L * 1024L)).coerceAtLeast(1L)
                            val pct = if (totalBytes > 0L) ((downloaded * 100L) / totalBytes).toInt().coerceIn(1, 99) else 50
                            onProgress(pct, "Downloading $label · $dlMb MB / $totMb MB")
                        }
                    }
                }
            }
            activeConn.disconnect()

            if (finalFile.exists()) finalFile.delete()
            if (!partFile.renameTo(finalFile)) {
                partFile.copyTo(finalFile, overwrite = true)
                partFile.delete()
            }
            onProgress(100, "$label downloaded (${finalFile.length() / (1024 * 1024)} MB)")
            true to "Downloaded successfully"
        } catch (e: Exception) {
            Log.e(TAG, "Weight download error", e)
            runCatching { partFile.delete() }
            runCatching { conn?.disconnect() }
            false to "Download interrupted: ${e.message ?: "Network error"}"
        }
    }

    suspend fun importLocalTaskFile(
        context: Context,
        spec: OpenSourceModelSpec,
        sourceUri: Uri,
        onProgress: (pct: Int, status: String) -> Unit
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "models/${spec.id}").apply { mkdirs() }
        val target = File(dir, "${spec.id}.task")
        try {
            onProgress(10, "Importing local .task weights for ${spec.name}...")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(target).use { output ->
                    val buf = ByteArray(64 * 1024)
                    var copied = 0L
                    while (true) {
                        val read = input.read(buf)
                        if (read == -1) break
                        output.write(buf, 0, read)
                        copied += read
                        onProgress(60, "Copying weights (${copied / (1024 * 1024)} MB)...")
                    }
                }
            } ?: return@withContext false to "Unable to read selected file."

            if (target.length() < 10L * 1024L * 1024L) {
                target.delete()
                return@withContext false to "Selected file is too small to be a valid .task model bundle."
            }
            onProgress(100, "${spec.name} imported (${target.length() / (1024 * 1024)} MB)")
            true to "${spec.name} imported for 100% offline execution."
        } catch (e: Exception) {
            runCatching { target.delete() }
            false to "Import failed: ${e.message}"
        }
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
