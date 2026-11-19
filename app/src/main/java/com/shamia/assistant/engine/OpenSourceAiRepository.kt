package com.shamia.assistant.engine

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.shamia.assistant.model.AmbientTrack
import com.shamia.assistant.model.ConversationalIntent
import com.shamia.assistant.model.DeviceHardwareSpec
import com.shamia.assistant.model.GroundingLink
import com.shamia.assistant.model.HudMessage
import com.shamia.assistant.model.MessageSource
import com.shamia.assistant.model.ModelExecutionMeta
import com.shamia.assistant.model.OfflineVoiceModelSpec
import com.shamia.assistant.model.OpenSourceModelSpec
import com.shamia.assistant.model.PermissionCapability
import com.shamia.assistant.model.PermissionPolicy
import com.shamia.assistant.model.RoutineItem
import com.shamia.assistant.model.SyllabusItem
import com.shamia.assistant.model.UserIdentityProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

data class ModelDispatchResult(
    val replyText: String,
    val source: MessageSource,
    val modelLabel: String,
    val groundingLinks: List<GroundingLink>,
    val executionMeta: ModelExecutionMeta
)

object OpenSourceAiRepository {

    private const val TAG = "ShamiaStrictRouter"

    @Volatile
    private var lastOllamaProbeMs: Long = 0L
    @Volatile
    private var cachedOllamaTags: List<String> = emptyList()

    val CATALOG: List<OpenSourceModelSpec> get() = ModelCatalog.CATALOG
    val VOICE_MODELS: List<OfflineVoiceModelSpec> get() = ModelCatalog.VOICE_MODELS
    val AMBIENT_TRACKS: List<AmbientTrack> get() = ModelCatalog.AMBIENT_TRACKS

    fun detectHardware(context: Context): DeviceHardwareSpec {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val ramGb = maxOf(4, Math.round(info.totalMem / (1024.0 * 1024.0 * 1024.0)).toInt())
        val availRamMb = (info.availMem / (1024L * 1024L)).toInt().coerceAtLeast(512)
        val isLowMem = info.lowMemory && availRamMb < 384
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(4)
        val label = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
        val tier = if (isLowMem) {
            "Low-RAM Safe Mode ($ramGb GB)"
        } else {
            "$availRamMb MB Free · Local Hardware Ready"
        }
        return DeviceHardwareSpec(
            ramGb = ramGb,
            availRamMb = availRamMb,
            isLowMemory = isLowMem,
            cpuCores = cores,
            deviceLabel = label,
            recommendedTier = tier
        )
    }

    suspend fun searchGoogleAndOpenWeb(query: String): Pair<String, List<GroundingLink>> =
        WebSearchClient.searchGoogleAndOpenWeb(query)

