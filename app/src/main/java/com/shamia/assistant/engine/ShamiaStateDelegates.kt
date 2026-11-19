package com.shamia.assistant.engine

import android.app.Application
import android.net.Uri
import com.shamia.assistant.model.NoteItem
import com.shamia.assistant.model.RoutineCategory
import com.shamia.assistant.model.RoutineItem
import com.shamia.assistant.model.SyllabusItem
import com.shamia.assistant.model.TaskItem
import com.shamia.assistant.ui.ShamiaUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

internal object ShamiaStateDelegates {

    private val activeDownloadJobs = ConcurrentHashMap<String, Job>()

    fun preloadModelIntoRam(
        app: Application,
        scope: CoroutineScope,
        ui: MutableStateFlow<ShamiaUiState>,
        modelId: String
    ) {
        if (modelId == "offline-core") return
        if (!OnDeviceLlmEngine.hasRealLocalWeights(app, modelId)) return
        if (OnDeviceLlmEngine.currentLoadedModelId() == modelId) {
            ui.update {
                it.copy(
                    loadedInRamModelId = modelId,
                    loadingInRamModelId = null,
                    loadedBackendLabel = OnDeviceLlmEngine.currentLoadedBackend()
                )
            }
            return
        }
        ui.update { it.copy(loadingInRamModelId = modelId) }
        scope.launch {
            val (ok, backend) = OnDeviceLlmEngine.preloadModel(app, modelId)
            ui.update { cur ->
                cur.copy(
                    loadedInRamModelId = if (ok) modelId else cur.loadedInRamModelId,
                    loadingInRamModelId = if (cur.loadingInRamModelId == modelId) null else cur.loadingInRamModelId,
                    loadedBackendLabel = if (ok) backend else cur.loadedBackendLabel
                )
            }
        }
    }

    fun pauseModelDownload(
        app: Application,
        ui: MutableStateFlow<ShamiaUiState>,
        modelId: String
    ) {
        activeDownloadJobs.remove(modelId)?.cancel()
        val partialPct = ModelDownloadManager.getPartialDownloadPercent(app, modelId)
        ui.update { cur ->
            val nextPartials = if (partialPct != null) {
                cur.partialDownloadPercents + (modelId to partialPct)
            } else {
                cur.partialDownloadPercents
            }
            cur.copy(
                modelDownloadProgress = cur.modelDownloadProgress - modelId,
                partialDownloadPercents = nextPartials
            )
        }
    }

    fun installOrRemoveModel(
        app: Application,
        scope: CoroutineScope,
        ui: MutableStateFlow<ShamiaUiState>,
        store: RoutineSyllabusStore,
        modelId: String,
        silent: Boolean = false
    ) {
        val state = ui.value
        val spec = state.allModels.find { it.id == modelId } ?: return
        if (state.installedModelIds.contains(modelId) && !spec.isBuiltInOffline) {
            if (silent) return
            activeDownloadJobs.remove(modelId)?.cancel()
            scope.launch { OnDeviceLlmEngine.release() }
            ModelDownloadManager.removeOfflineCache(app, modelId)
            val next = state.installedModelIds - modelId
            store.saveInstalledModelIds(next)
            ui.update {
                it.copy(
                    installedModelIds = next,
                    loadedInRamModelId = if (it.loadedInRamModelId == modelId) null else it.loadedInRamModelId,
                    partialDownloadPercents = it.partialDownloadPercents - modelId,
                    statusBanner = "Removed local weights for ${spec.name}."
                )
            }
            return
        }
        if (!ModelDownloadManager.isSafeForLocalInstall(spec, state.hardware)) {
            if (!silent) {
                ui.update {
                    it.copy(statusBanner = "OOM Protection: ${spec.name} requires ${spec.minRamGb} GB RAM (Device: ${state.hardware.ramGb} GB).")
                }
            }
            return
        }
        if (state.modelDownloadProgress.containsKey(modelId)) return

        val job = scope.launch {
            try {
                val (ok, detailMessage) = ModelDownloadManager.installModelOffline(app, spec, ui.value.ollamaEndpoint) { pct, statusText ->
                    ui.update { cur -> cur.copy(modelDownloadProgress = cur.modelDownloadProgress + (modelId to (pct to statusText))) }
                }
                val partialPct = if (!ok) ModelDownloadManager.getPartialDownloadPercent(app, modelId) else null
                ui.update { cur ->
                    val nextIds = if (ok) cur.installedModelIds + modelId else cur.installedModelIds
                    if (ok) {
                        store.saveInstalledModelIds(nextIds)
                        if (!silent) store.saveSelectedModelId(spec.id)
                    }
                    val nextPartials = if (ok) {
                        cur.partialDownloadPercents - modelId
                    } else if (partialPct != null) {
                        cur.partialDownloadPercents + (modelId to partialPct)
                    } else {
                        cur.partialDownloadPercents
                    }
                    cur.copy(
                        installedModelIds = nextIds,
                        selectedModelId = if (ok && !silent) spec.id else cur.selectedModelId,
                        modelDownloadProgress = cur.modelDownloadProgress - modelId,
                        partialDownloadPercents = nextPartials,
                        statusBanner = if (silent) cur.statusBanner else detailMessage
                    )
                }
                if (ok) {
                    // Immediately pre-warm downloaded weights into RAM silently in background
                    preloadModelIntoRam(app, scope, ui, spec.id)
                }
            } catch (_: CancellationException) {
                val partialPct = ModelDownloadManager.getPartialDownloadPercent(app, modelId)
                ui.update { cur ->
                    val nextPartials = if (partialPct != null) cur.partialDownloadPercents + (modelId to partialPct) else cur.partialDownloadPercents
                    cur.copy(
                        modelDownloadProgress = cur.modelDownloadProgress - modelId,
                        partialDownloadPercents = nextPartials
                    )
                }
            } finally {
                activeDownloadJobs.remove(modelId)
            }
        }
        activeDownloadJobs[modelId] = job
    }

