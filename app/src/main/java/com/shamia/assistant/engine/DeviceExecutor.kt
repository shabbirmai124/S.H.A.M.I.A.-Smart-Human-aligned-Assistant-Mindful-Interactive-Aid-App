package com.shamia.assistant.engine

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.shamia.assistant.model.DeviceActionType
import com.shamia.assistant.model.DeviceControlState
import com.shamia.assistant.model.PendingAction

object DeviceExecutor {

    private const val TAG = "ShamiaDeviceExecutor"

    /**
     * Reads real Android Do Not Disturb status so the HUD tile reflects actual OS state.
     */
    fun readSystemDndEnabled(context: Context): Boolean? {
        return runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!nm.isNotificationPolicyAccessGranted) return@runCatching null
            nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        }.getOrNull()
    }

    /**
     * Finds the camera ID that actually owns a physical flash LED (preferring rear camera).
     */
    private fun findFlashCameraId(cm: CameraManager): String? {
        var fallbackFlashId: String? = null
        for (id in cm.cameraIdList) {
            val chars = runCatching { cm.getCameraCharacteristics(id) }.getOrNull() ?: continue
            val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            if (!hasFlash) continue
            val facing = chars.get(CameraCharacteristics.LENS_FACING)
            if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                return id
            }
            if (fallbackFlashId == null) {
                fallbackFlashId = id
            }
        }
        return fallbackFlashId
    }

    fun execute(
        context: Context,
        action: PendingAction,
        currentControls: DeviceControlState,
        onControlsUpdated: (DeviceControlState) -> Unit,
        onBrightnessRequested: (Int) -> Unit = {},
        onScreenTorchRequested: (Boolean) -> Unit = {}
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
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (!nm.isNotificationPolicyAccessGranted) {
                    val settingsIntent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching { context.startActivity(settingsIntent) }
                    "Grant 'Do Not Disturb' access to S.H.A.M.I.A. in the Settings screen that just opened, then tap DND again."
                } else {
                    val applied = runCatching {
                        nm.setInterruptionFilter(
                            if (next) NotificationManager.INTERRUPTION_FILTER_PRIORITY
                            else NotificationManager.INTERRUPTION_FILTER_ALL
                        )
                        true
                    }.onFailure {
                        Log.e(TAG, "Failed to update Android DND interruption filter", it)
                    }.getOrDefault(false)

                    if (applied) {
                        onControlsUpdated(currentControls.copy(dnd = next))
                        "Android Do Not Disturb turned ${if (next) "ON" else "OFF"}."
                    } else {
                        "Could not change Do Not Disturb mode on this Android device."
                    }
                }
            }

            DeviceActionType.TOGGLE_FLASHLIGHT -> {
                val next = action.toggleState
                val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val flashId = runCatching { findFlashCameraId(cm) }.getOrNull()
                if (flashId != null) {
                    val switched = runCatching {
                        cm.setTorchMode(flashId, next)
                        true
                    }.onFailure {
                        Log.e(TAG, "setTorchMode failed for camera $flashId", it)
                    }.getOrDefault(false)

                    if (switched) {
                        onScreenTorchRequested(false)
                        onControlsUpdated(currentControls.copy(flashlight = next))
                        "Device rear LED torch turned ${if (next) "ON" else "OFF"}."
                    } else {
                        // Camera flash busy or restricted — fall back to high-luminance Screen Torch
                        onScreenTorchRequested(next)
                        onControlsUpdated(currentControls.copy(flashlight = next))
                        if (next) "Hardware LED busy — activated full-brightness Screen Torch." else "Screen Torch turned OFF."
                    }
                } else {
                    // Device/emulator has no physical rear flash LED — provide real full-brightness Screen Torch
                    onScreenTorchRequested(next)
                    onControlsUpdated(currentControls.copy(flashlight = next))
                    if (next) "No rear flash LED detected — activated full-brightness Screen Torch." else "Screen Torch turned OFF."
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
