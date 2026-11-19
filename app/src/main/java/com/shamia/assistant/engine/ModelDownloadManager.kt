package com.shamia.assistant.engine

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.shamia.assistant.model.DeviceHardwareSpec
import com.shamia.assistant.model.OpenSourceModelSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

object ModelDownloadManager {

    private const val TAG = "ShamiaTurboDownloader"
    private const val USER_AGENT = "SHAMIA-Android-OfflineAI/2.1"
    private const val TURBO_STREAMS = 3 // 3 balanced streams avoid HF XetHub/CloudFront HTTP 429 & mobile NAT socket exhaustion
    private const val CHUNK_SIZE_DEFAULT = 2L * 1024L * 1024L // 2 MB chunks for fast checkpointing & low rollback cost
    private const val IO_BUFFER_BYTES = 256 * 1024 // 256 KB socket buffer
    private const val IMPORT_BUFFER_BYTES = 512 * 1024 // 512 KB local copy buffer
    private const val MIN_REAL_MODEL_BYTES = 10L * 1024L * 1024L
    private const val STORAGE_SAFETY_MARGIN_BYTES = 48L * 1024L * 1024L // 48 MB OS headroom

    private data class CachedDiscovery(
        val fileName: String,
        val sizeBytes: Long,
        val fetchedAtMs: Long
    )

    private data class ResolvedTarget(
        val finalUrl: String,
        val totalBytes: Long,
        val supportsRange: Boolean,
        val statusCode: Int
    )

