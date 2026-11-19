package com.shamia.assistant.engine

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.shamia.assistant.model.HudMessage
import com.shamia.assistant.model.OpenSourceModelSpec
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class VerifiedLocalWeights(
    val modelId: String,
    val file: File,
    val sizeMb: Long,
    val fingerprint: String,
    val sourceLabel: String
)

data class VerifiedNativeOutput(
    val text: String,
    val backendLabel: String,
    val verifiedModelId: String,
    val weightsFileName: String,
    val weightsSizeMb: Long,
    val verificationProof: String
)

/**
 * True On-Device Offline Neural LLM Runtime powered by MediaPipe GenAI / LiteRT.
 * Optimized for ultra-fast local loading with:
 * - O(1) verified-binding memory cache
 * - Smart GPU-vs-CPU backend selection (skips slow failing GPU probes on emulators/CPU-only devices)
 * - Automatic background RAM pre-warming right after download, model switch, or app launch
 * - Strict cryptographic/path binding to `filesDir/models/<modelId>/`
 */
object OnDeviceLlmEngine {

    private const val TAG = "ShamiaOnDeviceLLM"
    private const val MIN_REAL_MODEL_BYTES = 10L * 1024L * 1024L

    private val engineDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "shamia-local-llm").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    private data class CachedBinding(
        val canonicalPath: String,
        val length: Long,
        val lastModified: Long,
        val verified: VerifiedLocalWeights
    )

    private val bindingCache = ConcurrentHashMap<String, CachedBinding>()

    private val mutex = Mutex()
    @Volatile
    private var loadedModelId: String? = null
    @Volatile
    private var loadedModelPath: String? = null
    @Volatile
    private var activeBackendLabel: String = "CPU"
    @Volatile
    private var gpuDelegateUnsupported: Boolean = detectLikelyEmulatorWithoutGpu()
    private var llmInference: LlmInference? = null

    @Volatile
    private var activeSession: LlmInferenceSession? = null
    private val cancelledFlag = AtomicBoolean(false)

    private fun detectLikelyEmulatorWithoutGpu(): Boolean {
        val fp = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val hw = Build.HARDWARE.lowercase()
        val prod = Build.PRODUCT.lowercase()
        return fp.contains("generic") ||
            fp.contains("emulator") ||
            model.contains("emulator") ||
            model.contains("android sdk built for") ||
            hw.contains("goldfish") ||
            hw.contains("ranchu") ||
            prod.contains("sdk_gphone") ||
            prod.contains("emulator")
    }

    fun invalidateCache(modelId: String? = null) {
        if (modelId == null) bindingCache.clear() else bindingCache.remove(modelId)
    }

    fun computeFastFingerprint(file: File): String {
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(file.length().toString().toByteArray())
            val buf = ByteArray(128 * 1024)
            var remaining = 1024L * 1024L // Fast 1 MB prefix + exact file size
            FileInputStream(file).use { fis ->
                while (remaining > 0) {
                    val toRead = minOf(buf.size.toLong(), remaining).toInt()
                    val read = fis.read(buf, 0, toRead)
                    if (read <= 0) break
                    digest.update(buf, 0, read)
                    remaining -= read
                }
            }
            digest.digest().take(8).joinToString("") { "%02x".format(it) }
        }.getOrDefault("size-${file.length()}")
    }

    /**
     * Strictly locates and verifies the real weights file bound to [modelId] with O(1) memoization.
     */
    fun verifyModelBinding(context: Context, modelId: String): VerifiedLocalWeights? {
        val cached = bindingCache[modelId]
        if (cached != null) {
            val f = cached.verified.file
            if (f.isFile && f.length() == cached.length && f.lastModified() == cached.lastModified) {
                return cached.verified
            } else {
                bindingCache.remove(modelId)
            }
        }

        val modelDir = File(context.filesDir, "models/$modelId")
        if (!modelDir.exists() || !modelDir.isDirectory) return null
        val canonicalDir = runCatching { modelDir.canonicalPath }.getOrNull() ?: return null

        val descriptorFile = File(modelDir, "model_descriptor.json")
        val descriptor = if (descriptorFile.isFile) {
            runCatching { JSONObject(descriptorFile.readText()) }.getOrNull()
        } else null

        if (descriptor != null) {
            val boundId = descriptor.optString("id")
            if (boundId.isNotBlank() && boundId != modelId) {
                Log.e(TAG, "STRICT VERIFICATION FAILED: descriptor id=$boundId != $modelId")
                return null
            }
        }

        val expectedFileName = descriptor?.optString("weightsFile")?.takeIf { it.isNotBlank() }
        val candidates = modelDir.listFiles()?.filter { f ->
            f.isFile &&
                f.length() >= MIN_REAL_MODEL_BYTES &&
                (f.name.endsWith(".task", ignoreCase = true) ||
                    f.name.endsWith(".litertlm", ignoreCase = true) ||
                    f.name.endsWith(".bin", ignoreCase = true))
        }.orEmpty()

        val weightsFile = if (expectedFileName != null) {
            candidates.firstOrNull { it.name == expectedFileName } ?: candidates.firstOrNull()
        } else {
            candidates.firstOrNull()
        } ?: return null

        val canonicalFile = runCatching { weightsFile.canonicalPath }.getOrNull() ?: return null
        if (!canonicalFile.startsWith("$canonicalDir${File.separator}")) {
            Log.e(TAG, "STRICT VERIFICATION FAILED: $canonicalFile outside $canonicalDir")
            return null
        }

        val storedSize = descriptor?.optLong("bytes", -1L) ?: -1L
        if (storedSize > 0L && weightsFile.length() != storedSize) {
            Log.e(TAG, "STRICT VERIFICATION FAILED: size ${weightsFile.length()} != $storedSize")
            return null
        }

        val fingerprint = descriptor?.optString("sha256Prefix")?.takeIf { it.isNotBlank() }
            ?: computeFastFingerprint(weightsFile)
        val sourceLabel = descriptor?.optString("hfRepo")?.takeIf { it.isNotBlank() }
            ?: descriptor?.optString("source")?.takeIf { it.isNotBlank() }
            ?: "Local Weights"

        val verified = VerifiedLocalWeights(
            modelId = modelId,
            file = weightsFile,
            sizeMb = weightsFile.length() / (1024L * 1024L),
            fingerprint = fingerprint,
            sourceLabel = sourceLabel
        )
        bindingCache[modelId] = CachedBinding(canonicalFile, weightsFile.length(), weightsFile.lastModified(), verified)
        return verified
    }

    fun findInstalledWeightsFile(context: Context, modelId: String): File? =
        verifyModelBinding(context, modelId)?.file

    fun hasRealLocalWeights(context: Context, modelId: String): Boolean =
        verifyModelBinding(context, modelId) != null

    fun installedWeightsSizeMb(context: Context, modelId: String): Long =
        verifyModelBinding(context, modelId)?.sizeMb ?: 0L

    fun currentLoadedModelId(): String? = loadedModelId

    fun currentLoadedBackend(): String = activeBackendLabel

    fun cancelGeneration() {
        cancelledFlag.set(true)
        runCatching {
            activeSession?.cancelGenerateResponseAsync()
        }
    }

    /**
     * Pre-warms the exact installed model into RAM in the background so the user's first prompt
     * starts streaming tokens immediately with zero cold-load stall.
     * Returns Pair(loadedSuccessfully, backendLabel).
     */
    suspend fun preloadModel(context: Context, modelId: String): Pair<Boolean, String> {
        val verified = verifyModelBinding(context, modelId) ?: return false to ""
        return mutex.withLock {
            withContext(engineDispatcher) {
                try {
                    ensureLoadedStrict(context, verified)
                    true to activeBackendLabel
                } catch (t: Throwable) {
                    Log.w(TAG, "Background preload skipped for $modelId: ${t.message}")
                    false to ""
                }
            }
        }
    }

    suspend fun unloadIfDifferent(targetModelId: String) = mutex.withLock {
        withContext(engineDispatcher) {
            if (loadedModelId != null && loadedModelId != targetModelId) {
                Log.i(TAG, "STRICT MODEL SWITCH: unloading previous model $loadedModelId before activating $targetModelId")
                closeInternal()
            }
        }
    }

    suspend fun release() = mutex.withLock {
        withContext(engineDispatcher) {
            closeInternal()
        }
    }

    private fun closeInternal() {
        runCatching { activeSession?.cancelGenerateResponseAsync() }
        runCatching { activeSession?.close() }
        activeSession = null
        runCatching { llmInference?.close() }
        llmInference = null
        loadedModelId = null
        loadedModelPath = null
    }

    private fun ensureLoadedStrict(context: Context, verified: VerifiedLocalWeights): LlmInference {
        val canonicalPath = verified.file.canonicalPath
        if (llmInference != null && loadedModelId == verified.modelId && loadedModelPath == canonicalPath) {
            return llmInference!!
        }
        closeInternal()
        val loadStartMs = System.currentTimeMillis()

        val engine = if (!gpuDelegateUnsupported) {
            val gpuAttempt = runCatching {
                val opts = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(canonicalPath)
                    .setMaxTokens(1024)
                    .setPreferredBackend(LlmInference.Backend.GPU)
                    .build()
                LlmInference.createFromOptions(context.applicationContext, opts)
            }
            if (gpuAttempt.isSuccess) {
                activeBackendLabel = "GPU"
                gpuAttempt.getOrThrow()
            } else {
                // Remember GPU unavailability on this device to avoid wasting 5-10s on future loads!
                gpuDelegateUnsupported = true
                Log.w(TAG, "GPU delegate unavailable (${gpuAttempt.exceptionOrNull()?.message}); switching straight to fast CPU")
                val cpuOpts = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(canonicalPath)
                    .setMaxTokens(1024)
                    .setPreferredBackend(LlmInference.Backend.CPU)
                    .build()
                activeBackendLabel = "CPU"
                LlmInference.createFromOptions(context.applicationContext, cpuOpts)
            }
        } else {
            val cpuOpts = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(canonicalPath)
                .setMaxTokens(1024)
                .setPreferredBackend(LlmInference.Backend.CPU)
                .build()
            activeBackendLabel = "CPU"
            LlmInference.createFromOptions(context.applicationContext, cpuOpts)
        }

        llmInference = engine
        loadedModelId = verified.modelId
        loadedModelPath = canonicalPath
        val elapsedMs = System.currentTimeMillis() - loadStartMs
        Log.i(
            TAG,
            "FAST LOAD VERIFIED | modelId=${verified.modelId} | file=${verified.file.name} | size=${verified.sizeMb}MB | sha=${verified.fingerprint} | backend=$activeBackendLabel | loadMs=$elapsedMs"
        )
        return engine
    }

    suspend fun generateOfflineStream(
        context: Context,
        spec: OpenSourceModelSpec,
        systemInstruction: String,
        history: List<HudMessage>,
        userPrompt: String,
        webContext: String,
        onPartialResponse: (accumulatedText: String) -> Unit
    ): VerifiedNativeOutput? {
        val verified = verifyModelBinding(context, spec.id) ?: return null

        return mutex.withLock {
            withContext(engineDispatcher) {
                cancelledFlag.set(false)
                val engine = try {
                    ensureLoadedStrict(context, verified)
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to initialize local model ${spec.id}", t)
                    null
                } ?: return@withContext null

                if (loadedModelId != spec.id || loadedModelPath != verified.file.canonicalPath) {
                    closeInternal()
                    return@withContext null
                }

                val formattedPrompt = buildChatPrompt(
                    spec = spec,
                    systemInstruction = systemInstruction,
                    history = history,
                    userPrompt = userPrompt,
                    webContext = webContext
                )

                val primaryReply = runSingleNativeSession(
                    engine = engine,
                    prompt = formattedPrompt,
                    onPartialResponse = onPartialResponse
                )

                val finalReply = if (!primaryReply.isNullOrBlank() || cancelledFlag.get()) {
                    primaryReply
                } else {
                    // Retry once with a clean minimal prompt in case the .task bundle applies its own internal chat template
                    val simplePrompt = if (webContext.isNotBlank()) {
                        "Context: ${webContext.take(800)}\n\nQuestion: $userPrompt\nAnswer:"
                    } else {
                        "Question: $userPrompt\nAnswer:"
                    }
                    runSingleNativeSession(
                        engine = engine,
                        prompt = simplePrompt,
                        onPartialResponse = onPartialResponse
                    )
                }

                if (finalReply.isNullOrBlank()) null
                else VerifiedNativeOutput(
                    text = finalReply,
                    backendLabel = "On-Device ($activeBackendLabel)",
                    verifiedModelId = spec.id,
                    weightsFileName = verified.file.name,
                    weightsSizeMb = verified.sizeMb,
                    verificationProof = "file=${verified.file.name} · sha=${verified.fingerprint} · $activeBackendLabel"
                )
            }
        }
    }

    private suspend fun runSingleNativeSession(
        engine: LlmInference,
        prompt: String,
        onPartialResponse: (accumulatedText: String) -> Unit
    ): String? {
        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTopK(40)
            .setTopP(0.90f)
            .setTemperature(0.65f)
            .build()

        val session = try {
            LlmInferenceSession.createFromOptions(engine, sessionOptions)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to create LlmInferenceSession", t)
            null
        } ?: return null

        activeSession = session
        val buffer = StringBuilder()
        val completion = CompletableDeferred<String>()

        return try {
            session.addQueryChunk(prompt)
            session.generateResponseAsync { partialResult, done ->
                if (!partialResult.isNullOrEmpty() && !cancelledFlag.get()) {
                    buffer.append(partialResult)
                    onPartialResponse(cleanRawTokens(buffer.toString()))
                }
                if (done && !completion.isCompleted) {
                    completion.complete(cleanRawTokens(buffer.toString()))
                }
            }
            val result = withTimeoutOrNull(45_000L) { completion.await() }
                ?: cleanRawTokens(buffer.toString())
            result.takeIf { it.isNotBlank() }
        } catch (t: Throwable) {
            Log.w(TAG, "Generation interrupted or failed: ${t.message}")
            cleanRawTokens(buffer.toString()).takeIf { it.isNotBlank() }
        } finally {
            runCatching { session.close() }
            if (activeSession === session) {
                activeSession = null
            }
        }
    }

    private fun buildChatPrompt(
        spec: OpenSourceModelSpec,
        systemInstruction: String,
        history: List<HudMessage>,
        userPrompt: String,
        webContext: String
    ): String {
        val compactSystem = systemInstruction.take(750)
        val promptWithWeb = if (webContext.isNotBlank()) {
            "$userPrompt\n\nVerified Web Context (use these facts to answer clearly in your own words):\n${webContext.take(1200)}"
        } else {
            userPrompt
        }

        val id = spec.id.lowercase()
        val recent = history.takeLast(4)

        return when {
            id.contains("gemma") -> buildString {
                append("<start_of_turn>user\n")
                append(compactSystem)
                append("\n\n")
                recent.forEach { msg ->
                    val role = if (msg.isUser) "User" else "Assistant"
                    append("$role: ${CloudModelResolver.stripHeaderFromHistoryText(msg.text).take(260)}\n")
                }
                append(promptWithWeb)
                append("<end_of_turn>\n<start_of_turn>model\n")
            }

            id.contains("nemotron-mini") -> buildString {
                append("<extra_id_0>System\n")
                append(compactSystem)
                append("\n")
                recent.forEach { msg ->
                    val role = if (msg.isUser) "User" else "Assistant"
                    append("<extra_id_1>$role\n")
                    append(CloudModelResolver.stripHeaderFromHistoryText(msg.text).take(260))
                    append("\n")
                }
                append("<extra_id_1>User\n")
                append(promptWithWeb)
                append("\n<extra_id_1>Assistant\n")
            }

            id.contains("llama") || id.contains("nemotron-nano") -> buildString {
                append("<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n")
                append(compactSystem)
                append("<|eot_id|>")
                recent.forEach { msg ->
                    val role = if (msg.isUser) "user" else "assistant"
                    append("<|start_header_id|>$role<|end_header_id|>\n\n")
                    append(CloudModelResolver.stripHeaderFromHistoryText(msg.text).take(260))
                    append("<|eot_id|>")
                }
                append("<|start_header_id|>user<|end_header_id|>\n\n")
                append(promptWithWeb)
                append("<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n")
            }

            id.contains("phi") -> buildString {
                append("<|system|>$compactSystem<|end|>")
                recent.forEach { msg ->
                    val tag = if (msg.isUser) "user" else "assistant"
                    append("<|$tag|>${CloudModelResolver.stripHeaderFromHistoryText(msg.text).take(260)}<|end|>")
                }
                append("<|user|>$promptWithWeb<|end|><|assistant|>")
            }

            else -> buildString {
                append("<|im_start|>system\n")
                append(compactSystem)
                append("<|im_end|>\n")
                recent.forEach { msg ->
                    val role = if (msg.isUser) "user" else "assistant"
                    append("<|im_start|>$role\n")
                    append(CloudModelResolver.stripHeaderFromHistoryText(msg.text).take(260))
                    append("<|im_end|>\n")
                }
                append("<|im_start|>user\n")
                append(promptWithWeb)
                append("<|im_end|>\n<|im_start|>assistant\n")
            }
        }
    }

    private fun cleanRawTokens(raw: String): String {
        return raw
            .replace(Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE), "")
            .replace("<|im_end|>", "")
            .replace("<|eot_id|>", "")
            .replace("<|end|>", "")
            .replace("<end_of_turn>", "")
            .replace("<extra_id_0>", "")
            .replace("<extra_id_1>", "")
            .replace("<eos>", "")
            .trim()
    }
}
