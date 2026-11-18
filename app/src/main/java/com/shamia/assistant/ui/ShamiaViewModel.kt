package com.shamia.assistant.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shamia.assistant.engine.DeviceExecutor
import com.shamia.assistant.engine.DeviceTelemetryMonitor
import com.shamia.assistant.engine.IntentEngine
import com.shamia.assistant.engine.ModelCatalog
import com.shamia.assistant.engine.OfflineCompanion
import com.shamia.assistant.engine.OpenSourceAiRepository
import com.shamia.assistant.engine.RoutineSyllabusStore
import com.shamia.assistant.engine.ShamiaPrompts
import com.shamia.assistant.engine.VoiceAssistantEngine
import com.shamia.assistant.model.ActionStatus
import com.shamia.assistant.model.AlarmItem
import com.shamia.assistant.model.ConversationalIntent
import com.shamia.assistant.model.CoreVisualState
import com.shamia.assistant.model.DeviceActionType
import com.shamia.assistant.model.HudMessage
import com.shamia.assistant.model.MessageSource
import com.shamia.assistant.model.NoteItem
import com.shamia.assistant.model.OpenSourceModelSpec
import com.shamia.assistant.model.PendingAction
import com.shamia.assistant.model.PermissionCapability
import com.shamia.assistant.model.PermissionPolicy
import com.shamia.assistant.model.RoutineCategory
import com.shamia.assistant.model.RoutineItem
import com.shamia.assistant.model.SyllabusItem
import com.shamia.assistant.model.TaskItem
import com.shamia.assistant.model.UserIdentityProfile
import com.shamia.assistant.model.VoiceGender
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ShamiaViewModel(application: Application) : AndroidViewModel(application) {
    private val store = RoutineSyllabusStore(application)
    private val telemetryMonitor = DeviceTelemetryMonitor(application)
    val voiceEngine = VoiceAssistantEngine(application)

    private val _ui = MutableStateFlow(ShamiaUiState())
    val ui: StateFlow<ShamiaUiState> = _ui.asStateFlow()

    private var generationJob: Job? = null

    init {
        val identity = store.loadIdentity()
        val hardware = OpenSourceAiRepository.detectHardware(application)
        val controls = store.loadControls()
        val selectedModelId = store.loadSelectedModelId()
        val customModels = store.loadCustomModels()
        val activeSpec = (ModelCatalog.CATALOG + customModels).find { it.id == selectedModelId }
            ?: ModelCatalog.CATALOG.first()
        val initialSession = "session-${System.currentTimeMillis()}"

        val initialGender = VoiceGender.entries.firstOrNull { it.name == store.loadVoiceGender() } ?: VoiceGender.FEMALE

        _ui.update {
            it.copy(
                chatSessionId = initialSession,
                identity = identity,
                hardware = hardware,
                clockLabel = clockFormat(),
                routine = store.loadRoutine(),
                syllabus = store.loadSyllabus(),
                tasks = store.loadTasks(),
                notes = store.loadNotes(),
                controls = controls,
                permissions = store.loadPermissions(),
                customModels = customModels,
                installedModelIds = store.loadInstalledModelIds(),
                installedVoiceIds = store.loadInstalledVoiceIds(),
                selectedModelId = activeSpec.id,
                voiceGender = initialGender,
                showAllModels = store.loadShowAllModels(),
                ensembleEnabled = store.loadEnsembleEnabled(),
                ensembleModelIds = store.loadEnsembleIds(),
                useResearch = store.loadUseResearch(),
                ollamaEndpoint = store.loadOllamaEndpoint(),
                serverBaseUrl = store.loadServerBaseUrl(),
                messages = listOf(buildWelcomeMessage(identity, activeSpec, initialSession))
            )
        }

        voiceEngine.initTts(initialGender) { isSpeaking ->
            _ui.update { state ->
                state.copy(
                    coreState = if (isSpeaking) CoreVisualState.SPEAKING
                    else if (state.coreState == CoreVisualState.SPEAKING) CoreVisualState.IDLE
                    else state.coreState
                )
            }
        }

        viewModelScope.launch {
            telemetryMonitor.telemetry.collect { snap -> _ui.update { it.copy(telemetry = snap) } }
        }

        viewModelScope.launch {
            while (true) {
                delay(1000L)
                _ui.update { it.copy(nowMillis = System.currentTimeMillis(), clockLabel = clockFormat()) }
            }
        }
    }

    private fun buildWelcomeMessage(
        identity: UserIdentityProfile,
        activeSpec: OpenSourceModelSpec,
        sessionId: String
    ): HudMessage {
        val backendTag = activeSpec.ollamaTag ?: activeSpec.cloudModelId
        return HudMessage(
            id = "welcome-$sessionId",
            isUser = false,
            sessionId = sessionId,
            modelIdForTurn = activeSpec.id,
            text = "Good day, ${identity.addressAs}. S.H.A.M.I.A. is ready for ${identity.fullName}. Currently locked to ${activeSpec.name} ($backendTag) with strict cache bypass enabled.",
            timestampLabel = nowLabel(),
            source = if (activeSpec.isBuiltInOffline) MessageSource.OFFLINE_CORE else MessageSource.CLOUD_OPEN,
            modelLabel = activeSpec.name
        )
    }

    private fun nowLabel(): String = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
    private fun clockFormat(): String = SimpleDateFormat("EEE, MMM d · hh:mm:ss a", Locale.getDefault()).format(Date())
    private fun appendMessage(msg: HudMessage) = _ui.update { it.copy(messages = it.messages + msg) }

    fun clearBanner() = _ui.update { it.copy(statusBanner = null) }

    fun startNewChat() {
        startNewChatWithModel(_ui.value.selectedModelId)
    }

    fun startNewChatWithModel(modelId: String) {
        generationJob?.cancel()
        generationJob = null
        voiceEngine.stopSpeaking()
        val chosen = _ui.value.allModels.find { it.id == modelId } ?: _ui.value.selectedModel
        store.saveSelectedModelId(chosen.id)
        val freshSession = "session-${System.currentTimeMillis()}"
        Log.i(
            "ShamiaModelRouter",
            "NEW CHAT WITH MODEL | session=$freshSession | id=${chosen.id} | ollama=${chosen.ollamaTag} | cloud=${chosen.cloudModelId}"
        )
        _ui.update {
            it.copy(
                chatSessionId = freshSession,
                selectedModelId = chosen.id,
                coreState = CoreVisualState.IDLE,
                liveTranscript = "",
                messages = listOf(buildWelcomeMessage(it.identity, chosen, freshSession)),
                statusBanner = "New chat started with ${chosen.name} (${chosen.ollamaTag ?: chosen.cloudModelId})."
            )
        }
    }

    fun selectModel(modelId: String) {
        val chosen = _ui.value.allModels.find { it.id == modelId } ?: return
        store.saveSelectedModelId(chosen.id)
        Log.i("ShamiaModelRouter", "MODEL SWITCH -> id=${chosen.id}, ollama=${chosen.ollamaTag}, cloud=${chosen.cloudModelId}")
        _ui.update {
            it.copy(
                selectedModelId = chosen.id,
                statusBanner = "Active model: ${chosen.name} (${chosen.ollamaTag ?: chosen.cloudModelId})."
            )
        }
    }

    fun sendMessage(rawText: String) {
        val text = rawText.trim()
        if (text.isEmpty()) return
        val state = _ui.value
        val addr = state.identity.addressAs.ifBlank { "Sir" }
        val activeSpec = state.selectedModel
        val currentSession = state.chatSessionId

        appendMessage(
            HudMessage(
                id = "u-${System.currentTimeMillis()}",
                isUser = true,
                text = text,
                timestampLabel = nowLabel(),
                sessionId = currentSession,
                modelIdForTurn = activeSpec.id
            )
        )

        val local = IntentEngine.matchLocalIntent(
            input = text,
            addressAs = addr,
            controls = state.controls,
            telemetry = state.telemetry,
            tasks = state.tasks,
            alarms = state.alarms,
            routine = state.routine,
            syllabus = state.syllabus
        )
        if (local.handled) {
            handleMemoryMutation(local.memoryMutation)
            val action = local.proposedAction
            if (action != null) {
                stageOrExecuteAction(action, local.replyText ?: "Ready, $addr.")
            } else {
                val reply = local.replyText ?: "Done, $addr."
                appendMessage(
                    HudMessage(
                        id = "a-${System.currentTimeMillis()}",
                        isUser = false,
                        text = reply,
                        timestampLabel = nowLabel(),
                        sessionId = currentSession,
                        modelIdForTurn = activeSpec.id,
                        intent = local.intent,
                        source = MessageSource.OFFLINE_CORE,
                        modelLabel = "Local Device Engine"
                    )
                )
                if (_ui.value.voiceReplyEnabled) voiceEngine.speak(reply)
            }
            return
        }

        val intent = IntentEngine.detectConversationalIntent(text)
        val shouldSearch = IntentEngine.shouldTriggerLiveSearch(text, state.useResearch)
        val isolatedHistory = _ui.value.messages
            .filter { it.sessionId == currentSession && it.modelIdForTurn == activeSpec.id && !it.id.startsWith("welcome-") }
            .dropLast(1)

        generationJob?.cancel()
        _ui.update { it.copy(coreState = CoreVisualState.THINKING) }

        generationJob = viewModelScope.launch {
            val activeTasks = _ui.value.tasks.filter { !it.completed }.map { it.title }
            val result = if (_ui.value.ensembleEnabled) {
                OpenSourceAiRepository.runEnsembleLocalAndWeb(
                    prompt = text,
                    identity = _ui.value.identity,
                    detectedIntent = intent,
                    permissions = _ui.value.permissions,
                    tasksSummary = activeTasks,
                    ollamaEndpoint = _ui.value.ollamaEndpoint,
                    serverBaseUrl = _ui.value.serverBaseUrl,
                    allModels = _ui.value.allModels,
                    ensembleModelIds = _ui.value.ensembleModelIds,
                    useResearch = shouldSearch,
                    routine = _ui.value.routine,
                    syllabus = _ui.value.syllabus
                )
            } else {
                OpenSourceAiRepository.dispatchStrictModelRequest(
                    prompt = text,
                    activeSpec = activeSpec,
                    identity = _ui.value.identity,
                    detectedIntent = intent,
                    permissions = _ui.value.permissions,
                    tasksSummary = activeTasks,
                    routine = _ui.value.routine,
                    syllabus = _ui.value.syllabus,
                    sameModelHistory = isolatedHistory,
                    ollamaEndpoint = _ui.value.ollamaEndpoint,
                    serverBaseUrl = _ui.value.serverBaseUrl,
                    useResearch = shouldSearch
                )
            }

            val (cleanText, extractedTip) = ShamiaPrompts.extractCoachingTip(result.replyText)
            val fallbackTip = if (extractedTip == null && intent == ConversationalIntent.ENGLISH_TUTOR) {
                OfflineCompanion.generateReply(text, addr, _ui.value.identity.fullName, _ui.value.routine, _ui.value.syllabus).coachingTip
            } else extractedTip

            _ui.update { it.copy(coreState = CoreVisualState.IDLE) }
            appendMessage(
                HudMessage(
                    id = "a-${System.currentTimeMillis()}",
                    isUser = false,
                    text = cleanText,
                    timestampLabel = nowLabel(),
                    sessionId = currentSession,
                    modelIdForTurn = activeSpec.id,
                    intent = intent,
                    source = result.source,
                    modelLabel = result.modelLabel,
                    executionMeta = result.executionMeta,
                    coachingTip = fallbackTip,
                    showBreathingGuide = intent == ConversationalIntent.MENTAL_SUPPORT,
                    groundingLinks = result.groundingLinks
                )
            )
            if (_ui.value.voiceReplyEnabled) voiceEngine.speak(cleanText)
        }
    }

    private fun handleMemoryMutation(mutation: IntentEngine.MemoryMutation?) {
        if (IntentEngine.evaluatePermission(_ui.value.permissions, PermissionCapability.MANAGE_MEMORY) == PermissionPolicy.DENY || mutation == null) return
        when (mutation) {
            is IntentEngine.MemoryMutation.AddTask -> addTask(mutation.title, mutation.highPriority)
            IntentEngine.MemoryMutation.ClearCompletedTasks -> clearCompletedTasks()
            is IntentEngine.MemoryMutation.AddNote -> addNote(mutation.content)
        }
    }

    fun stageOrExecuteAction(action: PendingAction, promptText: String) {
        val policy = IntentEngine.evaluatePermission(_ui.value.permissions, action.capability)
        val addr = _ui.value.identity.addressAs.ifBlank { "Sir" }
        val sessionId = _ui.value.chatSessionId

        when (policy) {
            PermissionPolicy.DENY -> appendMessage(
                HudMessage("a-${System.currentTimeMillis()}", false, "Blocked by Permission Vault (${action.capability.label} is Deny), $addr.", nowLabel(), sessionId, source = MessageSource.PERMISSION_GATE, pendingAction = action.copy(status = ActionStatus.BLOCKED))
            )
            PermissionPolicy.ALWAYS -> {
                val execMsg = runApprovedAction(action)
                appendMessage(HudMessage("a-${System.currentTimeMillis()}", false, execMsg, nowLabel(), sessionId, source = MessageSource.PERMISSION_GATE, pendingAction = action.copy(status = ActionStatus.APPROVED)))
                if (_ui.value.voiceReplyEnabled) voiceEngine.speak(execMsg)
            }
            PermissionPolicy.ASK -> {
                _ui.update { it.copy(coreState = CoreVisualState.AWAITING_PERMISSION) }
                appendMessage(HudMessage("a-${System.currentTimeMillis()}", false, promptText, nowLabel(), sessionId, source = MessageSource.PERMISSION_GATE, pendingAction = action.copy(status = ActionStatus.PENDING)))
            }
        }
    }

    private fun runApprovedAction(action: PendingAction): String {
        if (action.type == DeviceActionType.PLAY_MUSIC) {
            val idx = ModelCatalog.AMBIENT_TRACKS.indexOfFirst {
                it.mood.equals(action.targetValue, true) || it.title.contains(action.targetValue, true)
            }.takeIf { it >= 0 } ?: 0
            startTrackByIndex(idx)
        }
        if (action.type == DeviceActionType.SET_ALARM) scheduleCountdownAlarm(action.numericValue.coerceAtLeast(1), action.title)
        return DeviceExecutor.execute(
            context = getApplication(),
            action = action,
            currentControls = _ui.value.controls,
            onControlsUpdated = { updated -> store.saveControls(updated); _ui.update { it.copy(controls = updated) } },
            onBrightnessRequested = { pct -> _ui.update { it.copy(windowBrightnessPct = pct) } }
        )
    }

    fun approveActionOnce(messageId: String, phoneOverride: String?) = resolvePendingAction(messageId, phoneOverride, false)
    fun alwaysAllowAction(messageId: String, phoneOverride: String?) = resolvePendingAction(messageId, phoneOverride, true)

    private fun resolvePendingAction(messageId: String, phoneOverride: String?, setAlways: Boolean) {
        val msg = _ui.value.messages.find { it.id == messageId } ?: return
        val baseAction = msg.pendingAction ?: return
        val resolvedTarget = if (baseAction.type == DeviceActionType.DIAL_CONTACT && !phoneOverride.isNullOrBlank()) {
            IntentEngine.validatePhoneNumber(phoneOverride) ?: phoneOverride
        } else baseAction.targetValue
        val finalAction = baseAction.copy(targetValue = resolvedTarget)
        if (setAlways) setPermissionPolicy(finalAction.capability, PermissionPolicy.ALWAYS)
        val execMsg = runApprovedAction(finalAction)
        _ui.update { state ->
            state.copy(
                coreState = CoreVisualState.IDLE,
                statusBanner = execMsg,
                messages = state.messages.map { if (it.id == messageId) it.copy(pendingAction = finalAction.copy(status = ActionStatus.APPROVED)) else it }
            )
        }
        if (_ui.value.voiceReplyEnabled) voiceEngine.speak(execMsg)
    }

    fun denyAction(messageId: String) {
        val msg = _ui.value.messages.find { it.id == messageId } ?: return
        val action = msg.pendingAction ?: return
        val addr = _ui.value.identity.addressAs.ifBlank { "Sir" }
        _ui.update { state ->
            state.copy(
                coreState = CoreVisualState.IDLE,
                statusBanner = "Action cancelled cleanly, $addr.",
                messages = state.messages.map { if (it.id == messageId) it.copy(pendingAction = action.copy(status = ActionStatus.DENIED)) else it }
            )
        }
    }

    fun scheduleCountdownAlarm(minutes: Int, label: String = "S.H.A.M.I.A. Focus Timer") {
        val id = "alm-${System.currentTimeMillis()}"
        val triggerAt = System.currentTimeMillis() + minutes * 60_000L
        _ui.update { it.copy(alarms = listOf(AlarmItem(id, label, minutes, triggerAt, true)) + it.alarms, statusBanner = "Alarm set for $minutes minute(s).") }
        viewModelScope.launch {
            delay(minutes * 60_000L)
            val current = _ui.value.alarms.find { it.id == id }
            if (current != null && current.active) {
                voiceEngine.playAlarmChime()
                appendMessage(HudMessage("alm-fire-${System.currentTimeMillis()}", false, "Alarm chime complete ($label), ${_ui.value.identity.addressAs}.", nowLabel(), _ui.value.chatSessionId))
                _ui.update { state -> state.copy(alarms = state.alarms.map { if (it.id == id) it.copy(active = false) else it }) }
            }
        }
    }

    fun dismissAlarm(id: String) = _ui.update { s -> s.copy(alarms = s.alarms.filterNot { it.id == id }) }

    private fun startTrackByIndex(index: Int) {
        val safeIdx = index.coerceIn(0, ModelCatalog.AMBIENT_TRACKS.lastIndex)
        val track = ModelCatalog.AMBIENT_TRACKS[safeIdx]
        voiceEngine.setAmbientVolume(_ui.value.musicVolume)
        voiceEngine.startAmbientChord(track.chordFreqs, track.bpm)
        _ui.update { it.copy(musicActive = true, musicPlaying = true, activeTrackIndex = safeIdx, statusBanner = "Playing ${track.title}") }
    }

    fun playAmbientTrack(trackId: String) {
        val idx = ModelCatalog.AMBIENT_TRACKS.indexOfFirst { it.id == trackId }
        if (idx >= 0) { if (_ui.value.activeTrackId == trackId) stopMusic() else startTrackByIndex(idx) }
    }

    fun toggleMusicPlayPause() {
        val s = _ui.value
        if (!s.musicActive) startTrackByIndex(s.activeTrackIndex)
        else if (s.musicPlaying) { voiceEngine.stopAmbientTone(); _ui.update { it.copy(musicPlaying = false) } }
        else { voiceEngine.startAmbientChord(s.activeTrack.chordFreqs, s.activeTrack.bpm); _ui.update { it.copy(musicPlaying = true) } }
    }

    fun nextAmbientTrack() = startTrackByIndex((_ui.value.activeTrackIndex + 1) % ModelCatalog.AMBIENT_TRACKS.size)
    fun stopMusic() { voiceEngine.stopAmbientTone(); _ui.update { it.copy(musicActive = false, musicPlaying = false) } }
    fun setMusicVolume(v: Float) { val vol = v.coerceIn(0.05f, 0.55f); voiceEngine.setAmbientVolume(vol); _ui.update { it.copy(musicVolume = vol) } }

    fun toggleVoiceListening() {
        if (IntentEngine.evaluatePermission(_ui.value.permissions, PermissionCapability.MICROPHONE) == PermissionPolicy.DENY) {
            _ui.update { it.copy(statusBanner = "Microphone blocked in Permission Vault.") }; return
        }
        if (_ui.value.coreState == CoreVisualState.LISTENING) {
            voiceEngine.stopListening(); _ui.update { it.copy(coreState = CoreVisualState.IDLE, liveTranscript = "") }; return
        }
        _ui.update { it.copy(coreState = CoreVisualState.LISTENING, liveTranscript = "Listening...") }
        voiceEngine.startListening(
            onPartial = { p -> _ui.update { it.copy(liveTranscript = p) } },
            onFinal = { f -> _ui.update { it.copy(coreState = CoreVisualState.IDLE, liveTranscript = "") }; sendMessage(f) },
            onError = { e -> _ui.update { it.copy(coreState = CoreVisualState.IDLE, liveTranscript = "", statusBanner = e) } }
        )
    }

    fun toggleVoiceReply() = _ui.update { val next = !it.voiceReplyEnabled; if (!next) voiceEngine.stopSpeaking(); it.copy(voiceReplyEnabled = next) }
    fun setVoiceGender(gender: VoiceGender) {
        store.saveVoiceGender(gender.name)
        voiceEngine.setVoiceGender(gender)
        _ui.update { it.copy(voiceGender = gender, statusBanner = "Clear Natural Voice set to ${gender.label}.") }
    }
    fun toggleShowAllModels() {
        val next = !_ui.value.showAllModels
        store.saveShowAllModels(next)
        _ui.update { it.copy(showAllModels = next) }
    }
    fun toggleWebResearch() = _ui.update { val next = !it.useResearch; store.saveUseResearch(next); it.copy(useResearch = next) }
    fun toggleEnsembleEnabled() = _ui.update { val next = !it.ensembleEnabled; store.saveEnsembleEnabled(next); it.copy(ensembleEnabled = next) }

    fun toggleInstallModel(modelId: String) {
        if (modelId == "offline-core") return
        val next = _ui.value.installedModelIds.toMutableSet().apply { if (contains(modelId)) remove(modelId) else add(modelId) }
        store.saveInstalledModelIds(next); _ui.update { it.copy(installedModelIds = next) }
    }

    fun toggleInstallVoiceModel(voiceId: String) {
        val next = _ui.value.installedVoiceIds.toMutableSet().apply { if (contains(voiceId)) remove(voiceId) else add(voiceId) }
        store.saveInstalledVoiceIds(next); _ui.update { it.copy(installedVoiceIds = next) }
    }

    fun addCustomModel(name: String, hfOrOllama: String, minRamGb: Int) {
        val cleanName = name.trim(); val cleanRepo = hfOrOllama.trim()
        if (cleanName.isEmpty() || cleanRepo.isEmpty()) return
        val tag = if (!cleanRepo.contains("/")) cleanRepo else cleanRepo.substringAfterLast("/")
        val spec = OpenSourceModelSpec(
            id = "custom-${System.currentTimeMillis()}", name = cleanName, provider = "Custom Open Source",
            params = "Custom", sizeLabel = "User Added", minRamGb = minRamGb.coerceIn(1, 32), chipsetNote = "Custom HF / Ollama",
            hfRepo = if (cleanRepo.contains("/")) cleanRepo else null, ollamaTag = tag, cloudModelId = tag,
            hfInferenceModelId = if (cleanRepo.contains("/")) cleanRepo else null, summary = "Loaded dynamically ($cleanRepo).", isCustom = true
        )
        val updated = _ui.value.customModels + spec; val installed = _ui.value.installedModelIds + spec.id
        store.saveCustomModels(updated); store.saveInstalledModelIds(installed); store.saveSelectedModelId(spec.id)
        _ui.update { it.copy(customModels = updated, installedModelIds = installed, selectedModelId = spec.id, statusBanner = "Custom model $cleanName active.") }
    }

    fun toggleEnsembleMember(modelId: String) {
        val cur = _ui.value.ensembleModelIds.toMutableList()
        if (cur.contains(modelId)) { if (cur.size > 1) cur.remove(modelId) }
        else { if (cur.size >= 3) cur.removeAt(0); cur.add(modelId) }
        store.saveEnsembleIds(cur); _ui.update { it.copy(ensembleModelIds = cur) }
    }

    fun updateEndpointsAndTest(ollamaUrl: String, serverUrl: String) {
        val cleanO = ollamaUrl.trim(); val cleanS = serverUrl.trim()
        store.saveOllamaEndpoint(cleanO); store.saveServerBaseUrl(cleanS)
        _ui.update { it.copy(ollamaEndpoint = cleanO, serverBaseUrl = cleanS, ollamaTestStatus = "Testing $cleanO...") }
        viewModelScope.launch {
            val tags = OpenSourceAiRepository.testLocalEndpoint(cleanO)
            val msg = if (tags.isNotEmpty()) "Connected! Models: ${tags.take(6).joinToString(", ")}" else "No Ollama responding at $cleanO."
            _ui.update { it.copy(ollamaTestStatus = msg) }
        }
    }

    fun updateIdentity(fullName: String, addressAs: String) {
        val updated = UserIdentityProfile(fullName.trim().ifEmpty { "Md Shabbir Mia" }, addressAs.trim().ifEmpty { "Sir" })
        store.saveIdentity(updated); _ui.update { it.copy(identity = updated) }
    }

    fun setPermissionPolicy(cap: PermissionCapability, policy: PermissionPolicy) {
        store.savePermission(cap, policy); _ui.update { it.copy(permissions = it.permissions + (cap to policy)) }
    }

    fun addTask(title: String, highPriority: Boolean = false) {
        if (title.isBlank()) return
        val next = listOf(TaskItem("t-${System.currentTimeMillis()}", title.trim(), highPriority)) + _ui.value.tasks
        store.saveTasks(next); _ui.update { it.copy(tasks = next) }
    }

    fun toggleTask(id: String) = _ui.update { val n = it.tasks.map { t -> if (t.id == id) t.copy(completed = !t.completed) else t }; store.saveTasks(n); it.copy(tasks = n) }
    fun deleteTask(id: String) = _ui.update { val n = it.tasks.filterNot { t -> t.id == id }; store.saveTasks(n); it.copy(tasks = n) }
    fun clearCompletedTasks() = _ui.update { val n = it.tasks.filterNot { t -> t.completed }; store.saveTasks(n); it.copy(tasks = n) }

    fun addNote(content: String) {
        if (content.isBlank()) return
        val next = listOf(NoteItem("n-${System.currentTimeMillis()}", content.trim())) + _ui.value.notes
        store.saveNotes(next); _ui.update { it.copy(notes = next) }
    }

    fun deleteNote(id: String) = _ui.update { val n = it.notes.filterNot { m -> m.id == id }; store.saveNotes(n); it.copy(notes = n) }

    fun addRoutine(timeLabel: String, title: String, category: RoutineCategory = RoutineCategory.STUDY) {
        if (title.isBlank()) return
        val next = _ui.value.routine + RoutineItem("r-${System.currentTimeMillis()}", timeLabel.ifBlank { "Anytime" }, title.trim(), category)
        store.saveRoutine(next); _ui.update { it.copy(routine = next) }
    }

    fun toggleRoutine(id: String) = _ui.update { val n = it.routine.map { r -> if (r.id == id) r.copy(completed = !r.completed) else r }; store.saveRoutine(n); it.copy(routine = n) }
    fun deleteRoutine(id: String) = _ui.update { val n = it.routine.filterNot { r -> r.id == id }; store.saveRoutine(n); it.copy(routine = n) }

    fun addSyllabus(courseCode: String, courseTitle: String, topic: String, examNote: String = "") {
        if (topic.isBlank()) return
        val next = _ui.value.syllabus + SyllabusItem("s-${System.currentTimeMillis()}", courseCode.trim().ifBlank { "COURSE" }, courseTitle.trim().ifBlank { "Subject" }, topic.trim(), examNote.trim().takeIf { it.isNotEmpty() })
        store.saveSyllabus(next); _ui.update { it.copy(syllabus = next) }
    }

    fun toggleSyllabus(id: String) = _ui.update { val n = it.syllabus.map { s -> if (s.id == id) s.copy(completed = !s.completed) else s }; store.saveSyllabus(n); it.copy(syllabus = n) }
    fun deleteSyllabus(id: String) = _ui.update { val n = it.syllabus.filterNot { s -> s.id == id }; store.saveSyllabus(n); it.copy(syllabus = n) }

    fun requestStudyPlanFromRoutineAndSyllabus() {
        val rStr = _ui.value.routine.filter { !it.completed }.joinToString("; ") { "${it.timeLabel} (${it.title})" }.ifBlank { "General Study Blocks" }
        val sStr = _ui.value.syllabus.filter { !it.completed }.joinToString("; ") { "${it.courseCode} ${it.courseTitle}: ${it.topic}" }.ifBlank { "Core Subjects" }
        sendMessage("Build a practical step-by-step study plan based on my daily routine [$rStr] and my university syllabus [$sStr].")
    }

    override fun onCleared() {
        telemetryMonitor.release()
        voiceEngine.release()
        super.onCleared()
    }
}
