package com.shamia.assistant.engine

import com.shamia.assistant.model.AlarmItem
import com.shamia.assistant.model.ConversationalIntent
import com.shamia.assistant.model.DeviceActionType
import com.shamia.assistant.model.DeviceControlState
import com.shamia.assistant.model.DeviceTelemetry
import com.shamia.assistant.model.PendingAction
import com.shamia.assistant.model.PermissionCapability
import com.shamia.assistant.model.PermissionPolicy
import com.shamia.assistant.model.RoutineItem
import com.shamia.assistant.model.SyllabusItem
import com.shamia.assistant.model.TaskItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object IntentEngine {

    private val supportedApps = linkedMapOf(
        "spotify" to ("Spotify" to "https://open.spotify.com"),
        "youtube music" to ("YouTube Music" to "https://music.youtube.com"),
        "yt music" to ("YouTube Music" to "https://music.youtube.com"),
        "youtube" to ("YouTube" to "https://www.youtube.com"),
        "google maps" to ("Google Maps" to "https://maps.google.com"),
        "maps" to ("Google Maps" to "https://maps.google.com"),
        "navigation" to ("Google Maps" to "https://maps.google.com"),
        "calendar" to ("Google Calendar" to "https://calendar.google.com"),
        "schedule" to ("Google Calendar" to "https://calendar.google.com"),
        "gmail" to ("Gmail" to "https://mail.google.com"),
        "email" to ("Gmail" to "https://mail.google.com"),
        "whatsapp" to ("WhatsApp" to "https://web.whatsapp.com"),
        "github" to ("GitHub" to "https://github.com")
    )

    fun evaluatePermission(
        policies: Map<PermissionCapability, PermissionPolicy>,
        capability: PermissionCapability
    ): PermissionPolicy = policies[capability] ?: PermissionPolicy.ASK

    fun validatePhoneNumber(raw: String): String? {
        val cleaned = raw.replace(Regex("[^\\d+]"), "")
        val digits = cleaned.replace(Regex("\\D"), "")
        return if (digits.length in 3..15) cleaned else null
    }

    fun resolveSupportedApp(appName: String): Pair<String, String>? {
        val lower = appName.trim().lowercase(Locale.US)
        if (lower.isEmpty()) return null
        return supportedApps.entries.firstOrNull { lower.contains(it.key) }?.value
    }

    fun detectConversationalIntent(input: String): ConversationalIntent {
        val lower = input.lowercase(Locale.US)
        if (Regex("\\b(practice english|speak english|english tutor|english coach|grammar|pronunciation|fluency|correct my sentence|correct my english|idiom|interview practice|english speaking|i am go|yesterday i go|very much tired)\\b").containsMatchIn(lower)) {
            return ConversationalIntent.ENGLISH_TUTOR
        }
        if (Regex("\\b(stressed|overwhelmed|anxious|anxiety|depressed|lonely|sad|tired of|burnt out|burnout|mental health|mental support|panic|worried|heartbroken|scared|talk to me|feeling down|breathe|calm me)\\b").containsMatchIn(lower)) {
            return ConversationalIntent.MENTAL_SUPPORT
        }
        if (Regex("^(call|dial|phone|ring|open|launch|go to|play|put on|set (?:an? )?alarm|wake me|set (?:a )?timer|turn (?:on|off)|enable|disable|toggle|set brightness|dim screen|brighten)\\b").containsMatchIn(lower)) {
            return ConversationalIntent.DEVICE_ACTION
        }
        return ConversationalIntent.GENERAL
    }

    fun shouldTriggerLiveSearch(input: String, forceSearch: Boolean = false): Boolean {
        if (forceSearch) return true
        if (input.isBlank()) return false
        return Regex(
            "\\b(search|research|look up|google|latest|news|who won|what happened|current price|weather|real-?time|4nm|6nm|3nm|processor|chipset|snapdragon|dimensity|exynos|tensor|bionic|which phones?|what phones?|smartphones?|compare|specs|specification|benchmark|today|2025|2026)\\b",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(input)
    }

    data class ParsedAlarm(
        val label: String,
        val minutesFromNow: Int,
        val triggerAtMillis: Long,
        val formattedTime: String
    )

    fun parseAlarmRequest(input: String, nowMillis: Long = System.currentTimeMillis()): ParsedAlarm {
        val lower = input.lowercase(Locale.US)
        val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault())

        val relMatch = Regex("\\b(?:in|for)\\s+(\\d+)\\s*(sec|secs|second|seconds|min|mins|minute|minutes|hr|hrs|hour|hours)\\b").find(lower)
        if (relMatch != null) {
            val amount = (relMatch.groupValues[1].toIntOrNull() ?: 5).coerceAtLeast(1)
            val unit = relMatch.groupValues[2]
            val deltaMs = when {
                unit.startsWith("sec") -> amount * 1_000L
                unit.startsWith("hr") || unit.startsWith("hour") -> amount * 3_600_000L
                else -> amount * 60_000L
            }
            val triggerAt = nowMillis + deltaMs
            val minsApprox = ((deltaMs + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
            return ParsedAlarm(
                label = "Timer ($amount $unit)",
                minutesFromNow = minsApprox,
                triggerAtMillis = triggerAt,
                formattedTime = timeFmt.format(Date(triggerAt))
            )
        }

        val clockMatch = Regex("\\b(?:at|for)\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\b").find(lower)
        if (clockMatch != null) {
            var hours = clockMatch.groupValues[1].toIntOrNull() ?: 8
            val minutes = clockMatch.groupValues[2].toIntOrNull() ?: 0
            val meridiem = clockMatch.groupValues[3]
            if (meridiem == "pm" && hours < 12) hours += 12
            if (meridiem == "am" && hours == 12) hours = 0
            if (hours in 0..23 && minutes in 0..59) {
                val cal = Calendar.getInstance().apply {
                    timeInMillis = nowMillis
                    set(Calendar.HOUR_OF_DAY, hours)
                    set(Calendar.MINUTE, minutes)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    if (timeInMillis <= nowMillis) add(Calendar.DAY_OF_YEAR, 1)
                }
                val diffMins = ((cal.timeInMillis - nowMillis) / 60_000L).toInt().coerceAtLeast(1)
                val formatted = timeFmt.format(Date(cal.timeInMillis))
                return ParsedAlarm(
                    label = "Alarm ($formatted)",
                    minutesFromNow = diffMins,
                    triggerAtMillis = cal.timeInMillis,
                    formattedTime = formatted
                )
            }
        }

        val fallbackAt = nowMillis + 5 * 60_000L
        return ParsedAlarm(
            label = "Quick 5-Minute Alarm",
            minutesFromNow = 5,
            triggerAtMillis = fallbackAt,
            formattedTime = timeFmt.format(Date(fallbackAt))
        )
    }

    sealed class MemoryMutation {
        data class AddTask(val title: String, val highPriority: Boolean) : MemoryMutation()
        data object ClearCompletedTasks : MemoryMutation()
        data class AddNote(val content: String) : MemoryMutation()
    }

    data class LocalIntentResult(
        val handled: Boolean,
        val intent: ConversationalIntent = ConversationalIntent.GENERAL,
        val replyText: String? = null,
        val proposedAction: PendingAction? = null,
        val memoryMutation: MemoryMutation? = null
    )

    fun matchLocalIntent(
        input: String,
        addressAs: String,
        controls: DeviceControlState,
        telemetry: DeviceTelemetry,
        tasks: List<TaskItem>,
        alarms: List<AlarmItem>,
        routine: List<RoutineItem>,
        syllabus: List<SyllabusItem>
    ): LocalIntentResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return LocalIntentResult(false)
        val lower = trimmed.lowercase(Locale.US)
        val addr = addressAs.trim().ifEmpty { "Sir" }

        // 1. Time & Date query
        if (Regex("^(what('s| is) the time|tell me the time|current time|time please|what time is it)\\b").containsMatchIn(lower) || lower == "time") {
            val formatted = SimpleDateFormat("hh:mm a 'on' EEEE, MMM d", Locale.getDefault()).format(Date())
            return LocalIntentResult(true, ConversationalIntent.GENERAL, "It is currently $formatted, $addr.")
        }

        // 2. Battery query
        if (Regex("\\b(battery|power level|charge level)\\b").containsMatchIn(lower) && lower.length < 48) {
            val pct = telemetry.batteryPct
            val msg = if (pct != null) {
                val status = if (telemetry.isCharging) "charging" else "discharging"
                "Power reserves are at $pct% and currently $status, $addr."
            } else {
                "Direct battery telemetry is unavailable right now, $addr, though all core systems are steady."
            }
            return LocalIntentResult(true, ConversationalIntent.GENERAL, msg)
        }

        // 3. Status Report / Diagnostics
        if (Regex("^(status report|system status|diagnostics|phone status)\\b").containsMatchIn(lower)) {
            val pending = tasks.count { !it.completed }
            val activeAlarms = alarms.count { it.active }
            val studyPending = syllabus.count { !it.completed }
            val powerPart = if (telemetry.batteryPct != null) {
                "power at ${telemetry.batteryPct}%${if (telemetry.isCharging) " (charging)" else ""}"
            } else {
                "primary power steady"
            }
            return LocalIntentResult(
                handled = true,
                intent = ConversationalIntent.GENERAL,
                replyText = "All subsystems are nominal, $addr: $powerPart, Wi-Fi ${if (controls.wifi) "on" else "off"}, Bluetooth ${if (controls.bluetooth) "on" else "off"}, DND ${if (controls.dnd) "on" else "off"}, $pending pending tasks, $activeAlarms active alarms, ${routine.size} routine blocks, and $studyPending syllabus topics remaining."
            )
        }

        // 4. Hardware Control Toggles (Permission-Gated)
        Regex("\\b(turn on|enable|turn off|disable|toggle)\\s+(?:the\\s+)?wi-?fi\\b").find(lower)?.let { m ->
            val verb = m.groupValues[1]
            val next = when {
                verb.contains("on") || verb == "enable" -> true
                verb.contains("off") || verb == "disable" -> false
                else -> !controls.wifi
            }
            return LocalIntentResult(
                handled = true,
                intent = ConversationalIntent.DEVICE_ACTION,
                replyText = "Wi-Fi ${if (next) "activation" else "deactivation"} staged, $addr. Please authorize.",
                proposedAction = PendingAction(
                    id = "act-${System.currentTimeMillis()}",
                    type = DeviceActionType.TOGGLE_WIFI,
                    capability = PermissionCapability.DEVICE_CONTROL,
                    title = "${if (next) "Enable" else "Disable"} Wi-Fi",
                    detail = "Open Android Wi-Fi control panel.",
                    toggleState = next
                )
            )
        }

        Regex("\\b(turn on|enable|turn off|disable|toggle)\\s+(?:the\\s+)?bluetooth\\b").find(lower)?.let { m ->
            val verb = m.groupValues[1]
            val next = when {
                verb.contains("on") || verb == "enable" -> true
                verb.contains("off") || verb == "disable" -> false
                else -> !controls.bluetooth
            }
            return LocalIntentResult(
                handled = true,
                intent = ConversationalIntent.DEVICE_ACTION,
                replyText = "Bluetooth toggle staged, $addr. Please authorize.",
                proposedAction = PendingAction(
                    id = "act-${System.currentTimeMillis()}",
                    type = DeviceActionType.TOGGLE_BLUETOOTH,
                    capability = PermissionCapability.DEVICE_CONTROL,
                    title = "${if (next) "Enable" else "Disable"} Bluetooth",
                    detail = "Open Android Bluetooth settings.",
                    toggleState = next
                )
            )
        }

        Regex("\\b(turn on|enable|turn off|disable|toggle)\\s+(?:the\\s+)?(?:do not disturb|dnd|silent mode)\\b").find(lower)?.let { m ->
            val verb = m.groupValues[1]
            val next = when {
                verb.contains("on") || verb == "enable" -> true
                verb.contains("off") || verb == "disable" -> false
                else -> !controls.dnd
            }
            return LocalIntentResult(
                handled = true,
                intent = ConversationalIntent.DEVICE_ACTION,
                replyText = "Do Not Disturb mode staged, $addr. Please authorize.",
                proposedAction = PendingAction(
                    id = "act-${System.currentTimeMillis()}",
                    type = DeviceActionType.TOGGLE_DND,
                    capability = PermissionCapability.DEVICE_CONTROL,
                    title = "${if (next) "Enable" else "Disable"} Do Not Disturb",
                    detail = "Update Android DND silence state.",
                    toggleState = next
                )
            )
        }

        Regex("\\b(turn on|enable|turn off|disable|toggle)\\s+(?:the\\s+)?(?:flashlight|torch)\\b").find(lower)?.let { m ->
            val verb = m.groupValues[1]
            val next = when {
                verb.contains("on") || verb == "enable" -> true
                verb.contains("off") || verb == "disable" -> false
                else -> !controls.flashlight
            }
            return LocalIntentResult(
                handled = true,
                intent = ConversationalIntent.DEVICE_ACTION,
                replyText = "Flashlight beam staged, $addr. Please authorize.",
                proposedAction = PendingAction(
                    id = "act-${System.currentTimeMillis()}",
                    type = DeviceActionType.TOGGLE_FLASHLIGHT,
                    capability = PermissionCapability.DEVICE_CONTROL,
                    title = "${if (next) "Enable" else "Disable"} Flashlight",
                    detail = "Toggle device rear camera LED torch.",
                    toggleState = next
                )
            )
        }

        Regex("\\b(?:set\\s+)?brightness\\s+(?:to\\s+)?(\\d{1,3})\\s*%?").find(lower)?.let { m ->
            val pct = (m.groupValues[1].toIntOrNull() ?: 80).coerceIn(25, 100)
            return LocalIntentResult(
                handled = true,
                intent = ConversationalIntent.DEVICE_ACTION,
                replyText = "Display brightness change to $pct% staged, $addr. Please authorize.",
                proposedAction = PendingAction(
                    id = "act-${System.currentTimeMillis()}",
                    type = DeviceActionType.SET_BRIGHTNESS,
                    capability = PermissionCapability.DEVICE_CONTROL,
                    title = "Set Brightness to $pct%",
                    detail = "Adjust window display luminance to $pct%.",
                    numericValue = pct
                )
            )
        }

        // 5. Clear completed tasks
        if (Regex("\\b(clear|remove|delete)\\s+(completed|done)\\s*(tasks|items|priorities)?\\b").containsMatchIn(lower)) {
            return LocalIntentResult(
                handled = true,
                intent = ConversationalIntent.DEVICE_ACTION,
                replyText = "Completed items have been cleared from your priority ledger, $addr.",
                memoryMutation = MemoryMutation.ClearCompletedTasks
            )
        }

        // 6. Add task
        Regex("^(?:remember(?:\\s+to)?|add\\s+task|set\\s+task|new\\s+priority|remind\\s+me\\s+to|add\\s+priority)\\s*:?\\s+(.+)$", RegexOption.IGNORE_CASE).find(trimmed)?.let { m ->
            val title = m.groupValues[1].trim()
            if (title.isNotEmpty()) {
                val isHigh = lower.contains("urgent") || lower.contains("high")
                return LocalIntentResult(
                    handled = true,
                    intent = ConversationalIntent.DEVICE_ACTION,
                    replyText = "Recorded to your priority ledger, $addr: \"$title\".",
                    memoryMutation = MemoryMutation.AddTask(title, isHigh)
                )
            }
        }

        // 7. Add note
        Regex("^(?:take\\s+a\\s+note|add\\s+note|note\\s+this)\\s*:?\\s+(.+)$", RegexOption.IGNORE_CASE).find(trimmed)?.let { m ->
            val content = m.groupValues[1].trim()
            if (content.isNotEmpty()) {
                return LocalIntentResult(
                    handled = true,
                    intent = ConversationalIntent.DEVICE_ACTION,
                    replyText = "Noted and archived locally, $addr.",
                    memoryMutation = MemoryMutation.AddNote(content)
                )
            }
        }

        // 8. Dial / Call
        Regex("^(?:call|dial|phone|ring)\\s+(.+)$", RegexOption.IGNORE_CASE).find(trimmed)?.let { m ->
            val targetRaw = m.groupValues[1].trim()
            val validPhone = validatePhoneNumber(targetRaw)
            val displayTarget = validPhone ?: targetRaw
            return LocalIntentResult(
                handled = true,
                intent = ConversationalIntent.DEVICE_ACTION,
                replyText = "I have prepared a call to $displayTarget, $addr. Please confirm permission to proceed.",
                proposedAction = PendingAction(
                    id = "act-${System.currentTimeMillis()}",
                    type = DeviceActionType.DIAL_CONTACT,
                    capability = PermissionCapability.DIAL_CONTACT,
                    title = "Dial $displayTarget",
                    detail = if (validPhone != null) {
                        "Initiate a phone call to $validPhone via your device dialer."
                    } else {
                        "Confirm phone digits for \"$targetRaw\" before placing the call."
                    },
                    targetValue = validPhone.orEmpty()
                )
            )
        }

        // 9. Open App
        Regex("^(?:open|launch|go\\s+to|start)\\s+(?:the\\s+)?(.+?)(?:\\s+app)?$", RegexOption.IGNORE_CASE).find(trimmed)?.let { m ->
            val app = resolveSupportedApp(m.groupValues[1])
            if (app != null) {
                return LocalIntentResult(
                    handled = true,
                    intent = ConversationalIntent.DEVICE_ACTION,
                    replyText = "Standing by to open ${app.first}, $addr. Awaiting your authorization.",
                    proposedAction = PendingAction(
                        id = "act-${System.currentTimeMillis()}",
                        type = DeviceActionType.OPEN_APP,
                        capability = PermissionCapability.OPEN_APP,
                        title = "Open ${app.first}",
                        detail = "Launch ${app.second}",
                        targetValue = app.second
                    )
                )
            }
        }

        // 10. Play Music
        val musicMatch = Regex("^(?:play|put\\s+on)\\s+(?:some\\s+)?(?:(.+?)\\s+)?(?:music|song|tracks?|audio)$", RegexOption.IGNORE_CASE).find(lower)
        if (musicMatch != null || lower == "play focus music" || lower == "play music" || lower == "music please") {
            val query = musicMatch?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() } ?: "focus"
            return LocalIntentResult(
                handled = true,
                intent = ConversationalIntent.DEVICE_ACTION,
                replyText = "Music queue primed for \"$query\", $addr. Shall I begin playback?",
                proposedAction = PendingAction(
                    id = "act-${System.currentTimeMillis()}",
                    type = DeviceActionType.PLAY_MUSIC,
                    capability = PermissionCapability.PLAY_MUSIC,
                    title = "Play Ambient Focus Music",
                    detail = "Activate the built-in polyphonic audio engine ($query).",
                    targetValue = query,
                    toggleState = true
                )
            )
        }

        // 11. Set Alarm
        if (Regex("^(?:set\\s+(?:an?\\s+)?alarm|wake\\s+me|set\\s+(?:a\\s+)?timer|create\\s+(?:an?\\s+)?alarm)\\b", RegexOption.IGNORE_CASE).containsMatchIn(lower)) {
            val parsed = parseAlarmRequest(trimmed)
            return LocalIntentResult(
                handled = true,
                intent = ConversationalIntent.DEVICE_ACTION,
                replyText = "Alarm staged for ${parsed.formattedTime}, $addr. Please authorize to arm it.",
                proposedAction = PendingAction(
                    id = "act-${System.currentTimeMillis()}",
                    type = DeviceActionType.SET_ALARM,
                    capability = PermissionCapability.SET_ALARM,
                    title = "Set ${parsed.label}",
                    detail = "Schedule an audible chime for ${parsed.formattedTime}.",
                    targetValue = parsed.formattedTime,
                    numericValue = parsed.minutesFromNow
                )
            )
        }

        return LocalIntentResult(handled = false, intent = detectConversationalIntent(trimmed))
    }
}