    suspend fun testLocalEndpoint(endpoint: String): List<String> = withContext(Dispatchers.IO) {
        val base = endpoint.trimEnd('/')
        if (base.isBlank()) return@withContext emptyList()
        val tags = runCatching {
            val url = URL("$base/api/tags")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 900
                readTimeout = 1500
                setRequestProperty("Cache-Control", "no-cache")
            }
            val models = if (conn.responseCode == 200) {
                val res = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val arr = res.optJSONArray("models") ?: JSONArray()
                (0 until arr.length()).mapNotNull {
                    arr.getJSONObject(it).optString("name").takeIf { n -> n.isNotEmpty() }
                }
            } else emptyList()
            conn.disconnect()
            models
        }.getOrDefault(emptyList())
        cachedOllamaTags = tags
        lastOllamaProbeMs = System.currentTimeMillis()
        tags
    }

    private suspend fun getInstalledOllamaTagsFast(endpoint: String): List<String> {
        val now = System.currentTimeMillis()
        if (now - lastOllamaProbeMs < 15_000L) return cachedOllamaTags
        return testLocalEndpoint(endpoint)
    }

    private data class VerifiedOllamaOutput(
        val text: String,
        val executedModel: String,
        val engineName: String,
        val proof: String
    )

    /**
     * Strictly queries local Ollama ONLY if the exact requested model tag is verified present in `/api/tags`,
     * and strictly asserts that Ollama's JSON response echoes the exact same model tag.
     */
    private suspend fun queryLocalOllamaStrict(
        endpoint: String,
        requestedTag: String,
        prompt: String,
        systemInstruction: String,
        history: List<HudMessage>,
        webContext: String
    ): VerifiedOllamaOutput? = withContext(Dispatchers.IO) {
        val base = endpoint.trimEnd('/')
        if (base.isBlank() || requestedTag.isBlank()) return@withContext null

        val installedTags = getInstalledOllamaTagsFast(base)
        val exactInstalledTag = installedTags.firstOrNull { installed ->
            CloudModelResolver.matchesExactOllamaTag(installed, requestedTag)
        } ?: return@withContext null

        val finalPrompt = if (webContext.isNotBlank()) {
            "$prompt\n\n[Verified Web Search Context — synthesize cleanly in your own words without HTML artifacts]:\n$webContext"
        } else prompt

        val msgs = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemInstruction))
            history.takeLast(6).forEach { turn ->
                put(
                    JSONObject()
                        .put("role", if (turn.isUser) "user" else "assistant")
                        .put("content", CloudModelResolver.stripHeaderFromHistoryText(turn.text))
                )
            }
            put(JSONObject().put("role", "user").put("content", finalPrompt))
        }

        val seed = abs((System.currentTimeMillis() xor exactInstalledTag.hashCode().toLong()).toInt())

        runCatching {
            val conn = (URL("$base/api/chat").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 1500
                readTimeout = 30000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Cache-Control", "no-cache, no-store")
            }
            val payload = JSONObject().apply {
                put("model", exactInstalledTag)
                put("stream", false)
                put("options", JSONObject().put("seed", seed))
                put("messages", msgs)
            }
            conn.outputStream.use { it.write(payload.toString().toByteArray()) }
            val out = if (conn.responseCode == 200) {
                val res = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val content = res.optJSONObject("message")?.optString("content")?.trim().orEmpty()
                val echoedModel = res.optString("model").trim()
                if (content.isNotEmpty() && CloudModelResolver.matchesExactOllamaTag(echoedModel, requestedTag)) {
                    VerifiedOllamaOutput(
                        text = content,
                        executedModel = echoedModel,
                        engineName = "Verified Local Ollama ($echoedModel)",
                        proof = "ollama=$echoedModel @ $base"
                    )
                } else {
                    Log.e(TAG, "STRICT OLLAMA MISMATCH REJECTED | requested=$requestedTag | echoed=$echoedModel")
                    null
                }
            } else null
            conn.disconnect()
            out
        }.getOrNull()
    }

    fun isModelCachedOnDevice(context: Context?, modelId: String): Boolean {
        if (modelId == "offline-core") return true
        val ctx = context ?: return false
        return OnDeviceLlmEngine.hasRealLocalWeights(ctx, modelId)
    }

    private fun isLowQualityOrBrokenReply(text: String): Boolean {
        val t = text.trim()
        if (t.length < 2) return true
        val lower = t.lowercase(java.util.Locale.US)
        return lower.startsWith("<!doctype") ||
            lower.startsWith("<html") ||
            lower.contains("502 bad gateway") ||
            lower.contains("504 gateway") ||
            lower.contains("service unavailable") ||
            lower.contains("internal server error")
    }

    private fun isDirectAnswerNeededFromWeb(prompt: String): Boolean {
        val lower = prompt.lowercase(java.util.Locale.US).trim()
        if (lower.length < 4) return false
        if (Regex("^(hi|hello|hey|thanks|thank you|bye|good morning|good night|how are you)\\b").containsMatchIn(lower)) {
            return false
        }
        return !OfflineCompanion.isLikelyKeyboardSmashOrTypo(lower)
    }

    /**
     * Strictly dispatches [prompt] ONLY to the exact selected [activeSpec].
     * Zero cross-model substitution, zero fake cloud aliasing, zero silent fallback to a different engine.
     */
    suspend fun dispatchStrictModelRequest(
        context: Context? = null,
        prompt: String,
        activeSpec: OpenSourceModelSpec,
        identity: UserIdentityProfile,
        detectedIntent: ConversationalIntent,
        permissions: Map<PermissionCapability, PermissionPolicy>,
        tasksSummary: List<String>,
        routine: List<RoutineItem>,
        syllabus: List<SyllabusItem>,
        sameModelHistory: List<HudMessage>,
        ollamaEndpoint: String,
        serverBaseUrl: String,
        useResearch: Boolean,
        isOnline: Boolean = true,
        isInstalledLocally: Boolean = false,
        onStreamToken: ((String) -> Unit)? = null
    ): ModelDispatchResult {
        val addr = identity.addressAs.trim().ifEmpty { "Sir" }
        val routineSummary = routine.filter { !it.completed }.map { "${it.timeLabel}: ${it.title}" }
        val syllabusSummary = syllabus.filter { !it.completed }.map { "${it.courseCode} ${it.courseTitle}: ${it.topic}" }
        val verifiedLocal = context?.let { OnDeviceLlmEngine.verifyModelBinding(it, activeSpec.id) }
        val installedOllamaMatch = if (!activeSpec.isBuiltInOffline && !activeSpec.ollamaTag.isNullOrBlank()) {
            getInstalledOllamaTagsFast(ollamaEndpoint).firstOrNull {
                CloudModelResolver.matchesExactOllamaTag(it, activeSpec.ollamaTag)
            }
        } else null

        Log.i(
            TAG,
            "STRICT DISPATCH | id=${activeSpec.id} | name=${activeSpec.name} | localWeights=${verifiedLocal?.file?.name ?: "none"} | ollamaMatch=${installedOllamaMatch ?: "none"}"
        )

        // 1. Model Introspection Query (only mention the model when explicitly asked)
        if (CloudModelResolver.isModelIntrospectionQuery(prompt)) {
            val reply = "You are currently using **${activeSpec.name}** (${activeSpec.params}), $addr."
            return ModelDispatchResult(
                replyText = reply,
                source = MessageSource.OFFLINE_CORE,
                modelLabel = activeSpec.name,
                groundingLinks = emptyList(),
                executionMeta = ModelExecutionMeta(
                    requestedModelId = activeSpec.id,
                    requestedModelName = activeSpec.name,
                    executedModelId = when {
                        activeSpec.isBuiltInOffline -> "offline-core"
                        verifiedLocal != null -> activeSpec.id
                        installedOllamaMatch != null -> installedOllamaMatch
                        else -> activeSpec.id
                    },
                    providerEngine = "Local",
                    cacheBypassed = true,
                    verifiedExactModel = activeSpec.isBuiltInOffline || verifiedLocal != null || installedOllamaMatch != null,
                    verificationProof = verifiedLocal?.fingerprint ?: installedOllamaMatch ?: activeSpec.id
                )
            )
        }

        val (webSummary, links) = if (useResearch && isOnline) {
            searchGoogleAndOpenWeb(prompt)
        } else {
            "" to emptyList()
        }

        // 2. STRICT CASE A: Built-In S.H.A.M.I.A. Offline Core (explicitly selected by user)
        if (activeSpec.isBuiltInOffline || activeSpec.id == "offline-core") {
            val localReply = OfflineCompanion.generateReply(
                input = prompt,
                addressAs = addr,
                fullName = identity.fullName,
                routine = routine,
                syllabus = syllabus,
                selectedModel = activeSpec,
                webContext = webSummary,
                history = sameModelHistory
            )
            val tipSuffix = localReply.coachingTip?.let {
                "\n[ENG_TIP: ${it.naturalPhrase.orEmpty()} | ${it.tip.orEmpty()}]"
            }.orEmpty()
            val cleanText = CloudModelResolver.sanitizeReplyForActiveModel(
                rawText = "${localReply.text}$tipSuffix",
                activeSpec = activeSpec,
                identity = identity,
                executedBackendId = "offline-core"
            )
            return ModelDispatchResult(
                replyText = cleanText,
                source = if (links.isNotEmpty()) MessageSource.CLOUD_RESEARCH else MessageSource.OFFLINE_CORE,
                modelLabel = activeSpec.name,
                groundingLinks = links,
                executionMeta = ModelExecutionMeta(
                    requestedModelId = activeSpec.id,
                    requestedModelName = activeSpec.name,
                    executedModelId = "offline-core",
                    providerEngine = "Verified Built-In Offline Core",
                    cacheBypassed = true,
                    verifiedExactModel = true,
                    verificationProof = "engine=offline-core"
                )
            )
        }

        val resolvedBackendId = activeSpec.ollamaTag ?: activeSpec.id
        val systemInstruction = ShamiaPrompts.buildShamiaSystemInstruction(
            identity = identity,
            detectedIntent = detectedIntent,
            permissions = permissions,
            tasksSummary = tasksSummary,
            routineSummary = routineSummary,
            syllabusSummary = syllabusSummary,
            activeModel = activeSpec,
            resolvedBackendId = resolvedBackendId
        )

        // 3. STRICT CASE B (Priority 1): Verified On-Device MediaPipe LiteRT Neural Weights for THIS exact modelId
        if (context != null && verifiedLocal != null) {
            OnDeviceLlmEngine.generateOfflineStream(
                context = context,
                spec = activeSpec,
                systemInstruction = systemInstruction,
                history = sameModelHistory,
                userPrompt = prompt,
                webContext = webSummary,
                onPartialResponse = { partial ->
                    onStreamToken?.invoke(
                        CloudModelResolver.sanitizeReplyForActiveModel(partial, activeSpec, identity, activeSpec.id)
                    )
                }
            )?.let { nativeOut ->
                if (nativeOut.verifiedModelId == activeSpec.id && !isLowQualityOrBrokenReply(nativeOut.text)) {
                    val cleanReply = CloudModelResolver.sanitizeReplyForActiveModel(
                        nativeOut.text,
                        activeSpec,
                        identity,
                        nativeOut.verifiedModelId
                    )
                    return ModelDispatchResult(
                        replyText = cleanReply,
                        source = if (links.isNotEmpty()) MessageSource.CLOUD_RESEARCH else MessageSource.OFFLINE_CORE,
                        modelLabel = activeSpec.name,
                        groundingLinks = links,
                        executionMeta = ModelExecutionMeta(
                            requestedModelId = activeSpec.id,
                            requestedModelName = activeSpec.name,
                            executedModelId = nativeOut.verifiedModelId,
                            providerEngine = nativeOut.backendLabel,
                            cacheBypassed = true,
                            verifiedExactModel = true,
                            verificationProof = nativeOut.verificationProof
                        )
                    )
                }
            }
        }

        // 4. STRICT CASE B (Priority 2): Verified Local Termux / LAN Ollama matching THIS exact model tag
        val targetTag = activeSpec.ollamaTag.orEmpty()
        if (targetTag.isNotBlank()) {
            queryLocalOllamaStrict(ollamaEndpoint, targetTag, prompt, systemInstruction, sameModelHistory, webSummary)?.let { out ->
                if (!isLowQualityOrBrokenReply(out.text)) {
                    val cleanReply = CloudModelResolver.sanitizeReplyForActiveModel(out.text, activeSpec, identity, out.executedModel)
                    return ModelDispatchResult(
                        replyText = cleanReply,
                        source = if (links.isNotEmpty()) MessageSource.CLOUD_RESEARCH else MessageSource.OLLAMA_LOCAL,
                        modelLabel = activeSpec.name,
                        groundingLinks = links,
                        executionMeta = ModelExecutionMeta(
                            requestedModelId = activeSpec.id,
                            requestedModelName = activeSpec.name,
                            executedModelId = out.executedModel,
                            providerEngine = out.engineName,
                            cacheBypassed = true,
                            verifiedExactModel = true,
                            verificationProof = out.proof
                        )
                    )
                }
            }
        }

        // 5. Silent background fallback: never announce model status or blocked notices — answer the user's input directly!
        val (effectiveWeb, effectiveLinks) = if (webSummary.isBlank() && isOnline && isDirectAnswerNeededFromWeb(prompt)) {
            searchGoogleAndOpenWeb(prompt)
        } else {
            webSummary to links
        }
        val directReply = OfflineCompanion.generateReply(
            input = prompt,
            addressAs = addr,
            fullName = identity.fullName,
            routine = routine,
            syllabus = syllabus,
            selectedModel = activeSpec,
            webContext = effectiveWeb,
            history = sameModelHistory
        )
        val tipSuffix = directReply.coachingTip?.let {
            "\n[ENG_TIP: ${it.naturalPhrase.orEmpty()} | ${it.tip.orEmpty()}]"
        }.orEmpty()
        val cleanDirect = CloudModelResolver.sanitizeReplyForActiveModel(
            rawText = "${directReply.text}$tipSuffix",
            activeSpec = activeSpec,
            identity = identity,
            executedBackendId = activeSpec.id
        )
        return ModelDispatchResult(
            replyText = cleanDirect,
            source = if (effectiveLinks.isNotEmpty()) MessageSource.CLOUD_RESEARCH else MessageSource.OFFLINE_CORE,
            modelLabel = activeSpec.name,
            groundingLinks = effectiveLinks,
            executionMeta = ModelExecutionMeta(
                requestedModelId = activeSpec.id,
                requestedModelName = activeSpec.name,
                executedModelId = activeSpec.id,
                providerEngine = "Local Offline",
                cacheBypassed = true,
                verifiedExactModel = false,
                verificationProof = "local-direct"
            )
        )
    }

    suspend fun runEnsembleLocalAndWeb(
        context: Context? = null,
        prompt: String,
        identity: UserIdentityProfile,
        detectedIntent: ConversationalIntent,
        permissions: Map<PermissionCapability, PermissionPolicy>,
        tasksSummary: List<String>,
        ollamaEndpoint: String,
        serverBaseUrl: String,
        allModels: List<OpenSourceModelSpec>,
        ensembleModelIds: List<String>,
        useResearch: Boolean,
        routine: List<RoutineItem>,
        syllabus: List<SyllabusItem>,
        isOnline: Boolean = true
    ): ModelDispatchResult = coroutineScope {
        val searchDeferred = async { if (useResearch && isOnline) searchGoogleAndOpenWeb(prompt) else "" to emptyList() }
        val (_, links) = searchDeferred.await()

        val selectedSpecs = ensembleModelIds.mapNotNull { id -> allModels.find { it.id == id } }.take(3)
        val answers = selectedSpecs.map { spec ->
            async {
                val res = dispatchStrictModelRequest(
                    context = context,
                    prompt = prompt,
                    activeSpec = spec,
                    identity = identity,
                    detectedIntent = detectedIntent,
                    permissions = permissions,
                    tasksSummary = tasksSummary,
                    routine = routine,
                    syllabus = syllabus,
                    sameModelHistory = emptyList(),
                    ollamaEndpoint = ollamaEndpoint,
                    serverBaseUrl = serverBaseUrl,
                    useResearch = false,
                    isOnline = isOnline
                )
                Triple(spec.name.substringBefore(" (").trim(), res.replyText, res.executionMeta.verifiedExactModel)
            }
        }.map { it.await() }

        val label = "Ensemble (${answers.joinToString(" + ") { it.first }})"
        val merged = answers.joinToString("\n\n") { (name, reply, _) -> "**$name:**\n$reply" }
        val allVerified = answers.isNotEmpty() && answers.all { it.third }

        ModelDispatchResult(
            replyText = merged,
            source = if (links.isNotEmpty()) MessageSource.CLOUD_RESEARCH else MessageSource.OFFLINE_CORE,
            modelLabel = label,
            groundingLinks = links,
            executionMeta = ModelExecutionMeta(
                requestedModelId = ensembleModelIds.joinToString("+"),
                requestedModelName = label,
                executedModelId = selectedSpecs.joinToString("+") { it.ollamaTag ?: it.id },
                providerEngine = "Verified Multi-Model Ensemble",
                cacheBypassed = true,
                verifiedExactModel = allVerified,
                verificationProof = if (allVerified) "all-members-verified" else "some-members-unloaded"
            )
        )
    }
}
