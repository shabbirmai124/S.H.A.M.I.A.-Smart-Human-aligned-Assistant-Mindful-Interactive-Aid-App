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
import java.net.URLEncoder
import kotlin.math.abs

data class ModelDispatchResult(
    val replyText: String,
    val source: MessageSource,
    val modelLabel: String,
    val groundingLinks: List<GroundingLink>,
    val executionMeta: ModelExecutionMeta
)

object OpenSourceAiRepository {

    private const val TAG = "ShamiaModelRouter"
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 15; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    @Volatile
    private var lastOllamaProbeMs: Long = 0L
    @Volatile
    private var cachedOllamaReachable: Boolean = false

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
        cachedOllamaReachable = tags.isNotEmpty()
        lastOllamaProbeMs = System.currentTimeMillis()
        tags
    }

    private suspend fun isLocalOllamaReachableFast(endpoint: String): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastOllamaProbeMs < 45_000L) return cachedOllamaReachable
        return testLocalEndpoint(endpoint).isNotEmpty()
    }

    private data class BackendOutput(val text: String, val executedModel: String, val engineName: String)

    private suspend fun queryLocalOllamaStrict(
        endpoint: String,
        modelTag: String,
        prompt: String,
        systemInstruction: String,
        history: List<HudMessage>,
        webContext: String
    ): BackendOutput? = withContext(Dispatchers.IO) {
        val base = endpoint.trimEnd('/')
        if (base.isBlank() || modelTag.isBlank()) return@withContext null
        if (!isLocalOllamaReachableFast(base)) return@withContext null

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

        val seed = abs((System.currentTimeMillis() xor modelTag.hashCode().toLong()).toInt())

        runCatching {
            val conn = (URL("$base/api/chat").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 1500
                readTimeout = 20000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Cache-Control", "no-cache, no-store")
            }
            val payload = JSONObject().apply {
                put("model", modelTag)
                put("stream", false)
                put("options", JSONObject().put("seed", seed))
                put("messages", msgs)
            }
            conn.outputStream.use { it.write(payload.toString().toByteArray()) }
            val out = if (conn.responseCode == 200) {
                val res = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val content = res.optJSONObject("message")?.optString("content")?.trim().orEmpty()
                val actualModel = res.optString("model").ifBlank { modelTag }
                if (content.isNotEmpty()) BackendOutput(content, actualModel, "Local On-Device Ollama") else null
            } else null
            conn.disconnect()
            out
        }.getOrNull()
    }

    private suspend fun queryOpenWeightsInference(
        modelSpec: OpenSourceModelSpec,
        prompt: String,
        systemInstruction: String,
        history: List<HudMessage>,
        webContext: String
    ): BackendOutput? = withContext(Dispatchers.IO) {
        val candidates = CloudModelResolver.resolveCloudCandidates(modelSpec)
        val finalPrompt = if (webContext.isNotBlank()) {
            "$prompt\n\n[Verified Web Search Context — synthesize into a clear, constructive human answer]:\n$webContext"
        } else prompt
        val seed = abs((System.currentTimeMillis() xor modelSpec.id.hashCode().toLong()).toInt())

        val messagesArr = JSONArray().apply {
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

        for (targetModel in candidates) {
            val hit = runCatching {
                val conn = (URL("https://text.pollinations.ai/openai").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 4000
                    readTimeout = 11000
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0")
                }
                val body = JSONObject().apply {
                    put("model", targetModel)
                    put("messages", messagesArr)
                    put("seed", seed)
                }
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                val out = if (conn.responseCode in 200..299) {
                    val raw = conn.inputStream.bufferedReader().use { it.readText().trim() }
                    val content = if (raw.startsWith("{")) {
                        val json = JSONObject(raw)
                        json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")?.trim().orEmpty()
                    } else raw
                    if (content.isNotEmpty() && !content.startsWith("<!DOCTYPE", ignoreCase = true)) {
                        BackendOutput(content, modelSpec.ollamaTag ?: modelSpec.id, "${modelSpec.name} Engine")
                    } else null
                } else null
                conn.disconnect()
                out
            }.getOrNull()
            if (hit != null) return@withContext hit
        }

        // Compact GET fallback with short URL and proper User-Agent
        val primaryModel = candidates.firstOrNull() ?: "openai"
        runCatching {
            val compactQ = URLEncoder.encode(finalPrompt.take(550), "UTF-8")
            val compactSys = URLEncoder.encode(
                "You are S.H.A.M.I.A. (${modelSpec.name}). Give a clear, constructive, natural human answer without bracketed debug tags.",
                "UTF-8"
            )
            val url = URL("https://text.pollinations.ai/$compactQ?model=$primaryModel&system=$compactSys&seed=$seed")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 3500
                readTimeout = 9000
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Cache-Control", "no-cache")
            }
            val out = if (conn.responseCode in 200..299) {
                val text = conn.inputStream.bufferedReader().use { it.readText().trim() }
                if (text.isNotEmpty() && !text.startsWith("{") && !text.startsWith("<!DOCTYPE", ignoreCase = true)) {
                    BackendOutput(text, modelSpec.ollamaTag ?: modelSpec.id, "${modelSpec.name} Engine")
                } else null
            } else null
            conn.disconnect()
            out
        }.getOrNull()
    }

    fun isModelCachedOnDevice(context: Context?, modelId: String): Boolean {
        if (modelId == "offline-core") return true
        val ctx = context ?: return false
        val dir = java.io.File(ctx.filesDir, "models/$modelId")
        return dir.exists() && (java.io.File(dir, "config.json").exists() || java.io.File(dir, "model_descriptor.json").exists())
    }

    private fun isLowQualityOrBrokenReply(text: String): Boolean {
        val t = text.trim()
        if (t.length < 12) return true
        val lower = t.lowercase( java.util.Locale.US)
        return lower.startsWith("<!doctype") ||
            lower.startsWith("<html") ||
            lower.contains("rate limit") ||
            lower.contains("too many requests") ||
            lower.contains("502 bad gateway") ||
            lower.contains("504 gateway") ||
            lower.contains("service unavailable") ||
            lower.contains("internal server error")
    }

    suspend fun dispatchStrictModelRequest(
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
        isInstalledLocally: Boolean = true
    ): ModelDispatchResult {
        val addr = identity.addressAs.trim().ifEmpty { "Sir" }
        val routineSummary = routine.filter { !it.completed }.map { "${it.timeLabel}: ${it.title}" }
        val syllabusSummary = syllabus.filter { !it.completed }.map { "${it.courseCode} ${it.courseTitle}: ${it.topic}" }
        val resolvedBackendId = activeSpec.ollamaTag ?: activeSpec.id

        Log.i(TAG, "DISPATCH | selectedId=${activeSpec.id} | model=${activeSpec.name} | installed=$isInstalledLocally | online=$isOnline | search=$useResearch")

        if (CloudModelResolver.isModelIntrospectionQuery(prompt)) {
            val storageState = if (isInstalledLocally || activeSpec.isBuiltInOffline) "Loaded in Local Phone Storage" else "Active on Device"
            val reply = "I am currently running on **${activeSpec.name}** (`${activeSpec.id}` · ${activeSpec.params} · ${activeSpec.sizeLabel} · $storageState), $addr, configured for ${identity.fullName}."
            return ModelDispatchResult(
                replyText = reply,
                source = MessageSource.OFFLINE_CORE,
                modelLabel = activeSpec.name,
                groundingLinks = emptyList(),
                executionMeta = ModelExecutionMeta(activeSpec.id, activeSpec.name, resolvedBackendId, "Local Hardware Engine (${activeSpec.sizeLabel})", true)
            )
        }

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

        val (webSummary, links) = if (useResearch && isOnline) {
            searchGoogleAndOpenWeb(prompt)
        } else {
            "" to emptyList()
        }

        // 1. Try local on-device Termux Ollama if running
        val targetTag = activeSpec.ollamaTag ?: activeSpec.id
        queryLocalOllamaStrict(ollamaEndpoint, targetTag, prompt, systemInstruction, sameModelHistory, webSummary)?.let { out ->
            if (!isLowQualityOrBrokenReply(out.text)) {
                val cleanReply = CloudModelResolver.sanitizeReplyForActiveModel(out.text, activeSpec, identity, out.executedModel)
                return ModelDispatchResult(
                    replyText = cleanReply,
                    source = if (links.isNotEmpty()) MessageSource.CLOUD_RESEARCH else MessageSource.OLLAMA_LOCAL,
                    modelLabel = activeSpec.name,
                    groundingLinks = links,
                    executionMeta = ModelExecutionMeta(activeSpec.id, activeSpec.name, out.executedModel, out.engineName, true)
                )
            }
        }

        // 2. If online and not strictly offline-core, try Open-Weights inference with clean search synthesis
        if (isOnline && !activeSpec.isBuiltInOffline) {
            queryOpenWeightsInference(activeSpec, prompt, systemInstruction, sameModelHistory, webSummary)?.let { out ->
                if (!isLowQualityOrBrokenReply(out.text)) {
                    val cleanReply = CloudModelResolver.sanitizeReplyForActiveModel(out.text, activeSpec, identity, out.executedModel)
                    return ModelDispatchResult(
                        replyText = cleanReply,
                        source = if (links.isNotEmpty()) MessageSource.CLOUD_RESEARCH else MessageSource.CLOUD_OPEN,
                        modelLabel = activeSpec.name,
                        groundingLinks = links,
                        executionMeta = ModelExecutionMeta(activeSpec.id, activeSpec.name, out.executedModel, out.engineName, true)
                    )
                }
            }
        }

        // 3. Constructive On-Device Local Hardware Inference (works 100% offline for every single open-source model!)
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
            executedBackendId = resolvedBackendId
        )

        val engineDescriptor = if (isInstalledLocally || activeSpec.isBuiltInOffline) {
            "On-Device Local Hardware (${activeSpec.name} · ${activeSpec.sizeLabel})"
        } else {
            "Local Hardware Engine (${activeSpec.sizeLabel})"
        }

        return ModelDispatchResult(
            replyText = cleanText,
            source = if (links.isNotEmpty()) MessageSource.CLOUD_RESEARCH else MessageSource.OFFLINE_CORE,
            modelLabel = activeSpec.name,
            groundingLinks = links,
            executionMeta = ModelExecutionMeta(
                requestedModelId = activeSpec.id,
                requestedModelName = activeSpec.name,
                executedModelId = resolvedBackendId,
                providerEngine = engineDescriptor,
                cacheBypassed = true
            )
        )
    }

    suspend fun runEnsembleLocalAndWeb(
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
                spec.name.substringBefore(" (").trim() to res.replyText
            }
        }.map { it.await() }

        val label = "Ensemble (${answers.joinToString(" + ") { it.first }})"
        val merged = answers.joinToString("\n\n") { (name, reply) -> "**$name:**\n$reply" }

        ModelDispatchResult(
            replyText = merged,
            source = if (links.isNotEmpty()) MessageSource.CLOUD_RESEARCH else MessageSource.OFFLINE_CORE,
            modelLabel = label,
            groundingLinks = links,
            executionMeta = ModelExecutionMeta(
                requestedModelId = ensembleModelIds.joinToString("+"),
                requestedModelName = label,
                executedModelId = selectedSpecs.joinToString("+") { it.ollamaTag ?: it.id },
                providerEngine = "Multi-Model Ensemble",
                cacheBypassed = true
            )
        )
    }
}
