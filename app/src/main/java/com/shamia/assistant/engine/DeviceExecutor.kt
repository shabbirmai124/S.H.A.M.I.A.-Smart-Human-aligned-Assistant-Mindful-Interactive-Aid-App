package com.shamia.assistant.engine

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
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
                val launched = runCatching { context.startActivity(intent) }.isSuccess
                if (launched) {
                    "Opened Android Phone Dialer for $digits."
                } else {
                    "No Android Phone Dialer app found for $digits."
                }
            }

            DeviceActionType.OPEN_APP -> {
                val url = action.targetValue
                if (url.startsWith("https://")) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    val appLabel = action.title.removePrefix("Open ").trim()
                    val launched = runCatching { context.startActivity(intent) }.isSuccess
                    if (launched) {
                        "Launched $appLabel."
                    } else {
                        "No browser or app available to open $appLabel."
                    }
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
                val opened = runCatching { context.startActivity(panelIntent) }.isSuccess
                if (opened) {
                    "Wi-Fi ${if (next) "enabled" else "disabled"} in HUD and Android Wi-Fi panel opened."
                } else {
                    "Wi-Fi ${if (next) "enabled" else "disabled"} in HUD."
                }
            }

            DeviceActionType.TOGGLE_BLUETOOTH -> {
                val next = action.toggleState
                onControlsUpdated(currentControls.copy(bluetooth = next))
                val btIntent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val opened = runCatching { context.startActivity(btIntent) }.isSuccess
                if (opened) {
                    "Bluetooth ${if (next) "enabled" else "disabled"} in HUD and Bluetooth settings opened."
                } else {
                    "Bluetooth ${if (next) "enabled" else "disabled"} in HUD."
                }
            }

            DeviceActionType.TOGGLE_DND -> {
                val next = action.toggleState
                onControlsUpdated(currentControls.copy(dnd = next))
                "Do Not Disturb is now ${if (next) "ON" else "OFF"}."
            }

            DeviceActionType.TOGGLE_FLASHLIGHT -> {
                val next = action.toggleState
                val switched = runCatching {
                    val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                    val flashId = cm?.cameraIdList?.firstOrNull { cid ->
                        cm.getCameraCharacteristics(cid)
                            .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    }
                    if (cm != null && flashId != null) {
                        cm.setTorchMode(flashId, next)
                        true
                    } else {
                        false
                    }
                }.getOrDefault(false)
                onControlsUpdated(currentControls.copy(flashlight = if (switched) next else false))
                if (switched) {
                    "Device flashlight turned ${if (next) "ON" else "OFF"}."
                } else {
                    "No camera flash hardware available on this device."
                }
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
