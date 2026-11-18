package com.shamia.assistant.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shamia.assistant.engine.OpenSourceAiRepository
import com.shamia.assistant.engine.RoutineSyllabusStore
import com.shamia.assistant.model.DeviceActionType
import com.shamia.assistant.model.PendingAction
import com.shamia.assistant.model.PermissionCapability
import com.shamia.assistant.model.PermissionPolicy
import com.shamia.assistant.ui.ShamiaUiState
import com.shamia.assistant.ui.theme.AmberWarn
import com.shamia.assistant.ui.theme.CardBorder
import com.shamia.assistant.ui.theme.CardSlate
import com.shamia.assistant.ui.theme.EmeraldOk
import com.shamia.assistant.ui.theme.GraphiteSurface
import com.shamia.assistant.ui.theme.ObsidianVoid
import com.shamia.assistant.ui.theme.RoseDeny
import com.shamia.assistant.ui.theme.SapphireBright
import com.shamia.assistant.ui.theme.SapphireContainer
import com.shamia.assistant.ui.theme.SapphirePulse
import com.shamia.assistant.ui.theme.TextPrimary
import com.shamia.assistant.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhoneAndVaultTab(
    state: ShamiaUiState,
    nowMillis: Long,
    onStageAction: (PendingAction, String) -> Unit,
    onSendCommand: (String) -> Unit,
    onPlayTrack: (String) -> Unit,
    onDismissAlarm: (String) -> Unit,
    onSetPermission: (PermissionCapability, PermissionPolicy) -> Unit
) {
    var dialNumber by remember { mutableStateOf("") }
    var brightnessSlider by remember(state.controls.brightnessPct) {
        mutableFloatStateOf(state.controls.brightnessPct.toFloat())
    }
    val addr = state.identity.addressAs.ifBlank { "Sir" }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Phone Controls (Permission-Gated) 2x2 Grid + Display Brightness
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Phone Controls (Permission-Gated)",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PhoneToggleTile(
                        icon = Icons.Filled.Wifi,
                        label = "Wi-Fi",
                        active = state.controls.wifi,
                        accentColor = SapphirePulse,
                        modifier = Modifier.weight(1f).testTag("toggle_wifi_tile")
                    ) {
                        val next = !state.controls.wifi
                        onStageAction(
                            PendingAction(
                                id = "ui-wifi-${System.currentTimeMillis()}",
                                type = DeviceActionType.TOGGLE_WIFI,
                                capability = PermissionCapability.DEVICE_CONTROL,
                                title = "${if (next) "Enable" else "Disable"} Wi-Fi",
                                detail = "Open Android Wi-Fi panel.",
                                toggleState = next
                            ),
                            "Wi-Fi toggle staged, $addr."
                        )
                    }
                    PhoneToggleTile(
                        icon = Icons.Filled.Bluetooth,
                        label = "Bluetooth",
                        active = state.controls.bluetooth,
                        accentColor = SapphirePulse,
                        modifier = Modifier.weight(1f).testTag("toggle_bluetooth_tile")
                    ) {
                        val next = !state.controls.bluetooth
                        onStageAction(
                            PendingAction(
                                id = "ui-bt-${System.currentTimeMillis()}",
                                type = DeviceActionType.TOGGLE_BLUETOOTH,
                                capability = PermissionCapability.DEVICE_CONTROL,
                                title = "${if (next) "Enable" else "Disable"} Bluetooth",
                                detail = "Open Android Bluetooth settings.",
                                toggleState = next
                            ),
                            "Bluetooth toggle staged, $addr."
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PhoneToggleTile(
                        icon = Icons.Filled.NotificationsOff,
                        label = "DND",
                        active = state.controls.dnd,
                        accentColor = AmberWarn,
                        modifier = Modifier.weight(1f).testTag("toggle_dnd_tile")
                    ) {
                        val next = !state.controls.dnd
                        onStageAction(
                            PendingAction(
                                id = "ui-dnd-${System.currentTimeMillis()}",
                                type = DeviceActionType.TOGGLE_DND,
                                capability = PermissionCapability.DEVICE_CONTROL,
                                title = "${if (next) "Enable" else "Disable"} DND",
                                detail = "Update Do Not Disturb mode.",
                                toggleState = next
                            ),
                            "DND toggle staged, $addr."
                        )
                    }
                    PhoneToggleTile(
                        icon = Icons.Filled.FlashlightOn,
                        label = "Torch",
                        active = state.controls.flashlight,
                        accentColor = AmberWarn,
                        modifier = Modifier.weight(1f).testTag("toggle_torch_tile")
                    ) {
                        val next = !state.controls.flashlight
                        onStageAction(
                            PendingAction(
                                id = "ui-torch-${System.currentTimeMillis()}",
                                type = DeviceActionType.TOGGLE_FLASHLIGHT,
                                capability = PermissionCapability.DEVICE_CONTROL,
                                title = "${if (next) "Enable" else "Disable"} Torch",
                                detail = "Toggle device camera LED beam.",
                                toggleState = next
                            ),
                            "Torch toggle staged, $addr."
                        )
                    }
                }

                // Display Brightness Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = CardSlate,
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.WbSunny,
                                    contentDescription = null,
                                    tint = SapphireBright,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Display Brightness",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = "${brightnessSlider.toInt()}%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SapphireBright,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = brightnessSlider,
                            onValueChange = { brightnessSlider = it },
                            onValueChangeFinished = {
                                val pct = brightnessSlider.toInt().coerceIn(10, 100)
                                onStageAction(
                                    PendingAction(
                                        id = "ui-bri-${System.currentTimeMillis()}",
                                        type = DeviceActionType.SET_BRIGHTNESS,
                                        capability = PermissionCapability.DEVICE_CONTROL,
                                        title = "Set Brightness to $pct%",
                                        detail = "Adjust window luminance to $pct%.",
                                        numericValue = pct
                                    ),
                                    "Brightness change to $pct% staged, $addr."
                                )
                            },
                            valueRange = 10f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = SapphirePulse,
                                activeTrackColor = SapphirePulse,
                                inactiveTrackColor = GraphiteSurface
                            ),
                            modifier = Modifier.testTag("brightness_slider")
                        )
                    }
                }
            }
        }

        // 2. Quick Dialer
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Quick Dialer",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = dialNumber,
                        onValueChange = { dialNumber = it },
                        placeholder = { Text("Enter phone number...", color = TextSecondary) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).testTag("quick_dialer_input")
                    )
                    Surface(
                        onClick = {
                            if (dialNumber.isNotBlank()) {
                                onSendCommand("Call $dialNumber")
                                dialNumber = ""
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = SapphireContainer,
                        border = BorderStroke(1.dp, SapphirePulse.copy(alpha = 0.55f)),
                        modifier = Modifier.testTag("quick_dialer_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Call,
                                contentDescription = "Dial",
                                tint = SapphireBright,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Dial",
                                style = MaterialTheme.typography.labelLarge,
                                color = SapphireBright,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // 3. App Launcher
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "App Launcher",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf("Spotify", "YouTube Music", "Google Maps", "Gmail", "Google Calendar").forEach { app ->
                        Surface(
                            onClick = { onSendCommand("Open $app") },
                            shape = RoundedCornerShape(12.dp),
                            color = CardSlate,
                            border = BorderStroke(1.dp, CardBorder),
                            modifier = Modifier.testTag("launch_app_${app.lowercase().replace(" ", "_")}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = app,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "Launch $app",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Ambient Audio & Alarms
        item {
            Surface(shape = RoundedCornerShape(14.dp), color = CardSlate, border = BorderStroke(1.dp, CardBorder)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("POLYPHONIC FOCUS AUDIO & ALARMS", style = MaterialTheme.typography.labelMedium, color = SapphireBright, fontWeight = FontWeight.Bold)
                    OpenSourceAiRepository.AMBIENT_TRACKS.forEach { track ->
                        val active = state.activeTrackId == track.id
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(track.title, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                Text("${track.mood} · ${track.bpm} BPM", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            }
                            Button(
                                onClick = { onPlayTrack(track.id) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (active) EmeraldOk else SapphirePulse,
                                    contentColor = ObsidianVoid
                                )
                            ) {
                                Text(if (active) "Stop" else "Play")
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { onSendCommand("Set an alarm in 5 minutes") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Arm Quick 5-Minute Focus Timer")
                    }
                }
            }
        }

        items(state.alarms, key = { it.id }) { alarm ->
            Surface(shape = RoundedCornerShape(12.dp), color = CardSlate, border = BorderStroke(1.dp, CardBorder)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(alarm.label, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = if (alarm.active) {
                                "Remaining: ${RoutineSyllabusStore.formatCountdown(alarm.triggerAtMillis, nowMillis)}"
                            } else {
                                "Completed"
                            },
                            color = if (alarm.active) SapphireBright else EmeraldOk,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    OutlinedButton(onClick = { onDismissAlarm(alarm.id) }) {
                        Text("Dismiss", color = RoseDeny)
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneToggleTile(
    icon: ImageVector,
    label: String,
    active: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (active) accentColor.copy(alpha = 0.14f) else CardSlate,
        border = BorderStroke(1.dp, if (active) accentColor.copy(alpha = 0.65f) else CardBorder),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (active) accentColor else TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = if (active) "On" else "Off",
                style = MaterialTheme.typography.bodySmall,
                color = if (active) TextPrimary else TextSecondary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
