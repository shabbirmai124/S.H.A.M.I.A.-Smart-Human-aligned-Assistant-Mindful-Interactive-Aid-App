package com.shamia.assistant.ui

import com.shamia.assistant.engine.ModelCatalog
import com.shamia.assistant.engine.ModelDownloadManager
import com.shamia.assistant.model.AlarmItem
import com.shamia.assistant.model.AmbientTrack
import com.shamia.assistant.model.AppThemeMode
import com.shamia.assistant.model.CoreVisualState
import com.shamia.assistant.model.DeviceControlState
import com.shamia.assistant.model.DeviceHardwareSpec
import com.shamia.assistant.model.DeviceTelemetry
import com.shamia.assistant.model.HudMessage
import com.shamia.assistant.model.NoteItem
import com.shamia.assistant.model.OpenSourceModelSpec
import com.shamia.assistant.model.PendingAction
import com.shamia.assistant.model.PermissionCapability
import com.shamia.assistant.model.PermissionPolicy
import com.shamia.assistant.model.RoutineItem
import com.shamia.assistant.model.SyllabusItem
import com.shamia.assistant.model.TaskItem
import com.shamia.assistant.model.UserIdentityProfile
import com.shamia.assistant.model.VoiceGender

data class ShamiaUiState(
    val chatSessionId: String = "session-initial",
    val identity: UserIdentityProfile = UserIdentityProfile(),
    val hardware: DeviceHardwareSpec = DeviceHardwareSpec(
        ramGb = 6,
        cpuCores = 8,
        deviceLabel = "Android Device"
    ),
    val telemetry: DeviceTelemetry = DeviceTelemetry(),
    val nowMillis: Long = System.currentTimeMillis(),
    val clockLabel: String = "",
    val messages: List<HudMessage> = emptyList(),
    val coreState: CoreVisualState = CoreVisualState.IDLE,
    val isGenerating: Boolean = false,
    val liveTranscript: String = "",
    val voiceReplyEnabled: Boolean = true,
    val handsFreeVoiceMode: Boolean = false,
    val interactionSoundEnabled: Boolean = true,
    val voiceGender: VoiceGender = VoiceGender.FEMALE,
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val showAllModels: Boolean = false,
    val useResearch: Boolean = false,
    val selectedModelId: String = "offline-core",
    val recommendedModelId: String = "qwen25-1.5b-edge",
    val ensembleEnabled: Boolean = false,
    val ensembleModelIds: List<String> = listOf("qwen25-0.5b-webgpu", "qwen25-1.5b-edge", "nvidia-nemotron-mini-4b"),
    val customModels: List<OpenSourceModelSpec> = emptyList(),
    val installedModelIds: Set<String> = setOf("offline-core"),
    val modelDownloadProgress: Map<String, Pair<Int, String>> = emptyMap(),
    val partialDownloadPercents: Map<String, Int> = emptyMap(),
    val loadedInRamModelId: String? = null,
    val loadingInRamModelId: String? = null,
    val loadedBackendLabel: String = "",
    val installedVoiceIds: Set<String> = emptySet(),
    val voiceDownloadProgress: Map<String, Pair<Int, String>> = emptyMap(),
    val ollamaEndpoint: String = "http://127.0.0.1:11434",
    val serverBaseUrl: String = "",
    val ollamaTestStatus: String = "",
    val tasks: List<TaskItem> = emptyList(),
    val notes: List<NoteItem> = emptyList(),
    val routine: List<RoutineItem> = emptyList(),
    val syllabus: List<SyllabusItem> = emptyList(),
    val alarms: List<AlarmItem> = emptyList(),
    val controls: DeviceControlState = DeviceControlState(),
    val screenTorchActive: Boolean = false,
    val pendingModalAction: Pair<String, PendingAction>? = null,
    val musicActive: Boolean = false,
    val musicPlaying: Boolean = false,
    val activeTrackIndex: Int = 0,
    val musicVolume: Float = 0.25f,
    val windowBrightnessPct: Int? = null,
    val permissions: Map<PermissionCapability, PermissionPolicy> = emptyMap(),
    val statusBanner: String? = null
) {
    val allModels: List<OpenSourceModelSpec>
        get() = ModelCatalog.CATALOG + customModels

    fun isModelSafeForDevice(spec: OpenSourceModelSpec): Boolean =
        ModelDownloadManager.isSafeForLocalInstall(spec, hardware)

    val compatibleModels: List<OpenSourceModelSpec>
        get() = allModels.filter { isModelSafeForDevice(it) }

    val displayedModels: List<OpenSourceModelSpec>
        get() = if (showAllModels) allModels else compatibleModels

    val selectedModel: OpenSourceModelSpec
        get() = allModels.find { it.id == selectedModelId } ?: ModelCatalog.CATALOG.first()

    val isBusyOrSpeaking: Boolean
        get() = isGenerating || coreState == CoreVisualState.THINKING || coreState == CoreVisualState.SPEAKING

    val activeTrackId: String?
        get() = if (musicActive && musicPlaying) ModelCatalog.AMBIENT_TRACKS.getOrNull(activeTrackIndex)?.id else null

    val activeTrack: AmbientTrack
        get() = ModelCatalog.AMBIENT_TRACKS.getOrElse(activeTrackIndex) {
            ModelCatalog.AMBIENT_TRACKS.first()
        }
}
