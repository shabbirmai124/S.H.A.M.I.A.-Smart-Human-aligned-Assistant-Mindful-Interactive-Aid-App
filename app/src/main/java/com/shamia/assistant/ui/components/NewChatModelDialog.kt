package com.shamia.assistant.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shamia.assistant.model.OpenSourceModelSpec
import com.shamia.assistant.ui.theme.AmberWarn
import com.shamia.assistant.ui.theme.CardBorder
import com.shamia.assistant.ui.theme.CardSlate
import com.shamia.assistant.ui.theme.EmeraldOk
import com.shamia.assistant.ui.theme.GraphiteSurface
import com.shamia.assistant.ui.theme.ObsidianVoid
import com.shamia.assistant.ui.theme.SapphireBright
import com.shamia.assistant.ui.theme.SapphirePulse
import com.shamia.assistant.ui.theme.TextPrimary
import com.shamia.assistant.ui.theme.TextSecondary

@Composable
fun NewChatModelDialog(
    models: List<OpenSourceModelSpec>,
    selectedModelId: String,
    isSafeForDevice: (OpenSourceModelSpec) -> Boolean,
    onDismiss: () -> Unit,
    onStartWithModel: (String) -> Unit
) {
    val activeSpec = models.find { it.id == selectedModelId } ?: models.firstOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSlate,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AddComment, contentDescription = null, tint = SapphireBright)
                Spacer(modifier = Modifier.width(8.dp))
                Text("New Chat · Pick Model", color = TextPrimary)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Choose which model should power your new isolated conversation:",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(models, key = { it.id }) { spec ->
                        val isCurrent = spec.id == selectedModelId
                        val safe = isSafeForDevice(spec)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = GraphiteSurface,
                            border = BorderStroke(1.dp, if (isCurrent) SapphirePulse else CardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("new_chat_pick_${spec.id}")
                                .clickable { onStartWithModel(spec.id) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Memory,
                                        contentDescription = null,
                                        tint = if (isCurrent) EmeraldOk else SapphireBright,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = spec.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "${spec.ollamaTag ?: spec.cloudModelId} · ${spec.params} · ${if (safe) "Safe" else "High RAM"}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (safe) EmeraldOk else AmberWarn
                                        )
                                    }
                                }
                                if (isCurrent) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Current model",
                                        tint = EmeraldOk,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (activeSpec != null) {
                Button(
                    onClick = { onStartWithModel(activeSpec.id) },
                    colors = ButtonDefaults.buttonColors(containerColor = SapphirePulse, contentColor = ObsidianVoid),
                    modifier = Modifier.testTag("new_chat_same_model_button")
                ) {
                    Text("Use Current (${activeSpec.name.substringBefore(" (").substringBefore(" ·")})")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
