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

    private const val TAG = "ShamiaModelRouter"

    val CATALOG: List<OpenSourceModelSpec> get() = ModelCatalog.CATALOG
    val VOICE_MODELS: List<OfflineVoiceModelSpec> get() = ModelCatalog.VOICE_MODELS
    val AMBIENT_TRACKS: List<AmbientTrack> get() = ModelCatalog.AMBIENT_TRACKS

    fun detectHardware(context: Context): DeviceHardwareSpec {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val ramGb = maxOf(2, Math.round(info.totalMem / (1024.0 * 1024.0 * 1024.0)).toInt())
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(4)
        val label = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
        val tier = when {
            ramGb >= 8 -> "4nm Flagship Ready ($ramGb GB RAM)"
            ramGb >= 4 -> "4nm / 6nm Mid-Tier Ready ($ramGb GB RAM)"
            else -> "6nm+ Ultra-Light Ready ($ramGb GB RAM)"
        }
        return DeviceHardwareSpec(ramGb = ramGb, cpuCores = cores, deviceLabel = label, recommendedTier = tier)
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

    private data class BackendOutput(val text: String, val executedModel: String)

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
                if (content.isNotEmpty()) BackendOutput(content, actualModel) else null
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
        val targetCloudModel = modelSpec.cloudModelId.trim().ifEmpty { modelSpec.ollamaTag ?: modelSpec.id }
        val finalPrompt = if (webContext.isNotBlank()) "$prompt\n\n[Real-Time Google Search Context]: $webContext" else prompt
        val seed = abs((System.currentTimeMillis() xor modelSpec.id.hashCode().toLong()).toInt())

        val messagesArr = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemInstruction))
            history.takeLast(6).forEach { turn ->
                put(JSONObject().put("role", if (turn.isUser) "user" else "assistant").put("content", turn.text))
            }
            put(JSONObject().put("role", "user").put("content", finalPrompt))
        }

        Log.i(TAG, "OpenCloud POST -> requestedSpec=${modelSpec.id}, payloadModel=$targetCloudModel, seed=$seed")

        runCatching {
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
                Log.i(TAG, "OpenCloud confirmed executedModel=$actualModel for requestedSpec=${modelSpec.id}")
                if (content.isNotEmpty()) BackendOutput(content, actualModel) else null
            } else {
                Log.w(TAG, "OpenCloud HTTP ${conn.responseCode} for model=$targetCloudModel")
                null
            }
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
                    executionMeta = ModelExecutionMeta(activeSpec.id, activeSpec.name, out.executedModel, "Local Ollama ($ollamaEndpoint)", true)
                )
            }
            val offlineReply = OfflineCompanion.generateReply(prompt, addr, identity.fullName, routine, syllabus)
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
                executionMeta = ModelExecutionMeta(activeSpec.id, activeSpec.name, out.executedModel, "Local Ollama ($ollamaEndpoint)", true)
            )
        }

        queryOpenCloudStrict(activeSpec, prompt, systemInstruction, sameModelHistory, webSummary)?.let { out ->
            return ModelDispatchResult(
                replyText = ensureModelHeader(out.text, activeSpec, out.executedModel),
                source = if (links.isNotEmpty()) MessageSource.CLOUD_RESEARCH else MessageSource.CLOUD_OPEN,
                modelLabel = activeSpec.name,
                groundingLinks = links,
                executionMeta = ModelExecutionMeta(activeSpec.id, activeSpec.name, out.executedModel, "Open Cloud Router", true)
            )
        }

        val guidance = if (webSummary.isNotBlank()) {
            "[Active Model: ${activeSpec.name} ($resolvedBackendId) · Cloud Inference Offline]\n" +
                "Live Google & Web Search found for you, $addr: $webSummary\n\n" +
                "To run ${activeSpec.name} locally in Termux Ollama, pull: `ollama pull $targetTag`."
        } else {
            "[Active Model: ${activeSpec.name} ($resolvedBackendId) · Runtime Unreachable Offline]\n" +
                "Strict Model Mode prevented falling back to another model, $addr. Start Termux Ollama (`ollama pull $targetTag`), connect online, or select DeepSeek 2B · Offline Core."
        }
        return ModelDispatchResult(
            replyText = guidance,
            source = if (links.isNotEmpty()) MessageSource.CLOUD_RESEARCH else MessageSource.OFFLINE_CORE,
            modelLabel = activeSpec.name,
            groundingLinks = links,
            executionMeta = ModelExecutionMeta(activeSpec.id, activeSpec.name, "$resolvedBackendId (unreachable)", "Strict Guard (No Default Fallback)", true)
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
