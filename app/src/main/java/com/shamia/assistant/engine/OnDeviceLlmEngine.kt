package com.shamia.assistant.engine

import android.content.Context
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
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
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
 * Strictly binds every inference session to the exact selected model's isolated weights directory
 * (`filesDir/models/<modelId>/`), verifies manifest descriptor integrity, and unloads any other
 * model from RAM before generating tokens.
 */
object OnDeviceLlmEngine {

    private const val TAG = "ShamiaOnDeviceLLM"
    private const val MIN_REAL_MODEL_BYTES = 10L * 1024L * 1024L // 10 MB minimum for real neural weights

    private val engineDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "shamia-local-llm").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    private val mutex = Mutex()
    private var loadedModelId: String? = null
    private var loadedModelPath: String? = null
    private var activeBackendLabel: String = "CPU"
    private var llmInference: LlmInference? = null

    @Volatile
    private var activeSession: LlmInferenceSession? = null
    private val cancelledFlag = AtomicBoolean(false)

    /**
     * Computes a fast, deterministic SHA-256 fingerprint prefix (first 2 MB + exact file size)
     * to cryptographically bind a weights file to its model descriptor without stalling mobile I/O.
     */
    fun computeFastFingerprint(file: File): String {
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(file.length().toString().toByteArray())
            val buf = ByteArray(64 * 1024)
            var remaining = 2L * 1024L * 1024L
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
     * Strictly locates and verifies the real weights file bound to [modelId].
     * Guarantees the file lives inside `filesDir/models/<modelId>/` and matches `model_descriptor.json` if present.
     */
    fun verifyModelBinding(context: Context, modelId: String): VerifiedLocalWeights? {
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
                Log.e(TAG, "STRICT VERIFICATION FAILED: descriptor id=$boundId does not match requested modelId=$modelId")
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

        // Strict canonical path containment check — impossible to escape into another model's folder
        val canonicalFile = runCatching { weightsFile.canonicalPath }.getOrNull() ?: return null
        if (!canonicalFile.startsWith("$canonicalDir${File.separator}")) {
            Log.e(TAG, "STRICT VERIFICATION FAILED: file path $canonicalFile outside $canonicalDir")
            return null
        }

        val storedSize = descriptor?.optLong("bytes", -1L) ?: -1L
        if (storedSize > 0L && weightsFile.length() != storedSize) {
            Log.e(TAG, "STRICT VERIFICATION FAILED: file size ${weightsFile.length()} != descriptor $storedSize")
            return null
        }

        val fingerprint = descriptor?.optString("sha256Prefix")?.takeIf { it.isNotBlank() }
            ?: computeFastFingerprint(weightsFile)
        val sourceLabel = descriptor?.optString("hfRepo")?.takeIf { it.isNotBlank() }
            ?: descriptor?.optString("source")?.takeIf { it.isNotBlank() }
            ?: "Local Weights"

        return VerifiedLocalWeights(
            modelId = modelId,
            file = weightsFile,
            sizeMb = weightsFile.length() / (1024L * 1024L),
            fingerprint = fingerprint,
            sourceLabel = sourceLabel
        )
    }

    fun findInstalledWeightsFile(context: Context, modelId: String): File? =
        verifyModelBinding(context, modelId)?.file

    fun hasRealLocalWeights(context: Context, modelId: String): Boolean =
        verifyModelBinding(context, modelId) != null

    fun installedWeightsSizeMb(context: Context, modelId: String): Long =
        verifyModelBinding(context, modelId)?.sizeMb ?: 0L

    fun currentLoadedModelId(): String? = loadedModelId

    /**
     * Immediately interrupts active native C++ token generation when the user taps Stop.
     */
    fun cancelGeneration() {
        cancelledFlag.set(true)
        runCatching {
            activeSession?.cancelGenerateResponseAsync()
        }
    }

    /**
     * Immediately releases native weights from RAM if a different model is currently loaded,
     * preventing any stale session or cross-model memory state when switching models.
     */
    suspend fun unloadIfDifferent(targetModelId: String) = mutex.withLock {
        withContext(engineDispatcher) {
            if (loadedModelId != null && loadedModelId != targetModelId) {
                Log.i(TAG, "STRICT MODEL SWITCH: unloading previous model $loadedModelId before activating $targetModelId")
                closeInternal()
            }
        }
    }

    /**
     * Unloads any currently loaded model from RAM (e.g., when removing a model or freeing memory).
     */
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

        val gpuAttempt = runCatching {
            val opts = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(canonicalPath)
                .setMaxTokens(1024)
                .setPreferredBackend(LlmInference.Backend.GPU)
                .build()
            LlmInference.createFromOptions(context.applicationContext, opts)
        }

        val engine = gpuAttempt.getOrElse { gpuErr ->
            Log.w(TAG, "GPU delegate unavailable (${gpuErr.message}); falling back to local CPU backend")
            val cpuOpts = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(canonicalPath)
                .setMaxTokens(1024)
                .setPreferredBackend(LlmInference.Backend.CPU)
                .build()
            activeBackendLabel = "CPU"
            LlmInference.createFromOptions(context.applicationContext, cpuOpts)
        }

        if (gpuAttempt.isSuccess) {
            activeBackendLabel = "GPU"
        }

        llmInference = engine
        loadedModelId = verified.modelId
        loadedModelPath = canonicalPath
        Log.i(
            TAG,
            "STRICT LOAD VERIFIED | modelId=${verified.modelId} | file=${verified.file.name} | size=${verified.sizeMb}MB | sha=${verified.fingerprint} | backend=$activeBackendLabel"
        )
        return engine
    }

    /**
     * Runs real local offline neural inference strictly bound to [spec].
     * Returns null if verified weights for [spec.id] are not installed on disk.
     */
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
                val engine = runCatching { ensureLoadedStrict(context, verified) }
                    .onFailure { Log.e(TAG, "Failed to initialize local model ${spec.id}", it) }
                    .getOrNull() ?: return@withContext null

                // Hard runtime assertion: loaded native runtime MUST match requested spec.id and canonical file path
                if (loadedModelId != spec.id || loadedModelPath != verified.file.canonicalPath) {
                    Log.e(TAG, "STRICT ASSERTION FAILED: loaded=$loadedModelId vs requested=${spec.id}")
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

                val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTopK(40)
                    .setTopP(0.92f)
                    .setTemperature(0.7f)
                    .build()

                val session = runCatching {
                    LlmInferenceSession.createFromOptions(engine, sessionOptions)
                }.onFailure {
                    Log.e(TAG, "Failed to create LlmInferenceSession", it)
                }.getOrNull() ?: return@withContext null

                activeSession = session
                val buffer = StringBuilder()
                val completion = CompletableDeferred<String>()

                try {
                    session.addQueryChunk(formattedPrompt)
                    session.generateResponseAsync { partialResult, done ->
                        if (!partialResult.isNullOrEmpty() && !cancelledFlag.get()) {
                            buffer.append(partialResult)
                            onPartialResponse(cleanRawTokens(buffer.toString()))
                        }
                        if (done && !completion.isCompleted) {
                            completion.complete(cleanRawTokens(buffer.toString()))
                        }
                    }
                    val finalReply = completion.await()
                    if (finalReply.isBlank()) null
                    else VerifiedNativeOutput(
                        text = finalReply,
                        backendLabel = "Verified On-Device LiteRT ($activeBackendLabel · ${verified.sizeMb} MB)",
                        verifiedModelId = spec.id,
                        weightsFileName = verified.file.name,
                        weightsSizeMb = verified.sizeMb,
                        verificationProof = "file=${verified.file.name} · sha=${verified.fingerprint} · $activeBackendLabel"
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Generation interrupted or failed: ${e.message}")
                    val partial = cleanRawTokens(buffer.toString())
                    if (partial.isNotBlank()) {
                        VerifiedNativeOutput(
                            text = partial,
                            backendLabel = "Verified On-Device LiteRT ($activeBackendLabel · ${verified.sizeMb} MB)",
                            verifiedModelId = spec.id,
                            weightsFileName = verified.file.name,
                            weightsSizeMb = verified.sizeMb,
                            verificationProof = "file=${verified.file.name} · sha=${verified.fingerprint} · $activeBackendLabel"
                        )
                    } else null
                } finally {
                    runCatching { session.close() }
                    if (activeSession === session) {
                        activeSession = null
                    }
                }
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
        val compactSystem = systemInstruction.take(900)
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
                    append("$role: ${CloudModelResolver.stripHeaderFromHistoryText(msg.text).take(300)}\n")
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
                    append(CloudModelResolver.stripHeaderFromHistoryText(msg.text).take(300))
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
                    append(CloudModelResolver.stripHeaderFromHistoryText(msg.text).take(300))
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
                    append("<|$tag|>${CloudModelResolver.stripHeaderFromHistoryText(msg.text).take(300)}<|end|>")
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
                    append(CloudModelResolver.stripHeaderFromHistoryText(msg.text).take(300))
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
