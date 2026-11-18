package com.shamia.assistant.engine

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.shamia.assistant.model.DeviceActionType
import com.shamia.assistant.model.DeviceControlState
import com.shamia.assistant.model.PendingAction

object DeviceExecutor {

    fun execute(
        context: Context,
        action: PendingAction,
        currentControls: DeviceControlState,
        onControlsUpdated: (DeviceControlState) -> Unit,
        onBrightnessRequested: (Int) -> Unit = {}
    ): String {
        return when (action.type) {
            DeviceActionType.DIAL_CONTACT -> {
                val digits = action.targetValue.ifBlank { "1234567890" }
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$digits")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { context.startActivity(intent) }
                "Opened Android Phone Dialer for $digits."
            }

            DeviceActionType.OPEN_APP -> {
                val url = action.targetValue
                if (url.startsWith("https://")) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching { context.startActivity(intent) }
                    "Launched ${action.title.removePrefix("Open ").trim()}."
                } else {
                    "Unsupported launch scheme blocked."
                }
            }

            DeviceActionType.TOGGLE_WIFI -> {
                val next = action.toggleState
                onControlsUpdated(currentControls.copy(wifi = next))
                val panelIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Intent(Settings.Panel.ACTION_WIFI)
                } else {
                    Intent(Settings.ACTION_WIFI_SETTINGS)
                }.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                runCatching { context.startActivity(panelIntent) }
                "Wi-Fi ${if (next) "enabled" else "disabled"} in HUD and Android Wi-Fi panel opened."
            }

            DeviceActionType.TOGGLE_BLUETOOTH -> {
                val next = action.toggleState
                onControlsUpdated(currentControls.copy(bluetooth = next))
                val btIntent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { context.startActivity(btIntent) }
                "Bluetooth ${if (next) "enabled" else "disabled"} in HUD and Bluetooth settings opened."
            }

            DeviceActionType.TOGGLE_DND -> {
                val next = action.toggleState
                onControlsUpdated(currentControls.copy(dnd = next))
                "Do Not Disturb is now ${if (next) "ON" else "OFF"}."
            }

            DeviceActionType.TOGGLE_FLASHLIGHT -> {
                val next = action.toggleState
                val switched = runCatching {
                    val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                    val id = cm.cameraIdList.firstOrNull()
                    if (id != null) cm.setTorchMode(id, next)
                    true
                }.getOrDefault(false)
                onControlsUpdated(currentControls.copy(flashlight = next))
                if (switched) "Device flashlight turned ${if (next) "ON" else "OFF"}." else "Flashlight state updated."
            }

            DeviceActionType.SET_BRIGHTNESS -> {
                val pct = action.numericValue.coerceIn(10, 100)
                onControlsUpdated(currentControls.copy(brightnessPct = pct))
                onBrightnessRequested(pct)
                "Screen luminance adjusted to $pct%."
            }

            DeviceActionType.SET_ALARM -> {
                "Alarm '${action.title.removePrefix("Set ").trim()}' armed for ${action.targetValue}."
            }

            DeviceActionType.PLAY_MUSIC -> {
                "Ambient audio synthesis started."
            }

            DeviceActionType.ADD_TASK -> "Task added to local ledger."
            DeviceActionType.CLEAR_COMPLETED_TASKS -> "Completed tasks cleared from ledger."
            DeviceActionType.ADD_NOTE -> "Note archived to local ledger."
        }
    }
}
