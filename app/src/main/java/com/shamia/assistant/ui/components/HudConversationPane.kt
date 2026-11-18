package com.shamia.assistant.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shamia.assistant.model.CoreVisualState
import com.shamia.assistant.ui.ShamiaUiState
import com.shamia.assistant.ui.theme.CardBorder
import com.shamia.assistant.ui.theme.CardSlate
import com.shamia.assistant.ui.theme.EmeraldOk
import com.shamia.assistant.ui.theme.GraphiteSurface
import com.shamia.assistant.ui.theme.RoseDeny
import com.shamia.assistant.ui.theme.SapphireBright
import com.shamia.assistant.ui.theme.SapphirePulse
import com.shamia.assistant.ui.theme.TextPrimary
import com.shamia.assistant.ui.theme.TextSecondary

@Composable
fun TopCockpitBar(
    state: ShamiaUiState,
    modelDropdownExpanded: Boolean,
    onToggleModelDropdown: () -> Unit,
    onDismissModelDropdown: () -> Unit,
    onSelectModel: (String) -> Unit,
    onNewChat: () -> Unit,
    onToggleVoiceReply: () -> Unit,
    onOpenIdentity: () -> Unit
) {
    Surface(
        color = GraphiteSurface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "S.H.A.M.I.A.",
                        style = MaterialTheme.typography.titleMedium,
                        color = SapphireBright,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${state.clockLabel} · ${if (state.telemetry.isOnline) "Online" else "Offline"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    state.telemetry.batteryPct?.let { pct ->
                        Icon(
                            imageVector = Icons.Filled.BatteryChargingFull,
                            contentDescription = "Battery telemetry",
                            tint = if (state.telemetry.isCharging) EmeraldOk else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "$pct%",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                    IconButton(
                        onClick = onNewChat,
                        modifier = Modifier.testTag("new_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AddComment,
                            contentDescription = "Start New Chat",
                            tint = SapphireBright
                        )
                    }
                    IconButton(
                        onClick = onToggleVoiceReply,
                        modifier = Modifier.testTag("voice_reply_toggle")
                    ) {
                        Icon(
                            imageVector = if (state.voiceReplyEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = if (state.voiceReplyEnabled) "Mute spoken responses" else "Unmute spoken responses",
                            tint = if (state.voiceReplyEnabled) EmeraldOk else RoseDeny
                        )
                    }
                    IconButton(
                        onClick = onOpenIdentity,
                        modifier = Modifier.testTag("account_identity_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = "Owner profile settings",
                            tint = TextPrimary
                        )
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Surface(
                    onClick = onToggleModelDropdown,
                    shape = RoundedCornerShape(10.dp),
                    color = CardSlate,
                    border = BorderStroke(1.dp, SapphirePulse.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("active_model_dropdown_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Model: ${state.selectedModel.name} (${state.selectedModel.ollamaTag ?: state.selectedModel.cloudModelId})",
                            style = MaterialTheme.typography.labelMedium,
                            color = SapphireBright,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = "Select AI Model",
                            tint = SapphirePulse
                        )
                    }
                }

                DropdownMenu(
                    expanded = modelDropdownExpanded,
                    onDismissRequest = onDismissModelDropdown,
                    modifier = Modifier.background(CardSlate)
                ) {
                    state.allModels.forEach { spec ->
                        val isSelected = spec.id == state.selectedModelId
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = spec.name,
                                        color = if (isSelected) EmeraldOk else TextPrimary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Text(
                                        text = "${spec.ollamaTag ?: spec.cloudModelId} · ${spec.params}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                            },
                            trailingIcon = {
                                if (isSelected) {
                                    Icon(Icons.Filled.Check, contentDescription = "Selected", tint = EmeraldOk)
                                }
                            },
                            onClick = { onSelectModel(spec.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HudConversationPane(
    state: ShamiaUiState,
    compactCore: Boolean,
    inputDraft: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onQuickPrompt: (String) -> Unit,
    onTapCore: () -> Unit,
    onAuthorizeOnce: (String, String?) -> Unit,
    onAlwaysAllow: (String, String?) -> Unit,
    onDeny: (String) -> Unit,
    onSpeakText: (String) -> Unit,
    onTogglePlayPause: () -> Unit,
    onNextTrack: () -> Unit,
    onStopMusic: () -> Unit,
    onVolumeChange: (Float) -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    val quickChips = listOf(
        "Practice spoken English",
        "Status report",
        "I am feeling stressed",
        "Plan my study routine",
        "Compare 4nm & 6nm phones"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        if (compactCore) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuantumCore(
                    coreState = state.coreState,
                    addressAs = state.identity.addressAs,
                    activeModelName = state.selectedModel.name,
                    compact = true,
                    onTap = onTapCore
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Active: ${state.selectedModel.name}",
                        style = MaterialTheme.typography.labelMedium,
                        color = SapphireBright,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = state.liveTranscript.ifBlank { "Tap the core orb or type below." },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                QuantumCore(
                    coreState = state.coreState,
                    addressAs = state.identity.addressAs,
                    activeModelName = state.selectedModel.name,
                    compact = false,
                    onTap = onTapCore
                )
            }
        }

        if (state.musicActive) {
            MediaBar(
                track = state.activeTrack,
                isPlaying = state.musicPlaying,
                volume = state.musicVolume,
                onTogglePlayPause = onTogglePlayPause,
                onNextTrack = onNextTrack,
                onStop = onStopMusic,
                onVolumeChange = onVolumeChange,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(quickChips) { chip ->
                AssistChip(
                    onClick = { onQuickPrompt(chip) },
                    label = { Text(chip, style = MaterialTheme.typography.labelMedium) }
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.messages, key = { it.id }) { msg ->
                HudMessageCard(
                    message = msg,
                    onAuthorizeOnce = onAuthorizeOnce,
                    onAlwaysAllow = onAlwaysAllow,
                    onDeny = onDeny,
                    onSpeakText = onSpeakText
                )
            }
        }

        Surface(
            color = GraphiteSurface.copy(alpha = 0.96f),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onTapCore,
                    modifier = Modifier.testTag("mic_button")
                ) {
                    Icon(
                        imageVector = if (state.coreState == CoreVisualState.LISTENING) Icons.Filled.MicOff else Icons.Filled.Mic,
                        contentDescription = "Voice microphone input",
                        tint = if (state.coreState == CoreVisualState.LISTENING) RoseDeny else SapphirePulse
                    )
                }
                OutlinedTextField(
                    value = inputDraft,
                    onValueChange = onInputChange,
                    placeholder = { Text("Ask ${state.selectedModel.name.substringBefore(" ·")}...") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input_field")
                )
                IconButton(
                    onClick = onSend,
                    modifier = Modifier.testTag("send_message_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send command or prompt",
                        tint = SapphireBright
                    )
                }
            }
        }
    }
}
