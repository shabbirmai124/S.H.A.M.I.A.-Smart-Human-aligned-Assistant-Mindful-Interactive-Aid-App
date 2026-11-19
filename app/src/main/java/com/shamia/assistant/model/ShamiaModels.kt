package com.shamia.assistant.model

/**
 * S.H.A.M.I.A. — Smart Human-aligned Assistant & Mindful Interactive Aid
 * Kotlin Mobile Architecture Models
 * Exclusive Author & Owner: Md Shabbir Mia <shabbirmia520@gmail.com>
 */

enum class ConversationalIntent {
    GENERAL,
    ENGLISH_TUTOR,
    MENTAL_SUPPORT,
    DEVICE_ACTION
}

enum class CoreVisualState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    AWAITING_PERMISSION
}

enum class MessageSource(val label: String) {
    OFFLINE_CORE("Offline Core"),
    OLLAMA_LOCAL("Local Ollama"),
    CLOUD_OPEN("Cloud Model"),
    CLOUD_RESEARCH("Live Web Search"),
    PERMISSION_GATE("Permission Vault")
}

enum class PermissionCapability(val label: String, val description: String) {
    DIAL_CONTACT("Phone Dialer", "Place calls or open tel: links when you ask to dial a number."),
    OPEN_APP("App Launcher", "Open external apps and web destinations (Spotify, Maps, Calendar, Gmail)."),
    DEVICE_CONTROL("Phone Hardware Controls", "Toggle Wi-Fi, Bluetooth, Do Not Disturb, Flashlight, and Screen Brightness."),
    PLAY_MUSIC("Music & Media", "Start ambient audio playback and control the active track queue."),
    SET_ALARM("Alarms & Timers", "Arm countdown alarms and play audible chime alerts."),
    MANAGE_MEMORY("Tasks & Notes Memory", "Save priorities, notes, and clear completed items in local storage."),
    MICROPHONE("Voice Microphone", "Capture spoken commands via Android Speech Recognition.")
}

enum class PermissionPolicy {
    ASK,
    ALWAYS,
    DENY
}

enum class ActionStatus {
    PENDING,
    APPROVED,
    DENIED,
    BLOCKED
}

enum class DeviceActionType {
    DIAL_CONTACT,
    OPEN_APP,
    TOGGLE_WIFI,
    TOGGLE_BLUETOOTH,
    TOGGLE_DND,
    TOGGLE_FLASHLIGHT,
    SET_BRIGHTNESS,
    SET_ALARM,
    PLAY_MUSIC,
    ADD_TASK,
    CLEAR_COMPLETED_TASKS,
    ADD_NOTE
}

enum class VoiceModelKind {
    STT,
    TTS
}

enum class VoiceGender(val label: String) {
    FEMALE("Female (Clear)"),
    MALE("Male (Clear)")
}

enum class AppThemeMode(val label: String) {
    DARK("Obsidian Dark"),
    LIGHT("Google White")
}

enum class RoutineCategory(val label: String) {
    ROUTINE("Routine"),
    CLASS("Class"),
    STUDY("Study")
}

data class UserIdentityProfile(
    val fullName: String = "Md Shabbir Mia",
    val addressAs: String = "Sir"
)

data class OpenSourceModelSpec(
    val id: String,
    val name: String,
    val provider: String,
    val params: String,
    val sizeLabel: String,
    val minRamGb: Int,
    val chipsetNote: String = "Compatible",
    val runtimeLabel: String = "Cloud + Ollama",
    val hfRepo: String? = null,
    val ollamaTag: String? = null,
    val cloudModelId: String = "openai",
    val hfInferenceModelId: String? = null,
    val styleProfile: String = "",
    val summary: String = "",
    val isBuiltInOffline: Boolean = false,
    val supportsOfflineInstall: Boolean = true,
    val isCustom: Boolean = false
)

data class OfflineVoiceModelSpec(
    val id: String,
    val name: String,
    val kind: VoiceModelKind,
    val provider: String,
    val sizeLabel: String,
    val minRamGb: Int,
    val hfRepo: String,
    val summary: String
)

data class DeviceHardwareSpec(
    val ramGb: Int,
    val availRamMb: Int = 2048,
    val isLowMemory: Boolean = false,
    val cpuCores: Int,
    val deviceLabel: String,
    val recommendedTier: String = "OOM Protected"
)

data class DeviceTelemetry(
    val batteryPct: Int? = null,
    val isCharging: Boolean = false,
    val isOnline: Boolean = true
)

data class GroundingLink(
    val title: String,
    val uri: String
)

data class CoachingTip(
    val correction: String? = null,
    val naturalPhrase: String? = null,
    val tip: String? = null
)

data class ModelExecutionMeta(
    val requestedModelId: String,
    val requestedModelName: String,
    val executedModelId: String,
    val providerEngine: String,
    val cacheBypassed: Boolean = true,
    val verifiedExactModel: Boolean = false,
    val verificationProof: String = ""
)

data class PendingAction(
    val id: String,
    val type: DeviceActionType,
    val capability: PermissionCapability,
    val title: String,
    val detail: String,
    val targetValue: String = "",
    val toggleState: Boolean = true,
    val numericValue: Int = 100,
    val status: ActionStatus = ActionStatus.PENDING
)

data class HudMessage(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val timestampLabel: String,
    val sessionId: String = "session-1",
    val modelIdForTurn: String? = null,
    val intent: ConversationalIntent = ConversationalIntent.GENERAL,
    val source: MessageSource? = null,
    val modelLabel: String? = null,
    val executionMeta: ModelExecutionMeta? = null,
    val pendingAction: PendingAction? = null,
    val coachingTip: CoachingTip? = null,
    val showBreathingGuide: Boolean = false,
    val groundingLinks: List<GroundingLink> = emptyList()
)

data class RoutineItem(
    val id: String,
    val timeLabel: String,
    val title: String,
    val category: RoutineCategory = RoutineCategory.STUDY,
    val completed: Boolean = false
)

data class SyllabusItem(
    val id: String,
    val courseCode: String,
    val courseTitle: String,
    val topic: String,
    val examNote: String? = null,
    val completed: Boolean = false
)

data class TaskItem(
    val id: String,
    val title: String,
    val isHighPriority: Boolean = false,
    val completed: Boolean = false
)

data class NoteItem(
    val id: String,
    val content: String
)

data class AlarmItem(
    val id: String,
    val label: String,
    val minutesFromNow: Int,
    val triggerAtMillis: Long,
    val active: Boolean = true
)

data class AmbientTrack(
    val id: String,
    val title: String,
    val artist: String,
    val mood: String,
    val bpm: Int,
    val baseFreqHz: Double,
    val chordFreqs: List<Double>
)

data class DeviceControlState(
    val wifi: Boolean = true,
    val bluetooth: Boolean = true,
    val dnd: Boolean = false,
    val flashlight: Boolean = false,
    val brightnessPct: Int = 100
)