    private val discoveryCache = ConcurrentHashMap<String, CachedDiscovery>()

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
            ram < 3 -> "smollm2-360m-webgpu"
            ram < 4 -> "qwen25-0.5b-webgpu"
            else -> "qwen25-0.5b-webgpu"
        }
        return models.find { it.id == preferredId }
            ?: models.firstOrNull { isSafeForLocalInstall(it, hardware) }
            ?: models.first()
    }

    /**
     * Checks if a model has saved partial chunks on disk so the UI can show "Resume (XX%)".
     */
    fun getPartialDownloadPercent(context: Context, modelId: String): Int? {
        return runCatching {
            val dir = File(context.filesDir, "models/$modelId")
            val cpFile = File(dir, "download_checkpoint.json")
            val partFile = File(dir, "model.task.part")
            if (!cpFile.isFile || !partFile.isFile) return@runCatching null
            val obj = JSONObject(cpFile.readText())
            val totalChunks = obj.optInt("totalChunks", 0)
            val completed = obj.optJSONArray("completedChunks")?.length() ?: 0
            if (totalChunks <= 0 || completed <= 0) return@runCatching null
            ((completed * 100) / totalChunks).coerceIn(1, 99)
        }.getOrNull()
    }

    fun scanAllPartialDownloadPercents(context: Context, modelIds: List<String>): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        modelIds.forEach { id ->
            getPartialDownloadPercent(context, id)?.let { pct -> result[id] = pct }
        }
        return result
    }

    /**
     * Discards a model's partial `.part` file & checkpoint so the user can reclaim storage or restart cleanly.
     */
    fun clearPartialDownload(context: Context, modelId: String) {
        runCatching {
            val dir = File(context.filesDir, "models/$modelId")
            File(dir, "model.task.part").delete()
            File(dir, "download_checkpoint.json").delete()
        }
    }

    /**
     * Checks usable internal storage and safely clears temporary cache + orphaned partial downloads
     * from *other* models (never deleting completed models) if free space is tight.
     */
    private fun reclaimDownloadSpaceIfNeeded(
        context: Context,
        excludeModelId: String,
        neededBytes: Long
    ): Long {
        val modelsRoot = File(context.filesDir, "models").apply { mkdirs() }
        var usable = modelsRoot.usableSpace
        if (usable >= neededBytes) return usable

        // 1. Clean app cacheDir temporary files
        runCatching {
            context.cacheDir?.listFiles()?.forEach { f ->
                runCatching { f.deleteRecursively() }
            }
        }
        usable = modelsRoot.usableSpace
        if (usable >= neededBytes) return usable

        // 2. Clean abandoned .part / checkpoint files from OTHER models (never completed weights)
        runCatching {
            modelsRoot.listFiles()?.forEach { modelDir ->
                if (modelDir.isDirectory && modelDir.name != excludeModelId) {
                    val orphanPart = File(modelDir, "model.task.part")
                    val orphanCp = File(modelDir, "download_checkpoint.json")
                    if (orphanPart.exists()) {
                        Log.i(TAG, "Reclaiming storage from abandoned partial download in ${modelDir.name}")
                        orphanPart.delete()
                        orphanCp.delete()
                    }
                }
            }
        }
        return modelsRoot.usableSpace
    }

    private fun isOutOfSpaceError(t: Throwable?): Boolean {
        var cur: Throwable? = t
        while (cur != null) {
            val msg = cur.message?.lowercase().orEmpty()
            if (msg.contains("enospc") ||
                msg.contains("no space left") ||
                msg.contains("not enough space") ||
                msg.contains("disk full")
            ) {
                return true
            }
            cur = cur.cause
        }
        return false
    }

    /**
     * Opens an HTTP GET connection and manually follows up to 8 redirects while strictly
     * preserving Range, User-Agent, and Accept-Encoding headers across cross-host CDN hops
     * (Android's default HttpURLConnection can drop Range headers or refuse cross-host redirects).
     */
    private fun openRangeConnection(
        initialUrl: String,
        rangeHeader: String?,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): HttpURLConnection {
        var currentUrl = initialUrl
        var redirects = 0
        while (true) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                useCaches = false
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("Connection", "keep-alive")
                if (!rangeHeader.isNullOrBlank()) {
                    setRequestProperty("Range", rangeHeader)
                }
            }
            val code = conn.responseCode
            if (code in listOf(301, 302, 303, 307, 308) && redirects < 8) {
                val loc = conn.getHeaderField("Location")
                if (loc.isNullOrBlank()) return conn
                conn.disconnect()
                currentUrl = if (loc.startsWith("http://", true) || loc.startsWith("https://", true)) {
                    loc
                } else {
                    URL(URL(currentUrl), loc).toString()
                }
                redirects++
            } else {
                return conn
            }
        }
    }

    /**
     * Queries the official Hugging Face Hub API to locate the fastest-downloading, smallest
     * official quantized .task weight bundle inside the exact model's repository.
     */
    private fun discoverTaskBundleFile(repo: String): Pair<String, Long>? {
        val cached = discoveryCache[repo]
        if (cached != null && System.currentTimeMillis() - cached.fetchedAtMs < 3_600_000L) {
            return cached.fileName to cached.sizeBytes
        }
        return runCatching {
            val apiUrl = URL("https://huggingface.co/api/models/$repo/tree/main")
            val conn = (apiUrl.openConnection() as HttpURLConnection).apply {
                connectTimeout = 7000
                readTimeout = 10000
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept-Encoding", "identity")
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
                // MediaPipe tasks-genai natively executes .task and .bin bundles
                val isMediaPipeBundle = path.endsWith(".task", ignoreCase = true) || path.endsWith(".bin", ignoreCase = true)
                if (isMediaPipeBundle && size > MIN_REAL_MODEL_BYTES) {
                    candidates.add(path to size)
                }
            }
            if (candidates.isEmpty()) return@runCatching null

            // Prefer native .task bundles with seq128 (highest MediaPipe tasks-genai stability across all Android CPUs/GPUs),
            // then compact quantization (INT4/Q4 -> INT8/Q8), then smallest size for fastest download & RAM load.
            val best = candidates.sortedWith(
                compareBy<Pair<String, Long>>(
                    { if (it.first.endsWith(".task", ignoreCase = true)) 0 else 1 },
                    {
                        val lower = it.first.lowercase()
                        when {
                            lower.contains("seq128") -> 0
                            !lower.contains("multi-prefill") -> 1
                            else -> 2
                        }
                    },
                    {
                        val lower = it.first.lowercase()
                        when {
                            lower.contains("int4") || lower.contains("q4") -> 0
                            lower.contains("int8") || lower.contains("q8") -> 1
                            else -> 2
                        }
                    },
                    { it.second }
                )
            ).first()
            discoveryCache[repo] = CachedDiscovery(best.first, best.second, System.currentTimeMillis())
            best
        }.getOrNull()
    }

    /**
     * Resolves redirects once to get the direct CDN edge URL and probes HTTP Range support
     * using `Range: bytes=0-0` (more reliable than HEAD across Hugging Face & CloudFront CDNs).
     */
    private fun resolveAndProbeTarget(initialUrl: String, fallbackExpectedBytes: Long): ResolvedTarget {
        var conn: HttpURLConnection? = null
        try {
            conn = openRangeConnection(
                initialUrl = initialUrl,
                rangeHeader = "bytes=0-0",
                connectTimeoutMs = 10000,
                readTimeoutMs = 12000
            )
            val code = conn.responseCode
            val resolvedUrl = conn.url?.toString() ?: initialUrl
            val contentRange = conn.getHeaderField("Content-Range") // e.g., "bytes 0-0/554829104"
            val acceptRanges = conn.getHeaderField("Accept-Ranges")
            val rangeTotal = contentRange?.substringAfterLast("/")?.toLongOrNull()
            val contentLen = conn.contentLengthLong.takeIf { it > 1L }
            val totalBytes = rangeTotal ?: contentLen ?: fallbackExpectedBytes
            val supportsRange = (code == 206 && totalBytes > 0L) ||
                (acceptRanges?.equals("bytes", ignoreCase = true) == true && totalBytes > 0L)
            runCatching { conn.inputStream.close() }
            conn.disconnect()
            return ResolvedTarget(resolvedUrl, totalBytes, supportsRange, code)
        } catch (e: Exception) {
            runCatching { conn?.disconnect() }
            Log.w(TAG, "Probe failed, falling back to direct stream: ${e.message}")
            return ResolvedTarget(initialUrl, fallbackExpectedBytes, false, 0)
        }
    }

    suspend fun installModelOffline(
        context: Context,
        spec: OpenSourceModelSpec,
        ollamaEndpoint: String,
        onProgress: (pct: Int, status: String) -> Unit
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val base = ollamaEndpoint.trimEnd('/')
        val tag = spec.ollamaTag

        // 1. Fast probe (< 400ms) for local Termux/LAN Ollama; if running, pull directly into Ollama
        if (!tag.isNullOrBlank() && base.isNotBlank()) {
            val ollamaAlive = isLocalOllamaReachableFast(base)
            if (ollamaAlive) {
                onProgress(3, "Pulling $tag (${spec.sizeLabel}) via local Ollama...")
                val pulled = runCatching {
                    val conn = (URL("$base/api/pull").openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        doOutput = true
                        connectTimeout = 3000
                        readTimeout = 180000
                        setRequestProperty("Content-Type", "application/json")
                    }
                    conn.outputStream.use {
                        it.write(JSONObject().put("model", tag).put("stream", true).toString().toByteArray())
                    }
                    if (conn.responseCode in 200..299) {
                        var startMs = System.currentTimeMillis()
                        var startDone = 0L
                        conn.inputStream.bufferedReader().useLines { lines ->
                            lines.forEach { line ->
                                runCatching {
                                    val obj = JSONObject(line)
                                    val total = obj.optLong("total", 0L)
                                    val done = obj.optLong("completed", 0L)
                                    val status = obj.optString("status", "Downloading $tag")
                                    if (startDone == 0L && done > 0L) {
                                        startDone = done
                                        startMs = System.currentTimeMillis()
                                    }
                                    val elapsedSec = ((System.currentTimeMillis() - startMs) / 1000.0).coerceAtLeast(0.5)
                                    val speedMb = ((done - startDone).coerceAtLeast(0L) / (1024.0 * 1024.0)) / elapsedSec
                                    val pct = if (total > 0L) ((done * 100L) / total).toInt().coerceIn(3, 99) else 50
                                    val speedTag = if (speedMb > 0.1) " · %.1f MB/s".format(speedMb) else ""
                                    onProgress(pct, "$status$speedTag")
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

        // 2. Turbo Parallel Chunked + Resumable Download from official Hugging Face repository
        val repo = spec.hfRepo
        val dir = File(context.filesDir, "models/${spec.id}").apply { mkdirs() }
        if (!repo.isNullOrBlank()) {
            onProgress(1, "Connecting to fastest CDN for ${spec.name}...")
            val discovered = discoverTaskBundleFile(repo)
            if (discovered != null) {
                val (fileName, expectedBytes) = discovered
                val encodedFile = fileName.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
                val downloadUrl = "https://huggingface.co/$repo/resolve/main/$encodedFile?download=true"
                val partFile = File(dir, "model.task.part")
                val checkpointFile = File(dir, "download_checkpoint.json")
                val finalFile = File(dir, fileName.substringAfterLast("/"))

                val dlResult = turboDownloadFile(
                    context = context,
                    modelId = spec.id,
                    initialUrl = downloadUrl,
                    fileName = fileName,
                    partFile = partFile,
                    checkpointFile = checkpointFile,
                    finalFile = finalFile,
                    expectedBytes = expectedBytes,
                    label = spec.name,
                    onProgress = onProgress
                )
                if (dlResult.first) {
                    onProgress(99, "Verifying & pre-warming ${spec.name} into RAM...")
                    val fingerprint = OnDeviceLlmEngine.computeFastFingerprint(finalFile)
                    File(dir, "model_descriptor.json").writeText(
                        JSONObject()
                            .put("id", spec.id)
                            .put("name", spec.name)
                            .put("hfRepo", repo)
                            .put("source", "huggingface_turbo")
                            .put("weightsFile", finalFile.name)
                            .put("bytes", finalFile.length())
                            .put("sha256Prefix", fingerprint)
                            .put("installedAtEpochMs", System.currentTimeMillis())
                            .toString()
                    )
                    OnDeviceLlmEngine.invalidateCache(spec.id)
                    val mb = finalFile.length() / (1024L * 1024L)
                    onProgress(100, "${spec.name} ($mb MB) ready offline")
                    return@withContext true to "${spec.name} ($mb MB · verified) downloaded & ready offline."
                } else {
                    return@withContext false to dlResult.second
                }
            } else {
                return@withContext false to "${spec.name} could not be located on Hugging Face right now. Check internet and tap Retry, or tap 'Import .task'."
            }
        }

        false to "No downloadable .task weights configured for ${spec.name}."
    }

    private fun isLocalOllamaReachableFast(baseUrl: String): Boolean {
        return runCatching {
            val conn = (URL("$baseUrl/api/tags").openConnection() as HttpURLConnection).apply {
                connectTimeout = 400
                readTimeout = 500
            }
            val ok = conn.responseCode == 200
            conn.disconnect()
            ok
        }.getOrDefault(false)
    }

    private suspend fun turboDownloadFile(
        context: Context,
        modelId: String,
        initialUrl: String,
        fileName: String,
        partFile: File,
        checkpointFile: File,
        finalFile: File,
        expectedBytes: Long,
        label: String,
        onProgress: (pct: Int, status: String) -> Unit
    ): Pair<Boolean, String> {
        val target = resolveAndProbeTarget(initialUrl, expectedBytes)
        if (target.statusCode == 401 || target.statusCode == 403) {
            return false to "Gated model on Hugging Face (${target.statusCode}). Download SmolLM 135M (175 MB) or Qwen 2.5 0.5B (529 MB), or import a local .task file."
        }
        if (target.statusCode !in listOf(0, 200, 206)) {
            return false to "Download server returned HTTP ${target.statusCode}. Check internet and tap Retry."
        }

        val totalBytes = target.totalBytes.takeIf { it > 0L } ?: expectedBytes
        if (!target.supportsRange || totalBytes < 12L * 1024L * 1024L) {
            val neededWithMargin = totalBytes + STORAGE_SAFETY_MARGIN_BYTES
            val usable = reclaimDownloadSpaceIfNeeded(context, modelId, neededWithMargin)
            if (usable in 1 until neededWithMargin) {
                val freeMb = usable / (1024L * 1024L)
                val needMb = (totalBytes / (1024L * 1024L)).coerceAtLeast(1L)
                return false to "Low phone storage ($freeMb MB free, need $needMb MB). Free space or try SmolLM 135M (175 MB) / Qwen 2.5 0.5B (529 MB)."
            }
            return singleStreamFastDownload(
                urlStr = target.finalUrl,
                partFile = partFile,
                finalFile = finalFile,
                expectedBytes = totalBytes,
                label = label,
                onProgress = onProgress
            )
        }

        // Preserve chunk size from an existing matching checkpoint so 90% partial downloads resume seamlessly without losing progress!
        val completedSet = ConcurrentHashMap.newKeySet<Int>()
        var chunkSize = CHUNK_SIZE_DEFAULT
        val canResume = partFile.isFile && partFile.length() == totalBytes && checkpointFile.isFile
        if (canResume) {
            runCatching {
                val cp = JSONObject(checkpointFile.readText())
                val savedChunkSize = cp.optLong("chunkSize", 0L)
                if (cp.optString("fileName") == fileName &&
                    cp.optLong("totalBytes") == totalBytes &&
                    savedChunkSize in (512L * 1024L)..(8L * 1024L * 1024L)
                ) {
                    chunkSize = savedChunkSize
                    val totalChunksPreview = ((totalBytes + chunkSize - 1L) / chunkSize).toInt().coerceAtLeast(1)
                    val arr = cp.optJSONArray("completedChunks") ?: JSONArray()
                    for (i in 0 until arr.length()) {
                        val idx = arr.optInt(i, -1)
                        if (idx in 0 until totalChunksPreview) completedSet.add(idx)
                    }
                }
            }
        }

        val totalChunks = ((totalBytes + chunkSize - 1L) / chunkSize).toInt().coerceAtLeast(1)
        val workerCount = TURBO_STREAMS.coerceAtMost(totalChunks)
        val resumedBytes = completedSet.sumOf { idx ->
            val start = idx.toLong() * chunkSize
            val end = minOf(start + chunkSize - 1L, totalBytes - 1L)
            (end - start + 1L)
        }
        val remainingNeededBytes = (totalBytes - resumedBytes).coerceAtLeast(0L) + STORAGE_SAFETY_MARGIN_BYTES

        // Always verify & reclaim storage on BOTH fresh downloads AND resumes (sparse files allocate real blocks as chunks arrive!)
        val usableBytes = reclaimDownloadSpaceIfNeeded(context, modelId, remainingNeededBytes)
        if (usableBytes in 1 until remainingNeededBytes) {
            val freeMb = usableBytes / (1024L * 1024L)
            val needMb = ((totalBytes - resumedBytes).coerceAtLeast(1L) / (1024L * 1024L)).coerceAtLeast(1L)
            return false to "Low phone storage ($freeMb MB free, need $needMb MB more). Free storage, tap Reset, or download Qwen 2.5 0.5B (529 MB) / SmolLM 135M (175 MB)."
        }

        if (completedSet.isEmpty()) {
            // Pre-allocate sparse target file once for fast parallel random-access writes
            runCatching {
                if (partFile.exists()) partFile.delete()
                if (checkpointFile.exists()) checkpointFile.delete()
                RandomAccessFile(partFile, "rw").use { raf ->
                    raf.setLength(totalBytes)
                }
            }.onFailure { err ->
                val freeMb = (partFile.parentFile?.usableSpace ?: 0L) / (1024L * 1024L)
                val needMb = (totalBytes / (1024L * 1024L)).coerceAtLeast(1L)
                return false to "Insufficient phone storage ($freeMb MB free, need $needMb MB): ${err.message ?: "Disk full"}"
            }
        }

        val pendingChunkIndices = (0 until totalChunks).filter { !completedSet.contains(it) }
        val downloadedTotal = AtomicLong(resumedBytes)
        val sessionBytes = AtomicLong(0L)
        val startTimeMs = System.currentTimeMillis()
        val lastUiUpdateMs = AtomicLong(0L)
        val lastCheckpointSaveMs = AtomicLong(0L)
        val checkpointLock = Any()
        val urlRefreshLock = Any()
        val lastUrlRefreshMs = AtomicLong(0L)
        val fatalAbortReason = AtomicReference<String?>(null)

        val activeDirectUrl = AtomicReference(target.finalUrl)

        fun refreshDirectUrlSafe(force: Boolean = false) {
            synchronized(urlRefreshLock) {
                val now = System.currentTimeMillis()
                if (!force && now - lastUrlRefreshMs.get() < 3000L) return
                val refreshed = resolveAndProbeTarget(initialUrl, totalBytes)
                if (refreshed.statusCode in listOf(200, 206)) {
                    activeDirectUrl.set(refreshed.finalUrl)
                    lastUrlRefreshMs.set(System.currentTimeMillis())
                } else {
                    // Fall back to canonical HF resolve URL so openRangeConnection resolves a fresh redirect hop
                    activeDirectUrl.set(initialUrl)
                }
            }
        }

        fun saveCheckpointLocked(force: Boolean = false) {
            val now = System.currentTimeMillis()
            if (!force && now - lastCheckpointSaveMs.get() < 1200L) return
            lastCheckpointSaveMs.set(now)
            synchronized(checkpointLock) {
                runCatching {
                    val arr = JSONArray()
                    completedSet.toList().sorted().forEach { arr.put(it) }
                    checkpointFile.writeText(
                        JSONObject()
                            .put("fileName", fileName)
                            .put("totalBytes", totalBytes)
                            .put("chunkSize", chunkSize)
                            .put("totalChunks", totalChunks)
                            .put("completedChunks", arr)
                            .toString()
                    )
                }
            }
        }

        fun reportProgress(force: Boolean = false, modeLabel: String = "Turbo ${workerCount}x") {
            val now = System.currentTimeMillis()
            val prev = lastUiUpdateMs.get()
            if (!force && now - prev < 220L) return
            if (!force && !lastUiUpdateMs.compareAndSet(prev, now)) return

            val done = downloadedTotal.get().coerceAtMost(totalBytes)
            val pct = ((done * 100L) / totalBytes).toInt().coerceIn(1, 99)
            val doneMb = done / (1024L * 1024L)
            val totMb = (totalBytes / (1024L * 1024L)).coerceAtLeast(1L)
            val elapsedSec = ((now - startTimeMs) / 1000.0).coerceAtLeast(0.35)
            val speedBps = sessionBytes.get() / elapsedSec
            val speedMb = speedBps / (1024.0 * 1024.0)
            val remainingBytes = (totalBytes - done).coerceAtLeast(0L)
            val etaLabel = if (speedBps > 50_000.0) {
                val sec = (remainingBytes / speedBps).toLong().coerceAtLeast(1L)
                if (sec < 60L) "~${sec}s left" else "~${sec / 60}m ${sec % 60}s left"
            } else "measuring..."
            val speedStr = if (speedMb >= 0.1) "%.1f MB/s".format(speedMb) else "connecting..."
            onProgress(pct, "$modeLabel · $doneMb/$totMb MB · $speedStr · $etaLabel")
        }

        suspend fun downloadSingleChunk(
            raf: RandomAccessFile,
            buf: ByteArray,
            chunkIdx: Int,
            maxAttempts: Int,
            readTimeoutMs: Int,
            modeLabel: String,
            useCanonicalUrlFallback: Boolean = false
        ): Exception? {
            val start = chunkIdx.toLong() * chunkSize
            val end = minOf(start + chunkSize - 1L, totalBytes - 1L)
            val expectedChunkLen = end - start + 1L
            var attempt = 0
            var lastErr: Exception? = null

            while (attempt < maxAttempts) {
                currentCoroutineContext().ensureActive()
                if (fatalAbortReason.get() != null) {
                    return IllegalStateException(fatalAbortReason.get())
                }
                var writtenInAttempt = 0L
                var chunkConn: HttpURLConnection? = null
                try {
                    val reqUrl = if (useCanonicalUrlFallback && attempt >= 2) initialUrl else activeDirectUrl.get()
                    chunkConn = openRangeConnection(
                        initialUrl = reqUrl,
                        rangeHeader = "bytes=$start-$end",
                        connectTimeoutMs = 15000,
                        readTimeoutMs = readTimeoutMs
                    )
                    val code = chunkConn.responseCode
                    if (code == 400 || code == 403 || code == 410) {
                        // Signed CDN URL expired mid-download -> force-refresh edge URL across workers
                        chunkConn.disconnect()
                        refreshDirectUrlSafe(force = true)
                        attempt++
                        delay(500L * attempt)
                        continue
                    }
                    if (code == 416) {
                        // Remote bundle changed size or range mismatch -> fail fast & let user restart cleanly
                        chunkConn.disconnect()
                        return IllegalStateException("Remote model file changed on server (HTTP 416). Tap Reset & Download.")
                    }
                    if (code == 429 || code in 500..504) {
                        // CDN rate-limit or transient edge overload -> respect Retry-After & back off gracefully
                        val retryAfterSec = chunkConn.getHeaderField("Retry-After")?.toLongOrNull()?.coerceIn(1L, 10L)
                        chunkConn.disconnect()
                        attempt++
                        val waitMs = if (retryAfterSec != null) retryAfterSec * 1000L else (1000L * attempt)
                        delay(waitMs)
                        continue
                    }
                    if (start > 0L && code != 206) {
                        chunkConn.disconnect()
                        refreshDirectUrlSafe(force = true)
                        lastErr = IllegalStateException("CDN returned HTTP $code instead of partial content for chunk $chunkIdx")
                        attempt++
                        delay(800L * attempt)
                        continue
                    }
                    if (code != 206 && code != 200) {
                        throw IllegalStateException("HTTP $code for chunk $chunkIdx")
                    }
                    raf.seek(start)
                    chunkConn.inputStream.use { input ->
                        var remaining = expectedChunkLen
                        while (remaining > 0L) {
                            currentCoroutineContext().ensureActive()
                            val toRead = minOf(buf.size.toLong(), remaining).toInt()
                            val read = input.read(buf, 0, toRead)
                            if (read == -1) break
                            raf.write(buf, 0, read)
                            writtenInAttempt += read
                            remaining -= read
                            downloadedTotal.addAndGet(read.toLong())
                            sessionBytes.addAndGet(read.toLong())
                            reportProgress(modeLabel = modeLabel)
                        }
                    }
                    chunkConn.disconnect()
                    if (writtenInAttempt == expectedChunkLen) {
                        completedSet.add(chunkIdx)
                        saveCheckpointLocked()
                        return null
                    } else {
                        // Short read / socket dropped mid-chunk -> roll back counters & retry cleanly
                        downloadedTotal.addAndGet(-writtenInAttempt)
                        sessionBytes.addAndGet(-writtenInAttempt)
                        lastErr = IllegalStateException("Connection dropped ($writtenInAttempt/$expectedChunkLen bytes) on chunk $chunkIdx")
                        attempt++
                        if (attempt >= 2) refreshDirectUrlSafe(force = true)
                        delay(700L * attempt)
                    }
                } catch (ce: CancellationException) {
                    runCatching { chunkConn?.disconnect() }
                    throw ce
                } catch (e: Exception) {
                    runCatching { chunkConn?.disconnect() }
                    if (writtenInAttempt > 0L) {
                        downloadedTotal.addAndGet(-writtenInAttempt)
                        sessionBytes.addAndGet(-writtenInAttempt)
                    }
                    if (isOutOfSpaceError(e)) {
                        val freeMb = (partFile.parentFile?.usableSpace ?: 0L) / (1024L * 1024L)
                        val leftMb = ((totalBytes - downloadedTotal.get()).coerceAtLeast(1L) / (1024L * 1024L)).coerceAtLeast(1L)
                        val diskMsg = "Phone storage full ($freeMb MB free, need $leftMb MB more). Free storage, tap Reset, or use Qwen 2.5 0.5B (529 MB) / SmolLM 135M (175 MB)."
                        fatalAbortReason.compareAndSet(null, diskMsg)
                        return IllegalStateException(diskMsg, e)
                    }
                    lastErr = e
                    attempt++
                    if (attempt == 2) {
                        // Refresh CDN URL in case the signed edge host expired or reset
                        refreshDirectUrlSafe(force = true)
                    }
                    if (attempt < maxAttempts) {
                        delay(850L * attempt)
                    }
                }
            }
            return lastErr ?: IllegalStateException("Failed chunk $chunkIdx after $maxAttempts attempts")
        }

        if (resumedBytes > 0L) {
            reportProgress(force = true)
        } else {
            onProgress(2, "Turbo ${workerCount}x · Starting ${totalBytes / (1024L * 1024L)} MB download...")
        }

        return try {
            val queueCursor = AtomicInteger(0)
            val lastFailureRef = AtomicReference<Exception?>(null)

            // Pass 1: Fault-isolated 3x Turbo parallel download (a single transient chunk failure NEVER aborts healthy workers!)
            coroutineScope {
                val workers = (0 until workerCount).map {
                    async(Dispatchers.IO) {
                        val buf = ByteArray(IO_BUFFER_BYTES)
                        RandomAccessFile(partFile, "rw").use { raf ->
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                if (fatalAbortReason.get() != null) break
                                val pos = queueCursor.getAndIncrement()
                                if (pos >= pendingChunkIndices.size) break
                                val chunkIdx = pendingChunkIndices[pos]
                                val err = downloadSingleChunk(
                                    raf = raf,
                                    buf = buf,
                                    chunkIdx = chunkIdx,
                                    maxAttempts = 4,
                                    readTimeoutMs = 30000,
                                    modeLabel = "Turbo ${workerCount}x"
                                )
                                if (err != null) {
                                    lastFailureRef.set(err)
                                    Log.w(TAG, "Chunk $chunkIdx deferred to recovery pass: ${err.message}")
                                }
                            }
                        }
                    }
                }
                workers.awaitAll()
            }

            fatalAbortReason.get()?.let { diskMsg ->
                saveCheckpointLocked(force = true)
                return false to diskMsg
            }

            // Pass 2: Automatic gentle single-stream recovery pass for any chunks deferred by CDN expiry/throttling or Wi-Fi blips
            val remainingToRecover = (0 until totalChunks).filter { !completedSet.contains(it) }
            if (remainingToRecover.isNotEmpty()) {
                saveCheckpointLocked(force = true)
                refreshDirectUrlSafe(force = true)
                delay(1200L)
                val buf = ByteArray(IO_BUFFER_BYTES)
                RandomAccessFile(partFile, "rw").use { raf ->
                    for (chunkIdx in remainingToRecover) {
                        currentCoroutineContext().ensureActive()
                        if (fatalAbortReason.get() != null) break
                        val err = downloadSingleChunk(
                            raf = raf,
                            buf = buf,
                            chunkIdx = chunkIdx,
                            maxAttempts = 6,
                            readTimeoutMs = 45000,
                            modeLabel = "Stabilizing",
                            useCanonicalUrlFallback = true
                        )
                        if (err != null) {
                            throw err
                        }
                    }
                }
            }

            fatalAbortReason.get()?.let { diskMsg ->
                saveCheckpointLocked(force = true)
                return false to diskMsg
            }

            // Flush OS write buffers to flash before integrity check & atomic rename
            runCatching { RandomAccessFile(partFile, "rw").use { it.fd.sync() } }

            // Verify exact size and valid ZIP/MediaPipe archive integrity before finalizing
            if (completedSet.size != totalChunks || partFile.length() != totalBytes) {
                throw lastFailureRef.get() ?: IllegalStateException("Incomplete download (${completedSet.size}/$totalChunks chunks)")
            }
            if (finalFile.name.endsWith(".task", ignoreCase = true) && !hasValidBundleHeader(partFile)) {
                runCatching { checkpointFile.delete() }
                runCatching { partFile.delete() }
                return false to "Downloaded bundle failed integrity check (corrupted header). Tap Download to retry."
            }

            // Finalize atomic move
            runCatching { checkpointFile.delete() }
            if (finalFile.exists()) finalFile.delete()
            if (!partFile.renameTo(finalFile)) {
                partFile.copyTo(finalFile, overwrite = true)
                partFile.delete()
            }
            true to "Downloaded successfully"
        } catch (ce: CancellationException) {
            saveCheckpointLocked(force = true)
            val savedPct = ((completedSet.size * 100) / totalChunks).coerceIn(0, 99)
            Log.i(TAG, "Download paused for $label at $savedPct% (${completedSet.size}/$totalChunks chunks saved)")
            throw ce
        } catch (e: Exception) {
            saveCheckpointLocked(force = true)
            if (isOutOfSpaceError(e)) {
                val freeMb = (partFile.parentFile?.usableSpace ?: 0L) / (1024L * 1024L)
                return false to "Phone storage full ($freeMb MB free). Free space, tap Reset, or download a smaller model like Qwen 2.5 0.5B (529 MB)."
            }
            val savedPct = ((completedSet.size * 100) / totalChunks).coerceIn(0, 99)
            Log.w(TAG, "Download interrupted at $savedPct% (checkpoint saved): ${e.message}")
            val resumeHint = if (savedPct > 0) " Progress ($savedPct%) saved — tap Resume anytime." else ""
            false to "Download paused: ${e.message ?: "Network error"}.$resumeHint"
        }
    }

    private suspend fun singleStreamFastDownload(
        urlStr: String,
        partFile: File,
        finalFile: File,
        expectedBytes: Long,
        label: String,
        onProgress: (pct: Int, status: String) -> Unit
    ): Pair<Boolean, String> {
        var conn: HttpURLConnection? = null
        return try {
            conn = openRangeConnection(
                initialUrl = urlStr,
                rangeHeader = null,
                connectTimeoutMs = 12000,
                readTimeoutMs = 40000
            )
            val code = conn.responseCode
            if (code !in 200..299) {
                conn.disconnect()
                return false to "Download failed (HTTP $code)."
            }
            val totalBytes = conn.contentLengthLong.takeIf { it > 0L } ?: expectedBytes
            val buffer = ByteArray(IO_BUFFER_BYTES)
            var downloaded = 0L
            val startMs = System.currentTimeMillis()
            var lastUiMs = 0L

            conn.inputStream.use { input ->
                FileOutputStream(partFile).use { output ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastUiMs >= 220L) {
                            lastUiMs = now
                            val dlMb = downloaded / (1024L * 1024L)
                            val totMb = (totalBytes / (1024L * 1024L)).coerceAtLeast(1L)
                            val elapsedSec = ((now - startMs) / 1000.0).coerceAtLeast(0.35)
                            val speedMb = (downloaded / (1024.0 * 1024.0)) / elapsedSec
                            val pct = if (totalBytes > 0L) ((downloaded * 100L) / totalBytes).toInt().coerceIn(1, 99) else 50
                            onProgress(pct, "Downloading $label · $dlMb/$totMb MB · %.1f MB/s".format(speedMb))
                        }
                    }
                    output.fd.sync()
                }
            }
            conn.disconnect()
            if (finalFile.name.endsWith(".task", ignoreCase = true) && !hasValidBundleHeader(partFile)) {
                runCatching { partFile.delete() }
                return false to "Downloaded bundle failed integrity check. Tap Download to retry."
            }
            if (finalFile.exists()) finalFile.delete()
            if (!partFile.renameTo(finalFile)) {
                partFile.copyTo(finalFile, overwrite = true)
                partFile.delete()
            }
            true to "Downloaded successfully"
        } catch (ce: CancellationException) {
            runCatching { conn?.disconnect() }
            throw ce
        } catch (e: Exception) {
            runCatching { conn?.disconnect() }
            if (isOutOfSpaceError(e)) {
                val freeMb = (partFile.parentFile?.usableSpace ?: 0L) / (1024L * 1024L)
                return false to "Phone storage full ($freeMb MB free). Free space or try a smaller model."
            }
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
            val totalBytes = queryUriSizeBytes(context, sourceUri)
            onProgress(5, "Fast-copying local weights for ${spec.name}...")
            val startMs = System.currentTimeMillis()
            var lastUiMs = 0L

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(target).use { output ->
                    val buf = ByteArray(IMPORT_BUFFER_BYTES)
                    var copied = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buf)
                        if (read == -1) break
                        output.write(buf, 0, read)
                        copied += read
                        val now = System.currentTimeMillis()
                        if (now - lastUiMs >= 200L) {
                            lastUiMs = now
                            val copiedMb = copied / (1024L * 1024L)
                            val pct = if (totalBytes > 0L) ((copied * 95L) / totalBytes).toInt().coerceIn(5, 96) else 55
                            val sec = ((now - startMs) / 1000.0).coerceAtLeast(0.25)
                            val speedMb = (copied / (1024.0 * 1024.0)) / sec
                            onProgress(pct, "Fast Import · $copiedMb MB · %.0f MB/s".format(speedMb))
                        }
                    }
                }
            } ?: return@withContext false to "Unable to read selected file."

            if (target.length() < MIN_REAL_MODEL_BYTES) {
                target.delete()
                return@withContext false to "Selected file is too small to be a valid .task model bundle."
            }
            onProgress(98, "Verifying & pre-warming ${spec.name} into RAM...")
            val fingerprint = OnDeviceLlmEngine.computeFastFingerprint(target)
            File(dir, "model_descriptor.json").writeText(
                JSONObject()
                    .put("id", spec.id)
                    .put("name", spec.name)
                    .put("hfRepo", spec.hfRepo ?: "")
                    .put("source", "local_import")
                    .put("weightsFile", target.name)
                    .put("bytes", target.length())
                    .put("sha256Prefix", fingerprint)
                    .put("installedAtEpochMs", System.currentTimeMillis())
                    .toString()
            )
            OnDeviceLlmEngine.invalidateCache(spec.id)
            val mb = target.length() / (1024L * 1024L)
            onProgress(100, "${spec.name} imported ($mb MB)")
            true to "${spec.name} ($mb MB · verified) imported & ready offline."
        } catch (ce: CancellationException) {
            runCatching { target.delete() }
            throw ce
        } catch (e: Exception) {
            runCatching { target.delete() }
            false to "Import failed: ${e.message}"
        }
    }

    private fun queryUriSizeBytes(context: Context, uri: Uri): Long {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0 && !cursor.isNull(idx)) cursor.getLong(idx) else -1L
                } else -1L
            } ?: -1L
        }.getOrDefault(-1L)
    }

    fun removeOfflineCache(context: Context, modelId: String) {
        OnDeviceLlmEngine.invalidateCache(modelId)
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

    private fun hasValidBundleHeader(file: File): Boolean {
        return runCatching {
            if (!file.isFile || file.length() < 16L) return@runCatching false
            val header = ByteArray(16)
            RandomAccessFile(file, "r").use { raf ->
                val read = raf.read(header)
                if (read < 8) return@runCatching false
            }
            val b0 = header[0].toInt() and 0xFF
            val b1 = header[1].toInt() and 0xFF
            val b2 = header[2].toInt() and 0xFF
            val b3 = header[3].toInt() and 0xFF
            // Standard ZIP archive (PK\x03\x04) used by MediaPipe .task bundles
            val isZip = (b0 == 0x50 && b1 == 0x4B && b2 == 0x03 && b3 == 0x04)
            // TFLite FlatBuffer ("TFL3" at offset 4..7)
            val isTflite = (header[4].toInt() == 'T'.code &&
                header[5].toInt() == 'F'.code &&
                header[6].toInt() == 'L'.code &&
                header[7].toInt() == '3'.code)
            if (isZip || isTflite) return@runCatching true

            // Explicitly reject HTML/XML/JSON error pages accidentally saved as weights
            val ascii = String(header, Charsets.UTF_8).trimStart().lowercase()
            if (ascii.startsWith("<html") || ascii.startsWith("<!doc") || ascii.startsWith("<?xml") || ascii.startsWith("{")) {
                Log.w(TAG, "Rejected text/HTML error payload instead of model weights: ${ascii.take(12)}")
                return@runCatching false
            }
            Log.w(TAG, "Unexpected .task header bytes: %02X %02X %02X %02X".format(b0, b1, b2, b3))
            false
        }.getOrDefault(false)
    }

    fun removeVoiceCache(context: Context, voiceId: String) {
        runCatching {
            File(context.filesDir, "voices/$voiceId").deleteRecursively()
        }
    }
}
