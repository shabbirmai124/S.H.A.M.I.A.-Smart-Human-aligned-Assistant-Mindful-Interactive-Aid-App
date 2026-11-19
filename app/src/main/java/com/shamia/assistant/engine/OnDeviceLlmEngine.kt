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
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * True On-Device Offline Neural LLM Runtime powered by MediaPipe GenAI / LiteRT.
 * Runs quantized open-weight .task / .litertlm models directly on the phone's CPU/GPU
 * with zero internet connection, zero cloud APIs, and instant native Stop cancellation.
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
     * Finds a real downloaded .task or .litertlm weights file for [modelId] inside private app storage.
     */
    fun findInstalledWeightsFile(context: Context, modelId: String): File? {
        val dir = File(context.filesDir, "models/$modelId")
        if (!dir.exists() || !dir.isDirectory) return null
        return dir.listFiles()?.firstOrNull { f ->
            f.isFile &&
                f.length() >= MIN_REAL_MODEL_BYTES &&
                (f.name.endsWith(".task", ignoreCase = true) ||
                    f.name.endsWith(".litertlm", ignoreCase = true) ||
                    f.name.endsWith(".bin", ignoreCase = true))
        }
    }

    fun hasRealLocalWeights(context: Context, modelId: String): Boolean =
        findInstalledWeightsFile(context, modelId) != null

    fun installedWeightsSizeMb(context: Context, modelId: String): Long {
        val f = findInstalledWeightsFile(context, modelId) ?: return 0L
        return f.length() / (1024L * 1024L)
    }

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

    private fun ensureLoaded(context: Context, modelId: String, weightsFile: File): LlmInference {
        val path = weightsFile.absolutePath
        if (llmInference != null && loadedModelId == modelId && loadedModelPath == path) {
            return llmInference!!
        }
        closeInternal()

        // Try GPU acceleration first on mobile SoCs; fall back cleanly to CPU on emulators or devices without OpenCL
        val gpuAttempt = runCatching {
            val opts = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(path)
                .setMaxTokens(1024)
                .setPreferredBackend(LlmInference.Backend.GPU)
                .build()
            LlmInference.createFromOptions(context.applicationContext, opts)
        }

        val engine = gpuAttempt.getOrElse { gpuErr ->
            Log.w(TAG, "GPU delegate unavailable (${gpuErr.message}); falling back to local CPU backend")
            val cpuOpts = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(path)
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
        loadedModelId = modelId
        loadedModelPath = path
        Log.i(TAG, "Loaded local weights for $modelId (${weightsFile.length() / (1024 * 1024)} MB) on $activeBackendLabel")
        return engine
    }

    /**
     * Runs real local offline neural inference for [spec] if its .task weights file is installed on device.
     * Returns null if real weights are not yet downloaded for this model.
     */
    suspend fun generateOfflineStream(
        context: Context,
        spec: OpenSourceModelSpec,
        systemInstruction: String,
        history: List<HudMessage>,
        userPrompt: String,
        webContext: String,
        onPartialResponse: (accumulatedText: String) -> Unit
    ): Pair<String, String>? {
        val weightsFile = findInstalledWeightsFile(context, spec.id) ?: return null

        return mutex.withLock {
            withContext(engineDispatcher) {
                cancelledFlag.set(false)
                val engine = runCatching { ensureLoaded(context, spec.id, weightsFile) }
                    .onFailure { Log.e(TAG, "Failed to initialize local model ${spec.id}", it) }
                    .getOrNull() ?: return@withContext null

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
                    else finalReply to "On-Device LiteRT ($activeBackendLabel · ${weightsFile.length() / (1024 * 1024)} MB)"
                } catch (e: Exception) {
                    Log.w(TAG, "Generation interrupted or failed: ${e.message}")
                    val partial = cleanRawTokens(buffer.toString())
                    if (partial.isNotBlank()) partial to "On-Device LiteRT ($activeBackendLabel)" else null
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

            id.contains("llama") -> buildString {
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

            else -> buildString {
                // Standard ChatML format used by Qwen 2.5, Qwen 3, SmolLM2, DeepSeek-Qwen, Phi
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
            .replace("<end_of_turn>", "")
            .replace("<eos>", "")
            .trim()
    }
}
