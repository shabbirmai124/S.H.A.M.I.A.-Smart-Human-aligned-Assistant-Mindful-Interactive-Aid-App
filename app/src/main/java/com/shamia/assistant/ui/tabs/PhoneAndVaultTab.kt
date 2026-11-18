package com.shamia.assistant.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.shamia.assistant.ui.theme.CardBorder
import com.shamia.assistant.ui.theme.CardSlate
import com.shamia.assistant.ui.theme.EmeraldOk
import com.shamia.assistant.ui.theme.ObsidianVoid
import com.shamia.assistant.ui.theme.RoseDeny
import com.shamia.assistant.ui.theme.SapphireBright
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
        // Hardware Toggles
        item {
            Surface(shape = RoundedCornerShape(14.dp), color = CardSlate, border = BorderStroke(1.dp, CardBorder)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("DEVICE CONTROL DECK", style = MaterialTheme.typography.labelMedium, color = SapphireBright, fontWeight = FontWeight.Bold)
                    ControlSwitchRow("Wi-Fi Radio", state.controls.wifi) { next ->
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
                    ControlSwitchRow("Bluetooth", state.controls.bluetooth) { next ->
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
                    ControlSwitchRow("Do Not Disturb", state.controls.dnd) { next ->
                        onStageAction(
                            PendingAction(
                                id = "ui-dnd-${System.currentTimeMillis()}",
                                type = DeviceActionType.TOGGLE_DND,
                                capability = PermissionCapability.DEVICE_CONTROL,
                                title = "${if (next) "Enable" else "Disable"} DND",
                                detail = "Update silence preference.",
                                toggleState = next
                            ),
                            "DND toggle staged, $addr."
                        )
                    }
                    ControlSwitchRow("Rear Camera Torch", state.controls.flashlight) { next ->
                        onStageAction(
                            PendingAction(
                                id = "ui-torch-${System.currentTimeMillis()}",
                                type = DeviceActionType.TOGGLE_FLASHLIGHT,
                                capability = PermissionCapability.DEVICE_CONTROL,
                                title = "${if (next) "Enable" else "Disable"} Flashlight",
                                detail = "Toggle device camera LED beam.",
                                toggleState = next
                            ),
                            "Flashlight toggle staged, $addr."
                        )
                    }

                    Text(
                        text = "Display Luminance: ${brightnessSlider.toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                    Slider(
                        value = brightnessSlider,
                        onValueChange = { brightnessSlider = it },
                        onValueChangeFinished = {
                            val pct = brightnessSlider.toInt().coerceIn(25, 100)
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
                        valueRange = 25f..100f,
                        colors = SliderDefaults.colors(thumbColor = SapphirePulse, activeTrackColor = SapphirePulse),
                        modifier = Modifier.testTag("brightness_slider")
                    )
                }
            }
        }

        // Quick Dialer & App Launcher
        item {
            Surface(shape = RoundedCornerShape(14.dp), color = CardSlate, border = BorderStroke(1.dp, CardBorder)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("QUICK DIALER & VERIFIED APPS", style = MaterialTheme.typography.labelMedium, color = SapphireBright, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = dialNumber,
                            onValueChange = { dialNumber = it },
                            label = { Text("Phone Number or Contact") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (dialNumber.isNotBlank()) {
                                    onSendCommand("Call $dialNumber")
                                    dialNumber = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SapphirePulse, contentColor = ObsidianVoid)
                        ) {
                            Text("Call")
                        }
                    }

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Spotify", "YouTube", "Google Maps", "Gmail", "Calendar", "GitHub").forEach { app ->
                            OutlinedButton(onClick = { onSendCommand("Open $app") }) {
                                Text(app)
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

        // Permission Vault
        item {
            Text(
                text = "PERMISSION VAULT (ZERO-SURPRISE GOVERNANCE)",
                style = MaterialTheme.typography.labelMedium,
                color = SapphireBright,
                fontWeight = FontWeight.Bold
            )
        }

        items(PermissionCapability.entries, key = { it.name }) { cap ->
            val current = state.permissions[cap] ?: PermissionPolicy.ASK
            Surface(shape = RoundedCornerShape(12.dp), color = CardSlate, border = BorderStroke(1.dp, CardBorder)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(cap.label, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Text(cap.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        PermissionPolicy.entries.forEach { policy ->
                            FilterChip(
                                selected = current == policy,
                                onClick = { onSetPermission(cap, policy) },
                                label = { Text(policy.name) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlSwitchRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}
