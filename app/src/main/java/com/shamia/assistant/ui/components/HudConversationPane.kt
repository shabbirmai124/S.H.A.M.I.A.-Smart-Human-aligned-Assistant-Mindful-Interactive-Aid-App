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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shamia.assistant.model.AppThemeMode
import com.shamia.assistant.model.CoreVisualState
import com.shamia.assistant.ui.ShamiaUiState
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

@Composable
fun TopCockpitBar(
    state: ShamiaUiState,
    onNewChat: () -> Unit,
    onToggleVoiceReply: () -> Unit,
    onToggleTheme: () -> Unit,
    onToggleVoiceAssistantMode: () -> Unit,
    onOpenIdentity: () -> Unit
) {
    val isLight = state.themeMode == AppThemeMode.LIGHT
    Surface(
        color = GraphiteSurface,
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "S.H.A.M.I.A.",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Hands-Free Voice Assistant Mode Pill
                Surface(
                    onClick = onToggleVoiceAssistantMode,
                    shape = RoundedCornerShape(12.dp),
                    color = if (state.handsFreeVoiceMode) SapphireContainer else CardSlate,
                    border = BorderStroke(
                        1.dp,
                        if (state.handsFreeVoiceMode) SapphirePulse else CardBorder
                    ),
                    modifier = Modifier.testTag("voice_assistant_mode_toggle")
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.RecordVoiceOver,
                            contentDescription = if (state.handsFreeVoiceMode) "Stop Hands-Free Voice Assistant" else "Start Hands-Free Voice Assistant",
                            tint = if (state.handsFreeVoiceMode) SapphireBright else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // 1-Tap Google White / Obsidian Dark Theme Switcher
                Surface(
                    onClick = onToggleTheme,
                    shape = RoundedCornerShape(12.dp),
                    color = if (isLight) SapphireContainer else CardSlate,
                    border = BorderStroke(
                        1.dp,
                        if (isLight) SapphirePulse.copy(alpha = 0.6f) else CardBorder
                    ),
                    modifier = Modifier.testTag("theme_mode_toggle")
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isLight) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                            contentDescription = if (isLight) "Switch to Obsidian Dark Theme" else "Switch to Google White Theme",
                            tint = if (isLight) SapphireBright else SapphirePulse,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Voice Reply Speaker Toggle Pill Button
                Surface(
                    onClick = onToggleVoiceReply,
                    shape = RoundedCornerShape(12.dp),
                    color = CardSlate,
                    border = BorderStroke(
                        1.dp,
                        if (state.voiceReplyEnabled) SapphirePulse.copy(alpha = 0.55f) else CardBorder
                    ),
                    modifier = Modifier.testTag("voice_reply_toggle")
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (state.voiceReplyEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = if (state.voiceReplyEnabled) "Mute spoken responses" else "Unmute spoken responses",
                            tint = if (state.voiceReplyEnabled) SapphireBright else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Owner Profile Button
                Surface(
                    onClick = onOpenIdentity,
                    shape = RoundedCornerShape(12.dp),
                    color = CardSlate,
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.testTag("account_identity_button")
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PersonOutline,
                            contentDescription = "Owner profile settings (${state.identity.fullName})",
                            tint = SapphireBright,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // New Chat / Model Session Button
                Surface(
                    onClick = onNewChat,
                    shape = RoundedCornerShape(12.dp),
                    color = CardSlate,
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.testTag("new_chat_button")
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AddComment,
                            contentDescription = "Start New Chat",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
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
    modelDropdownExpanded: Boolean,
    onToggleModelDropdown: () -> Unit,
    onDismissModelDropdown: () -> Unit,
    onSelectModel: (String) -> Unit,
    inputDraft: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStopGeneration: () -> Unit,
    onToggleWebSearch: () -> Unit,
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
    val lastTextLen = state.messages.lastOrNull()?.text?.length ?: 0
    LaunchedEffect(state.messages.size, lastTextLen) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    val quickChips = listOf(
        "Status Report",
        "Practice English",
        "I Feel Stressed",
        "Set Alarm",
        "Play Music",
        "Plan Study Routine",
        "Compare 4nm & 6nm Phones"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        // Upper Quantum Core + Active Model Pill Bar + Quick Skill Chips (matching screenshot)
        Surface(
            color = GraphiteSurface.copy(alpha = 0.75f),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        QuantumCore(
                            coreState = state.coreState,
                            addressAs = state.identity.addressAs,
                            activeModelName = state.selectedModel.name,
                            compact = true,
                            onTap = onTapCore
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = when {
                                state.liveTranscript.isNotBlank() -> state.liveTranscript
                                state.coreState == CoreVisualState.LISTENING -> "Listening...\nTap to stop"
                                state.isBusyOrSpeaking -> "Active...\nTap to stop"
                                else -> "Tap core to\nspeak"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Active Model Pill Selector matching screenshot: [ ⚙️ GPT-OSS 20B (Ope... ]
                    Box {
                        Surface(
                            onClick = onToggleModelDropdown,
                            shape = RoundedCornerShape(12.dp),
                            color = CardSlate,
                            border = BorderStroke(1.dp, CardBorder),
                            modifier = Modifier.testTag("active_model_dropdown_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Memory,
                                    contentDescription = "Select AI Model",
                                    tint = SapphirePulse,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = state.selectedModel.name.take(18),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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
                                val isSafe = state.isModelSafeForDevice(spec)
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = spec.name,
                                                color = if (isSelected) SapphireBright else TextPrimary,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                            Text(
                                                text = "${spec.sizeLabel} · ${spec.params} · ${if (isSafe) "Local Hardware Ready" else "Needs ${spec.minRamGb} GB RAM"}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isSafe) EmeraldOk else TextSecondary
                                            )
                                        }
                                    },
                                    trailingIcon = {
                                        if (isSelected) {
                                            Icon(Icons.Filled.Check, contentDescription = "Selected", tint = SapphireBright)
                                        }
                                    },
                                    onClick = { onSelectModel(spec.id) }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Quick Skill Pill Row matching screenshot: [ Status Report ] [ Practice English ] [ I Feel Stressed ] [ Set Alarm ] [ Play Music ]
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(quickChips) { chip ->
                        Surface(
                            onClick = { onQuickPrompt(chip) },
                            shape = RoundedCornerShape(12.dp),
                            color = CardSlate,
                            border = BorderStroke(1.dp, CardBorder)
                        ) {
                            Text(
                                text = chip,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                            )
                        }
                    }
                }
            }
        }

        if (state.handsFreeVoiceMode || state.coreState == CoreVisualState.LISTENING) {
            Surface(
                color = SapphireContainer,
                border = BorderStroke(1.dp, SapphirePulse.copy(alpha = 0.65f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.RecordVoiceOver,
                            contentDescription = "Voice Assistant active",
                            tint = SapphireBright,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (state.handsFreeVoiceMode) "Hands-Free Voice Assistant ON" else "Listening for your voice...",
                                style = MaterialTheme.typography.labelMedium,
                                color = SapphireBright,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = state.liveTranscript.ifBlank {
                                    when (state.coreState) {
                                        CoreVisualState.SPEAKING -> "Speaking reply — will listen again when done"
                                        CoreVisualState.THINKING -> "Processing your voice command..."
                                        else -> "Speak naturally or tap Stop to end"
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Surface(
                        onClick = onStopGeneration,
                        shape = RoundedCornerShape(10.dp),
                        color = RoseDeny.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, RoseDeny),
                        modifier = Modifier.testTag("voice_banner_stop_button")
                    ) {
                        Text(
                            text = "Stop",
                            style = MaterialTheme.typography.labelMedium,
                            color = RoseDeny,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
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

        // Conversation Message Stream
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
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

        // Bottom Voice & Text Composer Bar matching screenshot: [ 🎤 ] [ 🌐 ] [ Message S.H.A.M.I.A., mam... ] [ ➤ / ⏹ ]
        Surface(
            color = GraphiteSurface,
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Microphone Button
                Surface(
                    onClick = onTapCore,
                    shape = RoundedCornerShape(14.dp),
                    color = if (state.coreState == CoreVisualState.LISTENING) RoseDeny.copy(alpha = 0.2f) else CardSlate,
                    border = BorderStroke(
                        1.dp,
                        if (state.coreState == CoreVisualState.LISTENING) RoseDeny else CardBorder
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("mic_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (state.coreState == CoreVisualState.LISTENING) Icons.Filled.MicOff else Icons.Filled.Mic,
                            contentDescription = "Voice microphone input",
                            tint = if (state.coreState == CoreVisualState.LISTENING) RoseDeny else SapphirePulse,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // 2. Globe / Web Search Toggle Button (lets user choose when to use web search or stay 100% local offline!)
                Surface(
                    onClick = onToggleWebSearch,
                    shape = RoundedCornerShape(14.dp),
                    color = if (state.useResearch) SapphireContainer else CardSlate,
                    border = BorderStroke(
                        1.dp,
                        if (state.useResearch) SapphirePulse else CardBorder
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("composer_web_search_toggle")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Language,
                            contentDescription = if (state.useResearch) "Web Search Enabled (Tap to turn off)" else "Local Offline Mode (Tap to enable Web Search)",
                            tint = if (state.useResearch) SapphireBright else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // 3. Rounded Message Input Field
                OutlinedTextField(
                    value = inputDraft,
                    onValueChange = onInputChange,
                    placeholder = {
                        Text(
                            text = "Message S.H.A.M.I.A., ${state.identity.addressAs}...",
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardSlate,
                        unfocusedContainerColor = CardSlate,
                        focusedBorderColor = SapphirePulse,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input_field")
                )

                // 4. Stop Button (when generating or speaking) OR Send Button (when idle)
                if (state.isBusyOrSpeaking && inputDraft.isBlank()) {
                    Surface(
                        onClick = onStopGeneration,
                        shape = RoundedCornerShape(14.dp),
                        color = RoseDeny.copy(alpha = 0.22f),
                        border = BorderStroke(1.dp, RoseDeny),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("stop_generation_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Stop,
                                contentDescription = "Stop current response",
                                tint = RoseDeny,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                } else {
                    Surface(
                        onClick = onSend,
                        shape = RoundedCornerShape(14.dp),
                        color = SapphirePulse.copy(alpha = 0.22f),
                        border = BorderStroke(1.dp, SapphirePulse.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("send_message_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send command or prompt",
                                tint = SapphireBright,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
