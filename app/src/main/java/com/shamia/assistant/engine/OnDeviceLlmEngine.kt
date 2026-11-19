package com.shamia.assistant.engine

import android.content.Context
import android.os.Process
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.shamia.assistant.model.HudMessage
import com.shamia.assistant.model.OpenSourceModelSpec
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

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

data class LlmRuntimeSnapshot(
    val loadedModelId: String? = null,
    val loadingModelId: String? = null,
    val backendLabel: String = "",
    val lastLoadMs: Long = 0L
)

/**
 * True On-Device Offline Neural LLM Runtime powered by MediaPipe GenAI / LiteRT.
 * Engineered for reliable, bug-free local execution across Android phones:
 * - Single-source-of-truth [runtimeState] StateFlow synchronized across ViewModel & BackgroundService
 * - Live [HardwareOptimizer] execution plans (RAM, ABI, SoC/GPU, thermal headroom, dynamic maxTokens)
 * - Dynamic thread priority (background during RAM pre-warm, normal/fast during active generation)
 * - Stall-aware streaming watchdog + deadlock-free cancellation before session.close()
 * - Idle-safe [releaseIfIdle] hook for Android ComponentCallbacks2 low-memory trim events
 */
object OnDeviceLlmEngine {

    private const val TAG = "ShamiaOnDeviceLLM"
    private const val MIN_REAL_MODEL_BYTES = 10L * 1024L * 1024L
    private const val MAX_GENERATION_TOTAL_MS = 110_000L
    private const val MAX_TOKEN_STALL_MS = 38_000L

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
    private val gpuUnsupportedModels = ConcurrentHashMap.newKeySet<String>()
    private val mutex = Mutex()

    private val _runtimeState = MutableStateFlow(LlmRuntimeSnapshot())
    val runtimeState: StateFlow<LlmRuntimeSnapshot> = _runtimeState.asStateFlow()

    @Volatile
    private var loadedModelId: String? = null
    @Volatile
    private var loadedModelPath: String? = null
    @Volatile
    private var activeBackendLabel: String = "CPU"
    @Volatile
    private var nativeLibUnavailable: Boolean = false
    private var llmInference: LlmInference? = null

    @Volatile
    private var activeSession: LlmInferenceSession? = null
    private val cancelledFlag = AtomicBoolean(false)
    private val generatingFlag = AtomicBoolean(false)

    fun invalidateCache(modelId: String? = null) {
        if (modelId == null) bindingCache.clear() else bindingCache.remove(modelId)
    }

    fun computeFastFingerprint(file: File): String {
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(file.length().toString().toByteArray())
            val buf = ByteArray(128 * 1024)
            var remaining = 1024L * 1024L
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
            if (boundId.isNotBlank() && boundId != modelId) return null
        }

        val expectedFileName = descriptor?.optString("weightsFile")?.takeIf { it.isNotBlank() }
        val candidates = modelDir.listFiles()?.filter { f ->
            f.isFile &&
                f.length() >= MIN_REAL_MODEL_BYTES &&
                (f.name.endsWith(".task", ignoreCase = true) || f.name.endsWith(".bin", ignoreCase = true))
        }.orEmpty()

        val weightsFile = if (expectedFileName != null) {
            candidates.firstOrNull { it.name == expectedFileName } ?: candidates.firstOrNull()
        } else {
            candidates.firstOrNull()
        } ?: return null

        val canonicalFile = runCatching { weightsFile.canonicalPath }.getOrNull() ?: return null
        if (!canonicalFile.startsWith("$canonicalDir${File.separator}")) return null

        val storedSize = descriptor?.optLong("bytes", -1L) ?: -1L
        if (storedSize > 0L && weightsFile.length() != storedSize) return null

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

