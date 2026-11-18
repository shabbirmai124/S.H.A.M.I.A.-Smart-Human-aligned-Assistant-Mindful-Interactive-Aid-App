package com.shamia.assistant.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shamia.assistant.model.ActionStatus
import com.shamia.assistant.model.DeviceActionType
import com.shamia.assistant.model.HudMessage
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
fun HudMessageCard(
    message: HudMessage,
    onAuthorizeOnce: (messageId: String, phoneOverride: String?) -> Unit,
    onAlwaysAllow: (messageId: String, phoneOverride: String?) -> Unit,
    onDeny: (messageId: String) -> Unit,
    onSpeakText: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bg = if (message.isUser) SapphireContainer else CardSlate
    val border = if (message.isUser) SapphirePulse.copy(alpha = 0.40f) else CardBorder

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = bg,
        border = BorderStroke(1.dp, border)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header row: role + active model + timestamp + Speak button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (message.isUser) "YOU" else "S.H.A.M.I.A.",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (message.isUser) SapphireBright else EmeraldOk,
                        fontWeight = FontWeight.Bold
                    )
                    if (!message.isUser && !message.modelLabel.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SapphirePulse.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = message.modelLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = SapphireBright,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = message.timestampLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    if (!message.isUser) {
                        IconButton(
                            onClick = { onSpeakText(message.text) },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("speak_message_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Speak message aloud",
                                tint = SapphirePulse,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Runtime Model Verification Telemetry Strip
            message.executionMeta?.let { meta ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = GraphiteSurface,
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 4.dp)
                ) {
                    Text(
                        text = "Model Verified: ${meta.executedModelId} · Engine: ${meta.providerEngine} · Cache Bypassed",
                        style = MaterialTheme.typography.labelSmall,
                        color = SapphireBright,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary
            )

            // Natural English Coaching Tip
            message.coachingTip?.let { tip ->
                if (!tip.naturalPhrase.isNullOrBlank() || !tip.tip.isNullOrBlank()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = GraphiteSurface,
                        border = BorderStroke(1.dp, SapphirePulse.copy(alpha = 0.35f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "NATURAL ENGLISH TIP",
                                style = MaterialTheme.typography.labelSmall,
                                color = SapphireBright,
                                fontWeight = FontWeight.Bold
                            )
                            if (!tip.naturalPhrase.isNullOrBlank()) {
                                Text(
                                    text = "\"${tip.naturalPhrase}\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = EmeraldOk,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            if (!tip.tip.isNullOrBlank()) {
                                Text(
                                    text = tip.tip,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Interactive Breathing Guide
            if (message.showBreathingGuide) {
                Spacer(modifier = Modifier.height(10.dp))
                BreathingGuide()
            }

            // Real-Time Web Search Grounding Links
            if (message.groundingLinks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "REAL-TIME SOURCES",
                    style = MaterialTheme.typography.labelSmall,
                    color = SapphireBright
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    message.groundingLinks.forEach { link ->
                        AssistChip(
                            onClick = {
                                if (link.uri.startsWith("https://") || link.uri.startsWith("http://")) {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.uri)))
                                    }
                                }
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "Open source link",
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = link.title.take(34),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }
            }

            // Permission-Gated Device Action Card (Authorize Once / Always Allow / Deny)
            message.pendingAction?.let { action ->
                Spacer(modifier = Modifier.height(10.dp))
                var phoneInput by remember(action.id) { mutableStateOf(action.targetValue) }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = GraphiteSurface,
                    border = BorderStroke(
                        1.dp,
                        when (action.status) {
                            ActionStatus.PENDING -> AmberWarn.copy(alpha = 0.65f)
                            ActionStatus.APPROVED -> EmeraldOk.copy(alpha = 0.65f)
                            else -> RoseDeny.copy(alpha = 0.55f)
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = action.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = action.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 2.dp)
                        )

                        if (action.status == ActionStatus.PENDING) {
                            if (action.type == DeviceActionType.DIAL_CONTACT && action.targetValue.isBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = phoneInput,
                                    onValueChange = { phoneInput = it },
                                    label = { Text("Enter digits to dial") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("dial_phone_override_input")
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Button(
                                    onClick = { onAuthorizeOnce(message.id, phoneInput.takeIf { it.isNotBlank() }) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = EmeraldOk,
                                        contentColor = ObsidianVoid
                                    ),
                                    modifier = Modifier.testTag("authorize_once_button")
                                ) {
                                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Authorize Once")
                                }
                                OutlinedButton(
                                    onClick = { onAlwaysAllow(message.id, phoneInput.takeIf { it.isNotBlank() }) },
                                    border = BorderStroke(1.dp, SapphirePulse),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SapphirePulse),
                                    modifier = Modifier.testTag("always_allow_button")
                                ) {
                                    Icon(Icons.Filled.VerifiedUser, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Always Allow")
                                }
                                OutlinedButton(
                                    onClick = { onDeny(message.id) },
                                    border = BorderStroke(1.dp, RoseDeny),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RoseDeny),
                                    modifier = Modifier.testTag("deny_action_button")
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Deny")
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "STATUS: ${action.status.name}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (action.status == ActionStatus.APPROVED) EmeraldOk else RoseDeny,
                                    fontWeight = FontWeight.Bold
                                )
                                if (action.status == ActionStatus.APPROVED) {
                                    if (action.type == DeviceActionType.DIAL_CONTACT && action.targetValue.isNotBlank()) {
                                        TextButton(onClick = {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:${action.targetValue}"))
                                                )
                                            }
                                        }) {
                                            Icon(Icons.Filled.Call, contentDescription = "Open dialer", modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Open Dialer")
                                        }
                                    } else if (action.type == DeviceActionType.OPEN_APP && action.targetValue.startsWith("https://")) {
                                        TextButton(onClick = {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_VIEW, Uri.parse(action.targetValue))
                                                )
                                            }
                                        }) {
                                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Launch app", modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Launch")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
