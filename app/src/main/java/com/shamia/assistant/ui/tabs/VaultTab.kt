package com.shamia.assistant.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shamia.assistant.model.PermissionCapability
import com.shamia.assistant.model.PermissionPolicy
import com.shamia.assistant.ui.ShamiaUiState
import com.shamia.assistant.ui.theme.CardBorder
import com.shamia.assistant.ui.theme.CardSlate
import com.shamia.assistant.ui.theme.EmeraldOk
import com.shamia.assistant.ui.theme.GraphiteSurface
import com.shamia.assistant.ui.theme.RoseDeny
import com.shamia.assistant.ui.theme.SapphireBright
import com.shamia.assistant.ui.theme.SapphireContainer
import com.shamia.assistant.ui.theme.SapphirePulse
import com.shamia.assistant.ui.theme.TextPrimary
import com.shamia.assistant.ui.theme.TextSecondary

@Composable
fun VaultTab(
    state: ShamiaUiState,
    onSetPermission: (PermissionCapability, PermissionPolicy) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Every sensitive phone capability honors your boundary below and never executes without your consent.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }

        items(PermissionCapability.entries, key = { it.name }) { cap ->
            val current = state.permissions[cap] ?: PermissionPolicy.ASK
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CardSlate,
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = cap.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = cap.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = GraphiteSurface,
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PermissionPolicy.entries.forEach { policy ->
                                val selected = current == policy
                                val label = when (policy) {
                                    PermissionPolicy.ASK -> "Ask"
                                    PermissionPolicy.ALWAYS -> "Allow"
                                    PermissionPolicy.DENY -> "Deny"
                                }
                                val activeFill = when (policy) {
                                    PermissionPolicy.ASK -> SapphireContainer
                                    PermissionPolicy.ALWAYS -> EmeraldOk.copy(alpha = 0.16f)
                                    PermissionPolicy.DENY -> RoseDeny.copy(alpha = 0.16f)
                                }
                                val activeBorder = when (policy) {
                                    PermissionPolicy.ASK -> SapphirePulse
                                    PermissionPolicy.ALWAYS -> EmeraldOk
                                    PermissionPolicy.DENY -> RoseDeny
                                }
                                val activeText = when (policy) {
                                    PermissionPolicy.ASK -> SapphireBright
                                    PermissionPolicy.ALWAYS -> EmeraldOk
                                    PermissionPolicy.DENY -> RoseDeny
                                }

                                Surface(
                                    onClick = { onSetPermission(cap, policy) },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selected) activeFill else GraphiteSurface,
                                    border = if (selected) BorderStroke(1.dp, activeBorder) else null,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("vault_${cap.name.lowercase()}_${policy.name.lowercase()}")
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (selected) activeText else TextSecondary,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 10.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