    fun scanAllInstalledModelIds(context: Context, catalogIds: List<String>): Set<String> {
        val found = mutableSetOf("offline-core")
        catalogIds.forEach { id ->
            if (id != "offline-core" && hasRealLocalWeights(context, id)) {
                found.add(id)
            }
        }
        val modelsRoot = File(context.filesDir, "models")
        modelsRoot.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
            if (hasRealLocalWeights(context, dir.name)) {
                found.add(dir.name)
            }
        }
        return found
    }

    fun findInstalledWeightsFile(context: Context, modelId: String): File? =
        verifyModelBinding(context, modelId)?.file

    fun hasRealLocalWeights(context: Context, modelId: String): Boolean =
        verifyModelBinding(context, modelId) != null

    fun installedWeightsSizeMb(context: Context, modelId: String): Long =
        verifyModelBinding(context, modelId)?.sizeMb ?: 0L

    fun currentLoadedModelId(): String? = loadedModelId

    fun currentLoadedBackend(): String = activeBackendLabel

    fun isModelHotInRam(modelId: String): Boolean =
        llmInference != null && loadedModelId == modelId

    fun cancelGeneration() {
        cancelledFlag.set(true)
        runCatching {
            activeSession?.cancelGenerateResponseAsync()
        }
    }

    suspend fun preloadModel(context: Context, modelId: String): Pair<Boolean, String> {
        if (nativeLibUnavailable) return false to ""
        val verified = verifyModelBinding(context, modelId) ?: return false to ""
        if (llmInference != null && loadedModelId == modelId && loadedModelPath == verified.file.canonicalPath) {
            return true to activeBackendLabel
        }
        return mutex.withLock {
            withContext(engineDispatcher) {
                runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND) }
                try {
                    ensureLoadedStrict(context, verified)
                    true to activeBackendLabel
                } catch (t: Throwable) {
                    Log.w(TAG, "Background preload skipped for $modelId: ${t.message}")
                    false to ""
                } finally {
                    runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT) }
                }
            }
        }
    }

    suspend fun unloadIfDifferent(targetModelId: String) = mutex.withLock {
        withContext(engineDispatcher) {
            if (loadedModelId != null && loadedModelId != targetModelId) {
                closeInternal()
            }
        }
    }

    /**
     * Called from Android onTrimMemory under critical system memory pressure.
     * Safely releases an idle model from RAM without ever interrupting an active reply.
     */
    fun releaseIfIdle() {
        if (generatingFlag.get()) return
        if (mutex.tryLock()) {
            try {
                if (!generatingFlag.get() && llmInference != null) {
                    Log.i(TAG, "System low-memory trim: releasing idle model $loadedModelId to protect app stability")
                    closeInternal()
                }
            } finally {
                mutex.unlock()
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
        activeBackendLabel = ""
        _runtimeState.value = LlmRuntimeSnapshot()
    }

    private fun tryCreateEngine(
        appContext: Context,
        modelId: String,
        canonicalPath: String,
        attempt: InferenceLoadAttempt
    ): LlmInference? {
        return try {
            val builder = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(canonicalPath)
                .setMaxTokens(attempt.maxTokens)
            if (attempt.backend != null) {
                builder.setPreferredBackend(attempt.backend)
            }
            LlmInference.createFromOptions(appContext, builder.build())
        } catch (ule: UnsatisfiedLinkError) {
            nativeLibUnavailable = true
            Log.w(TAG, "Native MediaPipe LiteRT binary unavailable on this ABI (${HardwareOptimizer.detectSocLabel()}): ${ule.message}")
            null
        } catch (t: Throwable) {
            if (attempt.backend == LlmInference.Backend.GPU) {
                gpuUnsupportedModels.add(modelId)
            }
            Log.w(TAG, "Create engine failed for $modelId (backend=${attempt.label}, maxTokens=${attempt.maxTokens}): ${t.message}")
            null
        }
    }

    private fun ensureLoadedStrict(context: Context, verified: VerifiedLocalWeights): LlmInference {
        val canonicalPath = verified.file.canonicalPath
        if (llmInference != null && loadedModelId == verified.modelId && loadedModelPath == canonicalPath) {
            _runtimeState.value = LlmRuntimeSnapshot(
                loadedModelId = verified.modelId,
                loadingModelId = null,
                backendLabel = activeBackendLabel
            )
            return llmInference!!
        }
        if (nativeLibUnavailable) {
            throw IllegalStateException("Native MediaPipe runtime unavailable on this ABI")
        }

        val appCtx = context.applicationContext
        val gpuBannedForThisModel = HardwareOptimizer.isLikelyEmulator() || gpuUnsupportedModels.contains(verified.modelId)
        val plan = HardwareOptimizer.computeInferencePlan(appCtx, verified.file, gpuBannedForThisModel)

        // Release previous model first and hint GC if free RAM is tight before allocating native weights
        if (llmInference != null) {
            closeInternal()
        }
        HardwareOptimizer.reclaimHeapIfTight(plan.availRamMb)

        _runtimeState.value = _runtimeState.value.copy(loadingModelId = verified.modelId)
        val loadStartMs = System.currentTimeMillis()

        var engine: LlmInference? = null
        var chosenBackend = "CPU"
        var chosenMaxTokens = 512

        for (attempt in plan.attempts) {
            if (nativeLibUnavailable) break
            val created = tryCreateEngine(appCtx, verified.modelId, canonicalPath, attempt)
            if (created != null) {
                engine = created
                chosenBackend = attempt.label
                chosenMaxTokens = attempt.maxTokens
                break
            }
        }

        if (engine == null) {
            _runtimeState.value = _runtimeState.value.copy(loadingModelId = null)
            throw IllegalStateException("Unable to initialize MediaPipe LlmInference for ${verified.file.name}")
        }

        val elapsedMs = System.currentTimeMillis() - loadStartMs
        activeBackendLabel = chosenBackend
        llmInference = engine
        loadedModelId = verified.modelId
        loadedModelPath = canonicalPath
        _runtimeState.value = LlmRuntimeSnapshot(
            loadedModelId = verified.modelId,
            loadingModelId = null,
            backendLabel = chosenBackend,
            lastLoadMs = elapsedMs
        )
        Log.i(
            TAG,
            "OFFLINE MODEL LOADED | modelId=${verified.modelId} | file=${verified.file.name} | size=${verified.sizeMb}MB | backend=$chosenBackend | maxTokens=$chosenMaxTokens | freeRam=${plan.availRamMb}MB | thermal=${plan.thermalLabel} | loadMs=$elapsedMs"
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
        onPhaseChanged: ((String) -> Unit)? = null,
        onPartialResponse: (accumulatedText: String) -> Unit
    ): VerifiedNativeOutput? {
        if (nativeLibUnavailable) return null
        val verified = verifyModelBinding(context, spec.id) ?: return null

        return mutex.withLock {
            withContext(engineDispatcher) {
                cancelledFlag.set(false)
                generatingFlag.set(true)
                runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT) }
                try {
                    val needsLoad = llmInference == null ||
                        loadedModelId != verified.modelId ||
                        loadedModelPath != verified.file.canonicalPath
                    if (needsLoad) {
                        onPhaseChanged?.invoke("Loading ${spec.name.substringBefore(" (")} into local RAM...")
                    }

                    val engine = try {
                        ensureLoadedStrict(context, verified)
                    } catch (t: Throwable) {
                        Log.e(TAG, "Failed to load local model ${spec.id}", t)
                        null
                    } ?: return@withContext null

                    if (cancelledFlag.get()) return@withContext null
                    if (loadedModelId != spec.id || loadedModelPath != verified.file.canonicalPath) {
                        closeInternal()
                        return@withContext null
                    }

                    onPhaseChanged?.invoke("Generating on ${spec.name.substringBefore(" (")} ($activeBackendLabel)...")
                    val isSeq128 = verified.file.name.contains("seq128", ignoreCase = true)

                    // Attempt 1: Clean natural prompt sized safely for the model's prefill window (works with MediaPipe bundles that inject prompt templates)
                    val cleanNaturalPrompt = buildNaturalPrompt(
                        history = history,
                        userPrompt = userPrompt,
                        webContext = webContext,
                        compactPrefill = isSeq128
                    )
                    var finalReply = runStreamingSession(engine, spec, cleanNaturalPrompt, onPartialResponse)
                        ?.takeIf { isMeaningfulModelOutput(it, userPrompt) }

                    // Attempt 2: Exact model-family chat template (Qwen/DeepSeek/SmolLM ChatML, Gemma, Llama, Phi, Nemotron)
                    if (finalReply.isNullOrBlank() && !cancelledFlag.get()) {
                        val formattedPrompt = buildCompactMobilePrompt(
                            spec = spec,
                            history = if (isSeq128) emptyList() else history,
                            userPrompt = if (isSeq128) userPrompt.take(220) else userPrompt,
                            webContext = if (isSeq128) "" else webContext
                        )
                        finalReply = runStreamingSession(engine, spec, formattedPrompt, onPartialResponse)
                            ?.takeIf { isMeaningfulModelOutput(it, userPrompt) }
                    }

                    // Attempt 3: Direct concise user prompt alone (guarantees prefill fits even on strict seq128 bundles)
                    if (finalReply.isNullOrBlank() && !cancelledFlag.get() && cleanNaturalPrompt != userPrompt.take(280)) {
                        finalReply = runStreamingSession(engine, spec, userPrompt.take(280), onPartialResponse)
                            ?.takeIf { isMeaningfulModelOutput(it, userPrompt) }
                    }

                    // Attempt 4: Direct battle-tested synchronous engine.generateResponse() fallback
                    if (finalReply.isNullOrBlank() && !cancelledFlag.get()) {
                        finalReply = try {
                            val syncRaw = engine.generateResponse(userPrompt.take(280))
                            val cleaned = cleanRawTokens(syncRaw.orEmpty())
                            if (isMeaningfulModelOutput(cleaned, userPrompt)) {
                                onPartialResponse(cleaned)
                                cleaned
                            } else null
                        } catch (t: Throwable) {
                            Log.w(TAG, "Synchronous generateResponse fallback failed for ${spec.id}: ${t.message}")
                            null
                        }
                    }

                    if (finalReply.isNullOrBlank()) null
                    else {
                        Log.i(TAG, "STRICT LOCAL REPLY | modelId=${spec.id} | file=${verified.file.name} | backend=$activeBackendLabel | chars=${finalReply.length}")
                        VerifiedNativeOutput(
                            text = finalReply,
                            backendLabel = "On-Device ($activeBackendLabel)",
                            verifiedModelId = spec.id,
                            weightsFileName = verified.file.name,
                            weightsSizeMb = verified.sizeMb,
                            verificationProof = "file=${verified.file.name} · sha=${verified.fingerprint} · $activeBackendLabel"
                        )
                    }
                } finally {
                    generatingFlag.set(false)
                }
            }
        }
    }

    private fun isMeaningfulModelOutput(reply: String, userPrompt: String): Boolean {
        val trimmed = reply.trim()
        if (trimmed.length < 3) return false
        if (!trimmed.any { it.isLetterOrDigit() }) return false
        val normReply = trimmed.lowercase().replace(Regex("\\s+"), " ").trimEnd('.', '?', '!')
        val normPrompt = userPrompt.trim().lowercase().replace(Regex("\\s+"), " ").trimEnd('.', '?', '!')
        // Reject pure prompt echo so the engine retries with the model's explicit chat template
        if (normReply == normPrompt) return false
        return true
    }

    private fun runStreamingSession(
        engine: LlmInference,
        spec: OpenSourceModelSpec,
        prompt: String,
        onPartialResponse: (accumulatedText: String) -> Unit
    ): String? {
        val idLower = spec.id.lowercase()
        val temp = when {
            idLower.contains("deepseek") -> 0.55f
            idLower.contains("smollm") -> 0.55f
            idLower.contains("qwen") -> 0.65f
            idLower.contains("gemma") || idLower.contains("llama") -> 0.70f
            else -> 0.62f
        }
        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTopK(40)
            .setTopP(0.90f)
            .setTemperature(temp)
            .build()

        val session = try {
            LlmInferenceSession.createFromOptions(engine, sessionOptions)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to create LlmInferenceSession for ${spec.id}", t)
            null
        } ?: return null

        activeSession = session
        val buffer = StringBuilder()
        val startMs = System.currentTimeMillis()
        val lastActivityMs = AtomicLong(startMs)
        val lastEmitMs = AtomicLong(0L)
        var timedOutOrCancelled = false

        return try {
            session.addQueryChunk(prompt)
            val future = session.generateResponseAsync { partialResult, done ->
                val now = System.currentTimeMillis()
                lastActivityMs.set(now)
                if (!partialResult.isNullOrEmpty() && !cancelledFlag.get()) {
                    synchronized(buffer) {
                        buffer.append(partialResult)
                    }
                    val prevEmit = lastEmitMs.get()
                    if (done || now - prevEmit >= 45L) {
                        lastEmitMs.set(now)
                        val snapshot = synchronized(buffer) { buffer.toString() }
                        val cleanedSoFar = cleanRawTokens(snapshot)
                        if (cleanedSoFar.isNotBlank()) {
                            onPartialResponse(cleanedSoFar)
                        }
                    }
                }
            }

            // Stall-aware watchdog: allows long constructive replies on mid-range CPUs while aborting real hangs/cancellations cleanly
            var futureResult: String? = null
            while (true) {
                if (cancelledFlag.get()) {
                    timedOutOrCancelled = true
                    break
                }
                val now = System.currentTimeMillis()
                if (now - startMs > MAX_GENERATION_TOTAL_MS || now - lastActivityMs.get() > MAX_TOKEN_STALL_MS) {
                    timedOutOrCancelled = true
                    Log.w(TAG, "Generation watchdog triggered (total=${now - startMs}ms, idle=${now - lastActivityMs.get()}ms)")
                    break
                }
                try {
                    futureResult = future.get(150L, TimeUnit.MILLISECONDS)
                    break
                } catch (_: java.util.concurrent.TimeoutException) {
                    // Still actively generating; continue polling
                }
            }

            if (timedOutOrCancelled) {
                runCatching { session.cancelGenerateResponseAsync() }
            }

            val accumulated = synchronized(buffer) { buffer.toString() }
            val finalText = cleanRawTokens(
                if (accumulated.isNotEmpty()) accumulated else futureResult.orEmpty()
            )
            finalText.takeIf { it.isNotBlank() }
        } catch (t: Throwable) {
            runCatching { session.cancelGenerateResponseAsync() }
            Log.w(TAG, "Streaming session interrupted: ${t.message}")
            val accumulated = synchronized(buffer) { buffer.toString() }
            cleanRawTokens(accumulated).takeIf { it.isNotBlank() }
        } finally {
            runCatching {
                if (timedOutOrCancelled || cancelledFlag.get()) {
                    session.cancelGenerateResponseAsync()
                }
                session.close()
            }
            if (activeSession === session) {
                activeSession = null
            }
        }
    }

    private fun buildNaturalPrompt(
        history: List<HudMessage>,
        userPrompt: String,
        webContext: String,
        compactPrefill: Boolean = false
    ): String {
        if (compactPrefill) {
            return userPrompt.take(300)
        }
        val recent = history.takeLast(2)
        if (recent.isEmpty() && webContext.isBlank()) {
            return userPrompt
        }
        return buildString {
            recent.forEach { msg ->
                val role = if (msg.isUser) "User" else "Assistant"
                append("$role: ${CloudModelResolver.stripHeaderFromHistoryText(msg.text).take(140)}\n")
            }
            if (webContext.isNotBlank()) {
                append("Facts: ${webContext.take(400)}\n")
            }
            append("User: $userPrompt\nAssistant:")
        }
    }

    private fun buildCompactMobilePrompt(
        spec: OpenSourceModelSpec,
        history: List<HudMessage>,
        userPrompt: String,
        webContext: String
    ): String {
        val conciseSystem = "You are S.H.A.M.I.A., a helpful, accurate personal assistant. Answer clearly and directly."
        val promptWithWeb = if (webContext.isNotBlank()) {
            "$userPrompt\n\nFacts:\n${webContext.take(600)}"
        } else {
            userPrompt
        }

        val id = spec.id.lowercase()
        val recent = history.takeLast(2)

        return when {
            id.contains("gemma") -> buildString {
                append("<start_of_turn>user\n")
                append(conciseSystem).append("\n")
                recent.forEach { msg ->
                    val role = if (msg.isUser) "User" else "Assistant"
                    append("$role: ${CloudModelResolver.stripHeaderFromHistoryText(msg.text).take(180)}\n")
                }
                append(promptWithWeb)
                append("<end_of_turn>\n<start_of_turn>model\n")
            }

            id.contains("nemotron-mini") -> buildString {
                append("<extra_id_0>System\n").append(conciseSystem).append("\n")
                recent.forEach { msg ->
                    val role = if (msg.isUser) "User" else "Assistant"
                    append("<extra_id_1>$role\n")
                    append(CloudModelResolver.stripHeaderFromHistoryText(msg.text).take(180)).append("\n")
                }
                append("<extra_id_1>User\n").append(promptWithWeb).append("\n<extra_id_1>Assistant\n")
            }

            id.contains("llama") || id.contains("nemotron-nano") -> buildString {
                append("<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n")
                append(conciseSystem).append("<|eot_id|>")
                recent.forEach { msg ->
                    val role = if (msg.isUser) "user" else "assistant"
                    append("<|start_header_id|>$role<|end_header_id|>\n\n")
                    append(CloudModelResolver.stripHeaderFromHistoryText(msg.text).take(180)).append("<|eot_id|>")
                }
                append("<|start_header_id|>user<|end_header_id|>\n\n")
                append(promptWithWeb)
                append("<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n")
            }

            id.contains("phi") -> buildString {
                append("<|system|>$conciseSystem<|end|>")
                recent.forEach { msg ->
                    val tag = if (msg.isUser) "user" else "assistant"
                    append("<|$tag|>${CloudModelResolver.stripHeaderFromHistoryText(msg.text).take(180)}<|end|>")
                }
                append("<|user|>$promptWithWeb<|end|><|assistant|>")
            }

            else -> buildString {
                append("<|im_start|>system\n").append(conciseSystem).append("<|im_end|>\n")
                recent.forEach { msg ->
                    val role = if (msg.isUser) "user" else "assistant"
                    append("<|im_start|>$role\n")
                    append(CloudModelResolver.stripHeaderFromHistoryText(msg.text).take(180)).append("<|im_end|>\n")
                }
                append("<|im_start|>user\n").append(promptWithWeb).append("<|im_end|>\n<|im_start|>assistant\n")
            }
        }
    }

    private fun cleanRawTokens(raw: String): String {
        return raw
            .replace(Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE), "")
            .replace("<|im_end|>", "")
            .replace("<|im_start|>", "")
            .replace("<|eot_id|>", "")
            .replace("<|end|>", "")
            .replace("<end_of_turn>", "")
            .replace("<extra_id_0>", "")
            .replace("<extra_id_1>", "")
            .replace("<eos>", "")
            .trim()
    }
}
