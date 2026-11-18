package com.shamia.assistant.engine

import android.app.Application
import com.shamia.assistant.model.NoteItem
import com.shamia.assistant.model.RoutineCategory
import com.shamia.assistant.model.RoutineItem
import com.shamia.assistant.model.SyllabusItem
import com.shamia.assistant.model.TaskItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.shamia.assistant.ui.ShamiaUiState

internal object ShamiaStateDelegates {

    fun installOrRemoveModel(
        app: Application,
        scope: CoroutineScope,
        ui: MutableStateFlow<ShamiaUiState>,
        store: RoutineSyllabusStore,
        modelId: String
    ) {
        val state = ui.value
        val spec = state.allModels.find { it.id == modelId } ?: return
        if (state.installedModelIds.contains(modelId) && !spec.isBuiltInOffline) {
            ModelDownloadManager.removeOfflineCache(app, modelId)
            val next = state.installedModelIds - modelId
            store.saveInstalledModelIds(next)
            ui.update { it.copy(installedModelIds = next, statusBanner = "Removed offline cache for ${spec.name}.") }
            return
        }
        if (!ModelDownloadManager.isSafeForLocalInstall(spec, state.hardware)) {
            ui.update {
                it.copy(statusBanner = "OOM Protection: ${spec.name} requires ${spec.minRamGb} GB RAM (Device: ${state.hardware.ramGb} GB).")
            }
            return
        }
        if (state.modelDownloadProgress.containsKey(modelId)) return
        scope.launch {
            val ok = ModelDownloadManager.installModelOffline(app, spec, ui.value.ollamaEndpoint) { pct, statusText ->
                ui.update { cur -> cur.copy(modelDownloadProgress = cur.modelDownloadProgress + (modelId to (pct to statusText))) }
            }
            ui.update { cur ->
                val nextIds = if (ok) cur.installedModelIds + modelId else cur.installedModelIds
                if (ok) store.saveInstalledModelIds(nextIds)
                cur.copy(
                    installedModelIds = nextIds,
                    modelDownloadProgress = cur.modelDownloadProgress - modelId,
                    statusBanner = if (ok) "${spec.name} installed for offline use." else "Offline install cancelled."
                )
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
