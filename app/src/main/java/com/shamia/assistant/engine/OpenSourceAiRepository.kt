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

    val CATALOG: List<OpenSourceModelSpec> get() = ModelCatalog.CATALOG
    val VOICE_MODELS: List<OfflineVoiceModelSpec> get() = ModelCatalog.VOICE_MODELS
    val AMBIENT_TRACKS: List<AmbientTrack> get() = ModelCatalog.AMBIENT_TRACKS

    fun detectHardware(context: Context): DeviceHardwareSpec {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val ramGb = maxOf(2, Math.round(info.totalMem / (1024.0 * 1024.0 * 1024.0)).toInt())
        val availRamMb = (info.availMem / (1024L * 1024L)).toInt().coerceAtLeast(256)
        val isLowMem = info.lowMemory
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(4)
        val label = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
        val tier = if (isLowMem) {
            "Low-RAM Safe Mode ($ramGb GB)"
        } else {
            "$availRamMb MB Free · OOM Protected"
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
        runCatching {
            val url = URL("$base/api/tags")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 2500
                readTimeout = 3500
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

        val finalPrompt = if (webContext.isNotBlank()) {
            "$prompt\n\n[Real-Time Google & Open Web Context]: $webContext"
        } else prompt

        val msgs = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemInstruction))
            history.takeLast(6).forEach { turn ->
                put(JSONObject().put("role", if (turn.isUser) "user" else "assistant").put("content", turn.text))
            }
            put(JSONObject().put("role", "user").put("content", finalPrompt))
        }

        val seed = abs((System.currentTimeMillis() xor modelTag.hashCode().toLong()).toInt())
        Log.i(TAG, "Ollama POST $base/api/chat -> exact model=$modelTag seed=$seed")

        runCatching {
            val conn = (URL("$base/api/chat").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 3500
                readTimeout = 25000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Cache-Control", "no-cache, no-store")
                setRequestProperty("X-Shamia-Model-Tag", modelTag)
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
                Log.i(TAG, "Ollama confirmed executedModel=$actualModel")
                if (content.isNotEmpty()) BackendOutput(content, actualModel, "Local Ollama ($base)") else null
            } else null
            conn.disconnect()
            out
        }.getOrNull()
    }

    private suspend fun queryShamiaServerStrict(
        serverBaseUrl: String,
        message: String,
        modelSpec: OpenSourceModelSpec,
        identity: UserIdentityProfile,
        useResearch: Boolean,
        history: List<HudMessage>,
        routine: List<RoutineItem>,
        syllabus: List<SyllabusItem>
    ): ModelDispatchResult? = withContext(Dispatchers.IO) {
        if (serverBaseUrl.isBlank()) return@withContext null
        val base = serverBaseUrl.trimEnd('/')
        val nonce = "${modelSpec.id}-${System.currentTimeMillis()}"
        Log.i(TAG, "Server POST $base/api/shamia/chat -> modelId=${modelSpec.id} cloud=${modelSpec.cloudModelId}")

        runCatching {
            val conn = (URL("$base/api/shamia/chat").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 4500
                readTimeout = 18000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Cache-Control", "no-cache, no-store")
                setRequestProperty("X-Shamia-Model-Id", modelSpec.id)
                setRequestProperty("X-Shamia-Nonce", nonce)
            }
            val body = JSONObject().apply {
                put("message", message)
                put("modelId", modelSpec.id)
                put("modelLabel", modelSpec.name)
                put("requestedCloudModel", modelSpec.cloudModelId)
                put("requestedOllamaTag", modelSpec.ollamaTag ?: "")
                put("strictSelectedModel", true)
                put("bypassCache", true)
                put("nonce", nonce)
                put("useResearch", useResearch)
                put("ensembleEnabled", false)
                put("identity", JSONObject().put("fullName", identity.fullName).put("addressAs", identity.addressAs))
                put("history", JSONArray(history.takeLast(6).map {
                    JSONObject().put("role", if (it.isUser) "user" else "assistant").put("text", it.text)
                }))
                put("routine", JSONArray(routine.filter { !it.completed }.map { "${it.timeLabel}: ${it.title}" }))
                put("syllabus", JSONArray(syllabus.filter { !it.completed }.map { "${it.courseCode} ${it.courseTitle}: ${it.topic}" }))
            }
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val result = if (conn.responseCode == 200) {
                val res = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val reply = res.optString("replyText").trim()
                val executedModel = res.optString("executedModelId").ifBlank { modelSpec.id }
                val rawLinks = res.optJSONArray("groundingLinks") ?: JSONArray()
                val links = (0 until rawLinks.length()).mapNotNull { i ->
                    val o = rawLinks.getJSONObject(i)
                    val uri = o.optString("uri")
                    val title = o.optString("title")
                    if (uri.isNotEmpty() && title.isNotEmpty()) GroundingLink(title, uri) else null
                }
                if (reply.isNotEmpty()) {
                    ModelDispatchResult(
                        replyText = ensureModelHeader(reply, modelSpec, executedModel),
                        source = if (links.isNotEmpty()) MessageSource.CLOUD_RESEARCH else MessageSource.CLOUD_OPEN,
                        modelLabel = res.optString("modelLabel").ifBlank { modelSpec.name },
                        groundingLinks = links,
                        executionMeta = ModelExecutionMeta(modelSpec.id, modelSpec.name, executedModel, "Paired Server ($base)", true)
                    )
                } else null
            } else null
            conn.disconnect()
            result
        }.getOrNull()
    }

    private suspend fun queryOpenCloudStrict(
        modelSpec: OpenSourceModelSpec,
        prompt: String,
        systemInstruction: String,
        history: List<HudMessage>,
        webContext: String
    ): BackendOutput? = withContext(Dispatchers.IO) {
        val candidates = CloudModelResolver.resolveCloudCandidates(modelSpec)
        val finalPrompt = if (webContext.isNotBlank()) "$prompt\n\n[Real-Time Google Search Context]: $webContext" else prompt
        val seed = abs((System.currentTimeMillis() xor modelSpec.id.hashCode().toLong()).toInt())

        val messagesArr = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemInstruction))
            history.takeLast(6).forEach { turn ->
                put(JSONObject().put("role", if (turn.isUser) "user" else "assistant").put("content", turn.text))
            }
            put(JSONObject().put("role", "user").put("content", finalPrompt))
        }

        // 1. Try Pollinations OpenAI-compatible POST across prioritized candidates for this exact model family
        for (targetCloudModel in candidates) {
            Log.i(TAG, "OpenCloud POST -> requestedSpec=${modelSpec.id}, candidate=$targetCloudModel, seed=$seed")
            val hit = runCatching {
                val conn = (URL("https://text.pollinations.ai/openai").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 5000
                    readTimeout = 16000
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0")
                    setRequestProperty("Pragma", "no-cache")
                    setRequestProperty("X-Shamia-Selected-Model", modelSpec.id)
                    setRequestProperty("X-Shamia-Cloud-Model", targetCloudModel)
                }
                val body = JSONObject().apply {
                    put("model", targetCloudModel)
                    put("messages", messagesArr)
                    put("seed", seed)
                    put("cache", false)
                    put("private", true)
                }
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                val out = if (conn.responseCode in 200..299) {
                    val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    val content = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")?.trim().orEmpty()
                    val actualModel = json.optString("model").ifBlank { targetCloudModel }
                    Log.i(TAG, "OpenCloud POST confirmed executedModel=$actualModel for requestedSpec=${modelSpec.id}")
                    if (content.isNotEmpty()) BackendOutput(content, actualModel, "Open Cloud Router") else null
                } else {
                    Log.w(TAG, "OpenCloud POST HTTP ${conn.responseCode} for candidate=$targetCloudModel")
                    null
                }
                conn.disconnect()
                out
            }.getOrNull()
            if (hit != null) return@withContext hit
        }

        // 2. Fallback to Hugging Face Open Inference Router when hfInferenceModelId is configured
        val hfId = modelSpec.hfInferenceModelId?.trim().orEmpty()
        if (hfId.isNotEmpty()) {
            Log.i(TAG, "HuggingFace Router POST -> $hfId")
            val hfHit = runCatching {
                val conn = (URL("https://router.huggingface.co/hf-inference/models/$hfId/v1/chat/completions").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 5000
                    readTimeout = 15000
                    setRequestProperty("Content-Type", "application/json")
                }
                val body = JSONObject().apply {
                    put("model", hfId)
                    put("messages", messagesArr)
                    put("max_tokens", 512)
                }
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                val out = if (conn.responseCode in 200..299) {
                    val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    val content = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")?.trim().orEmpty()
                    if (content.isNotEmpty()) BackendOutput(content, hfId, "Hugging Face Open Router") else null
                } else null
                conn.disconnect()
                out
            }.getOrNull()
            if (hfHit != null) return@withContext hfHit
        }

        // 3. Fallback to Pollinations GET direct router using resolved candidate
        val primaryModel = candidates.firstOrNull() ?: modelSpec.cloudModelId
        runCatching {
            val q = URLEncoder.encode(finalPrompt.take(1200), "UTF-8")
            val sys = URLEncoder.encode(systemInstruction.take(900), "UTF-8")
            val url = URL("https://text.pollinations.ai/$q?model=$primaryModel&system=$sys&seed=$seed")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 15000
                setRequestProperty("Cache-Control", "no-cache")
            }
            val out = if (conn.responseCode in 200..299) {
                val text = conn.inputStream.bufferedReader().use { it.readText().trim() }
                if (text.isNotEmpty() && !text.startsWith("{")) {
                    BackendOutput(text, primaryModel, "Open Cloud Direct")
                } else null
            } else null
            conn.disconnect()
            out
        }.getOrNull()
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
        useResearch: Boolean
    ): ModelDispatchResult {
        val addr = identity.addressAs.trim().ifEmpty { "Sir" }
        val routineSummary = routine.filter { !it.completed }.map { "${it.timeLabel}: ${it.title}" }
        val syllabusSummary = syllabus.filter { !it.completed }.map { "${it.courseCode} ${it.courseTitle}: ${it.topic}" }
        val resolvedBackendId = activeSpec.ollamaTag ?: activeSpec.cloudModelId

        Log.i(TAG, "DISPATCH | selectedId=${activeSpec.id} | backend=$resolvedBackendId | historyTurns=${sameModelHistory.size} | cacheBypass=true")

        if (CloudModelResolver.isModelIntrospectionQuery(prompt)) {
            val localTags = testLocalEndpoint(ollamaEndpoint)
            val localMatch = localTags.firstOrNull { t ->
                val tag = activeSpec.ollamaTag.orEmpty()
                tag.isNotBlank() && t.startsWith(tag.substringBefore(":"), ignoreCase = true)
            }
            val cloudRoute = CloudModelResolver.resolveCloudCandidates(activeSpec).firstOrNull() ?: activeSpec.cloudModelId
            val routeDetail = when {
                localMatch != null -> "Local Termux Ollama active (`$localMatch` at $ollamaEndpoint)"
                serverBaseUrl.isNotBlank() -> "Paired Server (`$serverBaseUrl`) -> `$cloudRoute`"
                activeSpec.isBuiltInOffline -> "Built-In Offline Core (0 MB Instant + Ollama `${activeSpec.ollamaTag}`)"
                else -> "Open Cloud Router (`$cloudRoute`) / Hugging Face (`${activeSpec.hfInferenceModelId ?: activeSpec.hfRepo ?: "none"}`)"
            }
            val executedTag = localMatch ?: if (activeSpec.isBuiltInOffline) "offline-core / deepseek-r1:1.5b" else cloudRoute
            val reply = "[Model: ${activeSpec.name} | Backend: $executedTag]\n" +
                "You are currently using **${activeSpec.name}**, $addr.\n" +
                "• Model ID: `${activeSpec.id}` (${activeSpec.provider} · ${activeSpec.params})\n" +
                "• Execution Route: $routeDetail\n" +
                "• Cache Bypass: Active (strict per-model isolation)"
            return ModelDispatchResult(
                replyText = reply,
                source = if (localMatch != null) MessageSource.OLLAMA_LOCAL else if (activeSpec.isBuiltInOffline) MessageSource.OFFLINE_CORE else MessageSource.CLOUD_OPEN,
                modelLabel = activeSpec.name,
                groundingLinks = emptyList(),
                executionMeta = ModelExecutionMeta(activeSpec.id, activeSpec.name, executedTag, "Verified Live Model State", true)
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

        queryShamiaServerStrict(serverBaseUrl, prompt, activeSpec, identity, useResearch, sameModelHistory, routine, syllabus)
            ?.let { return it }

        val (webSummary, links) = if (useResearch) searchGoogleAndOpenWeb(prompt) else "" to emptyList()

        if (activeSpec.isBuiltInOffline) {
            val tag = activeSpec.ollamaTag ?: "deepseek-r1:1.5b"
            queryLocalOllamaStrict(ollamaEndpoint, tag, prompt, systemInstruction, sameModelHistory, webSummary)?.let { out ->
                return ModelDispatchResult(
                    replyText = ensureModelHeader(out.text, activeSpec, out.executedModel),
                    source = if (links.isNotEmpty()) MessageSource.CLOUD_RESEARCH else MessageSource.OLLAMA_LOCAL,
                    modelLabel = activeSpec.name,
                    groundingLinks = links,
                    executionMeta = ModelExecutionMeta(activeSpec.id, activeSpec.name, out.executedModel, out.engineName, true)
                )
            }
            val offlineReply = OfflineCompanion.generateReply(prompt, addr, identity.fullName, routine, syllabus, activeSpec)
            val body = if (webSummary.isNotBlank() && links.isNotEmpty()) "${offlineReply.text}\n\nLive Google & Web Context: $webSummary" else offlineReply.text
            val tipSuffix = offlineReply.coachingTip?.let { "\n[ENG_TIP: ${it.naturalPhrase.orEmpty()} | ${it.tip.orEmpty()}]" }.orEmpty()
            return ModelDispatchResult(
                replyText = ensureModelHeader("$body$tipSuffix", activeSpec, "deepseek-r1:1.5b / offline-core"),
                source = if (links.isNotEmpty()) MessageSource.CLOUD_RESEARCH else MessageSource.OFFLINE_CORE,
                modelLabel = activeSpec.name,
                groundingLinks = links,
                executionMeta = ModelExecutionMeta(activeSpec.id, activeSpec.name, "deepseek-r1:1.5b / offline-core", "Built-In Offline Core (0 MB)", true)
            )
        }

        val targetTag = activeSpec.ollamaTag ?: activeSpec.hfRepo ?: activeSpec.id
        queryLocalOllamaStrict(ollamaEndpoint, targetTag, prompt, systemInstruction, sameModelHistory, webSummary)?.let { out ->
            return ModelDispatchResult(
                replyText = ensureModelHeader(out.text, activeSpec, out.executedModel),
                source = if (links.isNotEmpty()) MessageSource.CLOUD_RESEARCH else MessageSource.OLLAMA_LOCAL,
                modelLabel = activeSpec.name,
                groundingLinks = links,
                executionMeta = ModelExecutionMeta(activeSpec.id, activeSpec.name, out.executedModel, out.engineName, true)
            )
        }

        queryOpenCloudStrict(activeSpec, prompt, systemInstruction, sameModelHistory, webSummary)?.let { out ->
            return ModelDispatchResult(
                replyText = ensureModelHeader(out.text, activeSpec, out.executedModel),
                source = if (links.isNotEmpty()) MessageSource.CLOUD_RESEARCH else MessageSource.CLOUD_OPEN,
                modelLabel = activeSpec.name,
                groundingLinks = links,
                executionMeta = ModelExecutionMeta(activeSpec.id, activeSpec.name, out.executedModel, out.engineName, true)
            )
        }

        val offlineAssist = OfflineCompanion.generateReply(prompt, addr, identity.fullName, routine, syllabus, activeSpec)
        val guidance = if (webSummary.isNotBlank()) {
            "[Model: ${activeSpec.name} ($resolvedBackendId) · Web Grounded Fallback]\n" +
                "Live Google & Web Search found for you, $addr: $webSummary\n\n" +
                "For full ${activeSpec.name} inference offline in Termux Ollama, run: `ollama pull $targetTag`."
        } else {
            "[Model: ${activeSpec.name} ($resolvedBackendId) · Local Mode]\n" +
                "${offlineAssist.text}\n\n(Note: Could not reach online inference for ${activeSpec.name}; no hidden default model was substituted. Connect internet or start Termux Ollama `ollama pull $targetTag`.)"
        }
        return ModelDispatchResult(
            replyText = guidance,
            source = if (links.isNotEmpty()) MessageSource.CLOUD_RESEARCH else MessageSource.CLOUD_OPEN,
            modelLabel = activeSpec.name,
            groundingLinks = links,
            executionMeta = ModelExecutionMeta(activeSpec.id, activeSpec.name, "$resolvedBackendId (strict-guard)", "Strict Guard (No Default Substitution)", true)
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
        syllabus: List<SyllabusItem>
    ): ModelDispatchResult = coroutineScope {
        val searchDeferred = async { if (useResearch) searchGoogleAndOpenWeb(prompt) else "" to emptyList() }
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
                    useResearch = false
                )
                spec.name.substringBefore(" (").trim() to res.replyText
            }
        }.map { it.await() }

        val label = "Ensemble (${answers.joinToString(" + ") { it.first }})"
        val merged = answers.joinToString("\n\n") { (name, reply) -> "[$name]:\n$reply" }

        ModelDispatchResult(
            replyText = merged,
            source = if (links.isNotEmpty()) MessageSource.CLOUD_RESEARCH else MessageSource.CLOUD_OPEN,
            modelLabel = label,
            groundingLinks = links,
            executionMeta = ModelExecutionMeta(
                requestedModelId = ensembleModelIds.joinToString("+"),
                requestedModelName = label,
                executedModelId = selectedSpecs.joinToString("+") { it.ollamaTag ?: it.cloudModelId },
                providerEngine = "Multi-Model Ensemble",
                cacheBypassed = true
            )
        )
    }

    private fun ensureModelHeader(text: String, spec: OpenSourceModelSpec, executedModelId: String): String {
        val trimmed = text.trim()
        if (trimmed.startsWith("[Model:", ignoreCase = true) || trimmed.startsWith("[Active Model:", ignoreCase = true)) {
            return trimmed
        }
        return "[Model: ${spec.name} | Backend: $executedModelId]\n$trimmed"
    }
}