    fun importModelTaskFile(
        app: Application,
        scope: CoroutineScope,
        ui: MutableStateFlow<ShamiaUiState>,
        store: RoutineSyllabusStore,
        modelId: String,
        sourceUri: Uri
    ) {
        val state = ui.value
        val spec = state.allModels.find { it.id == modelId } ?: return
        if (state.modelDownloadProgress.containsKey(modelId)) return
        scope.launch {
            val (ok, detailMessage) = ModelDownloadManager.importLocalTaskFile(app, spec, sourceUri) { pct, statusText ->
                ui.update { cur -> cur.copy(modelDownloadProgress = cur.modelDownloadProgress + (modelId to (pct to statusText))) }
            }
            ui.update { cur ->
                val nextIds = if (ok) cur.installedModelIds + modelId else cur.installedModelIds
                if (ok) {
                    store.saveInstalledModelIds(nextIds)
                    store.saveSelectedModelId(spec.id)
                }
                cur.copy(
                    installedModelIds = nextIds,
                    selectedModelId = if (ok) spec.id else cur.selectedModelId,
                    modelDownloadProgress = cur.modelDownloadProgress - modelId,
                    partialDownloadPercents = cur.partialDownloadPercents - modelId,
                    statusBanner = detailMessage
                )
            }
            if (ok) {
                preloadModelIntoRam(app, scope, ui, spec.id)
            }
        }
    }

    fun installOrRemoveVoice(
        app: Application,
        scope: CoroutineScope,
        ui: MutableStateFlow<ShamiaUiState>,
        store: RoutineSyllabusStore,
        voiceId: String
    ) {
        val state = ui.value
        val voice = ModelCatalog.VOICE_MODELS.find { it.id == voiceId } ?: return
        if (state.installedVoiceIds.contains(voiceId)) {
            ModelDownloadManager.removeVoiceCache(app, voiceId)
            val next = state.installedVoiceIds - voiceId
            store.saveInstalledVoiceIds(next)
            ui.update { it.copy(installedVoiceIds = next, statusBanner = "Removed ${voice.name}.") }
            return
        }
        if (state.hardware.isLowMemory || state.hardware.ramGb < voice.minRamGb) {
            ui.update { it.copy(statusBanner = "OOM Protection: ${voice.name} needs ${voice.minRamGb} GB RAM.") }
            return
        }
        if (state.voiceDownloadProgress.containsKey(voiceId)) return
        scope.launch {
            val ok = ModelDownloadManager.installVoiceModelOffline(app, voice) { pct, statusText ->
                ui.update { cur -> cur.copy(voiceDownloadProgress = cur.voiceDownloadProgress + (voiceId to (pct to statusText))) }
            }
            ui.update { cur ->
                val nextIds = if (ok) cur.installedVoiceIds + voiceId else cur.installedVoiceIds
                if (ok) store.saveInstalledVoiceIds(nextIds)
                cur.copy(
                    installedVoiceIds = nextIds,
                    voiceDownloadProgress = cur.voiceDownloadProgress - voiceId,
                    statusBanner = "${voice.name} ready offline."
                )
            }
        }
    }

    fun addTask(ui: MutableStateFlow<ShamiaUiState>, store: RoutineSyllabusStore, title: String, high: Boolean) {
        if (title.isBlank()) return
        val next = listOf(TaskItem("t-${System.currentTimeMillis()}", title.trim(), high)) + ui.value.tasks
        store.saveTasks(next); ui.update { it.copy(tasks = next) }
    }

    fun addNote(ui: MutableStateFlow<ShamiaUiState>, store: RoutineSyllabusStore, content: String) {
        if (content.isBlank()) return
        val next = listOf(NoteItem("n-${System.currentTimeMillis()}", content.trim())) + ui.value.notes
        store.saveNotes(next); ui.update { it.copy(notes = next) }
    }

    fun addRoutine(ui: MutableStateFlow<ShamiaUiState>, store: RoutineSyllabusStore, timeLabel: String, title: String, category: RoutineCategory) {
        if (title.isBlank()) return
        val next = ui.value.routine + RoutineItem("r-${System.currentTimeMillis()}", timeLabel.ifBlank { "Anytime" }, title.trim(), category)
        store.saveRoutine(next); ui.update { it.copy(routine = next) }
    }

    fun addSyllabus(ui: MutableStateFlow<ShamiaUiState>, store: RoutineSyllabusStore, code: String, title: String, topic: String, examNote: String) {
        if (topic.isBlank()) return
        val next = ui.value.syllabus + SyllabusItem(
            "s-${System.currentTimeMillis()}",
            code.trim().ifBlank { "COURSE" },
            title.trim().ifBlank { "Subject" },
            topic.trim(),
            examNote.trim().takeIf { it.isNotEmpty() }
        )
        store.saveSyllabus(next); ui.update { it.copy(syllabus = next) }
    }
}
