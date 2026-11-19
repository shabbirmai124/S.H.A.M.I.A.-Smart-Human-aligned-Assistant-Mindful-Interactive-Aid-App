package com.shamia.assistant.engine

import android.app.Application
import android.net.Uri
import com.shamia.assistant.model.NoteItem
import com.shamia.assistant.model.RoutineCategory
import com.shamia.assistant.model.RoutineItem
import com.shamia.assistant.model.SyllabusItem
import com.shamia.assistant.model.TaskItem
import com.shamia.assistant.ui.ShamiaUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal object ShamiaStateDelegates {

    fun preloadModelIntoRam(
        app: Application,
        ui: MutableStateFlow<ShamiaUiState>,
        modelId: String
    ) {
        if (modelId == "offline-core") return
        val name = ui.value.allModels.find { it.id == modelId }?.name ?: modelId
        ModelBackgroundHub.enqueuePreload(app, modelId, name)
    }

    fun pauseModelDownload(
        app: Application,
        modelId: String
    ) {
        ModelBackgroundHub.pauseDownload(app, modelId)
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
            ModelBackgroundHub.markRemoved(modelId)
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
        if (ModelBackgroundHub.isDownloading(modelId)) return

        // Delegate to process-scoped ModelBackgroundHub + ModelBackgroundService so download & RAM preload
        // continue running smoothly even when the user switches apps or turns off the display!
        ModelBackgroundHub.enqueueDownload(
            context = app,
            spec = spec,
            ollamaEndpoint = ui.value.ollamaEndpoint,
            silent = silent,
            onFinished = { ok, detailMessage ->
                ui.update { cur ->
                    val nextIds = if (ok) cur.installedModelIds + modelId else cur.installedModelIds
                    cur.copy(
                        installedModelIds = nextIds,
                        selectedModelId = if (ok && !silent) spec.id else cur.selectedModelId,
                        statusBanner = if (silent) cur.statusBanner else detailMessage
                    )
                }
            }
        )
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
                ModelBackgroundHub.enqueuePreload(app, spec.id, spec.name)
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
