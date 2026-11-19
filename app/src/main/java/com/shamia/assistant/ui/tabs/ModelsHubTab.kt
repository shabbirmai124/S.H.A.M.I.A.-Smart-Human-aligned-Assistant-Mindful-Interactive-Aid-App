package com.shamia.assistant.ui.tabs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shamia.assistant.engine.ModelCatalog
import com.shamia.assistant.model.VoiceGender
import com.shamia.assistant.ui.ShamiaUiState
import com.shamia.assistant.ui.theme.AmberWarn
import com.shamia.assistant.ui.theme.CardBorder
import com.shamia.assistant.ui.theme.CardSlate
import com.shamia.assistant.ui.theme.EmeraldOk
import com.shamia.assistant.ui.theme.GraphiteSurface
import com.shamia.assistant.ui.theme.ObsidianVoid
import com.shamia.assistant.ui.theme.SapphireBright
import com.shamia.assistant.ui.theme.SapphireContainer
import com.shamia.assistant.ui.theme.SapphirePulse
import com.shamia.assistant.ui.theme.TextPrimary
import com.shamia.assistant.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModelsHubTab(
    state: ShamiaUiState,
    onSelectModel: (String) -> Unit,
    onNewChatWithModel: (String) -> Unit,
    onToggleInstallModel: (String) -> Unit,
    onImportLocalModel: (String, Uri) -> Unit,
    onToggleInstallVoice: (String) -> Unit,
    onSelectVoiceGender: (VoiceGender) -> Unit,
    onToggleInteractionSound: () -> Unit,
    onToggleResearch: () -> Unit,
    onToggleEnsemble: () -> Unit,
    onToggleEnsembleMember: (String) -> Unit,
    onToggleShowAllModels: () -> Unit,
    onAddCustomModel: (name: String, repoOrTag: String, minRamGb: Int) -> Unit,
    onSaveEndpointsAndTest: (ollamaUrl: String, serverUrl: String) -> Unit,
    onStartNewChatWithActiveModel: () -> Unit
) {
    val activeSpec = state.selectedModel
    var pendingImportModelId by remember { mutableStateOf<String?>(null) }
    val importTaskLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val targetId = pendingImportModelId
        pendingImportModelId = null
        if (uri != null && targetId != null) {
            onImportLocalModel(targetId, uri)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Device Safety & Hardware · Local Hardware Compatibility
        item {
            Surface(shape = RoundedCornerShape(14.dp), color = CardSlate, border = BorderStroke(1.dp, CardBorder)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.PhoneAndroid, contentDescription = null, tint = SapphireBright, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Local Phone Hardware Profiler", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.VerifiedUser, contentDescription = "OOM Protected", tint = EmeraldOk, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("100% Local Ready", style = MaterialTheme.typography.labelSmall, color = EmeraldOk, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Text(
                        text = "Device: ${state.hardware.deviceLabel} · RAM: ${state.hardware.ramGb} GB (${state.hardware.availRamMb} MB Free) · CPU Cores: ${state.hardware.cpuCores}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        // 2. Clear Natural Voice (Female (Clear) / Male (Clear))
        item {
            Surface(shape = RoundedCornerShape(14.dp), color = CardSlate, border = BorderStroke(1.dp, CardBorder)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(0.42f)) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = SapphireBright, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear Natural Voice", style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Medium)
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = GraphiteSurface,
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.weight(0.58f)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            VoiceGender.entries.forEach { gender ->
                                val selected = state.voiceGender == gender
                                Surface(
                                    onClick = { onSelectVoiceGender(gender) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selected) SapphirePulse else GraphiteSurface,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("voice_gender_${gender.name.lowercase()}")
                                ) {
                                    Text(
                                        text = gender.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) ObsidianVoid else TextSecondary,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Voice Interaction Sound Effects Toggle
        item {
            Surface(shape = RoundedCornerShape(14.dp), color = CardSlate, border = BorderStroke(1.dp, CardBorder)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = SapphireBright, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Voice Interaction Sound FX", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                            Text("Audible chimes on mic listen, send, reply & stop", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                    }
                    Surface(
                        onClick = onToggleInteractionSound,
                        shape = RoundedCornerShape(8.dp),
                        color = if (state.interactionSoundEnabled) SapphireContainer else GraphiteSurface,
                        border = BorderStroke(1.dp, if (state.interactionSoundEnabled) SapphirePulse else CardBorder),
                        modifier = Modifier.testTag("toggle_interaction_sound_pill")
                    ) {
                        Text(
                            text = if (state.interactionSoundEnabled) "Enabled" else "Off",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (state.interactionSoundEnabled) SapphireBright else TextSecondary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // 4. Optional Live Web Search Pill Card
        item {
            Surface(shape = RoundedCornerShape(14.dp), color = CardSlate, border = BorderStroke(1.dp, CardBorder)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Language, contentDescription = null, tint = SapphireBright, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Optional Web Search Grounding", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                            Text("Toggle anytime here or via the 🌐 button in chat", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                    }
                    Surface(
                        onClick = onToggleResearch,
                        shape = RoundedCornerShape(8.dp),
                        color = if (state.useResearch) SapphireContainer else GraphiteSurface,
                        border = BorderStroke(1.dp, if (state.useResearch) SapphirePulse else CardBorder),
                        modifier = Modifier.testTag("toggle_research_pill")
                    ) {
                        Text(
                            text = if (state.useResearch) "Enabled" else "Local Only",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (state.useResearch) SapphireBright else TextSecondary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // 5. Multi-Model Ensemble (3) Pill Card
        item {
            Surface(shape = RoundedCornerShape(14.dp), color = CardSlate, border = BorderStroke(1.dp, CardBorder)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Layers, contentDescription = null, tint = SapphireBright, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Multi-Model Ensemble (${state.ensembleModelIds.size})", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    }
                    Surface(
                        onClick = onToggleEnsemble,
                        shape = RoundedCornerShape(8.dp),
                        color = if (state.ensembleEnabled) SapphireContainer else GraphiteSurface,
                        border = BorderStroke(1.dp, if (state.ensembleEnabled) SapphirePulse else CardBorder),
                        modifier = Modifier.testTag("toggle_ensemble_pill")
                    ) {
                        Text(
                            text = if (state.ensembleEnabled) "On" else "Off",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (state.ensembleEnabled) SapphireBright else TextSecondary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // Active Model Banner + New Chat Quick Action
        item {
            Surface(shape = RoundedCornerShape(14.dp), color = GraphiteSurface, border = BorderStroke(1.dp, SapphirePulse.copy(alpha = 0.6f))) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "ACTIVE LOCAL MODEL: ${activeSpec.name} (${activeSpec.sizeLabel})",
                        style = MaterialTheme.typography.labelMedium,
                        color = SapphireBright,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${activeSpec.params} · Min RAM: ${activeSpec.minRamGb} GB · Official Source: ${activeSpec.hfRepo ?: activeSpec.ollamaTag}",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldOk
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onStartNewChatWithActiveModel,
                        colors = ButtonDefaults.buttonColors(containerColor = SapphirePulse, contentColor = ObsidianVoid),
                        modifier = Modifier.fillMaxWidth().testTag("models_start_new_chat_button")
                    ) {
                        Text("New Chat with ${activeSpec.name.substringBefore(" ·").substringBefore(" (")}", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // 6. Compatible Models (N) + Show All Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (state.showAllModels) "All Open-Weight Models (${state.allModels.size})" else "Hardware-Compatible Models (${state.compatibleModels.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = onToggleShowAllModels, modifier = Modifier.testTag("toggle_show_all_models")) {
                    Text(if (state.showAllModels) "Compatible Only" else "Show All (${state.allModels.size})", color = SapphireBright)
                }
            }
        }

        // 7. Model Cards with explicit GB/MB download size, Safe for Device, Use/Active, Download Offline
        items(state.displayedModels, key = { it.id }) { spec ->
            val isActive = spec.id == state.selectedModelId
            val isInstalled = state.installedModelIds.contains(spec.id)
            val inEnsemble = state.ensembleModelIds.contains(spec.id)
            val safe = state.isModelSafeForDevice(spec)

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CardSlate,
                border = BorderStroke(1.dp, if (isActive) SapphirePulse else CardBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            val isRecommended = spec.id == state.recommendedModelId
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Memory, contentDescription = null, tint = SapphireBright, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(spec.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            }
                            Text(
                                text = buildString {
                                    append(spec.sizeLabel)
                                    append(" · ")
                                    append(spec.params)
                                    append(" · ")
                                    append(if (safe) "Compatible (${spec.minRamGb} GB+ RAM)" else "Needs ${spec.minRamGb} GB RAM")
                                    if (isRecommended) append(" · Recommended")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (safe) EmeraldOk else AmberWarn,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                            if (spec.summary.isNotBlank()) {
                                Text(
                                    text = spec.summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { onSelectModel(spec.id) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isActive) SapphirePulse else GraphiteSurface,
                                contentColor = if (isActive) ObsidianVoid else TextPrimary
                            ),
                            border = if (isActive) null else BorderStroke(1.dp, CardBorder),
                            modifier = Modifier.testTag("select_model_${spec.id}")
                        ) {
                            Text(if (isActive) "Active" else "Use")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = { onNewChatWithModel(spec.id) },
                            border = BorderStroke(1.dp, SapphirePulse.copy(alpha = 0.65f)),
                            modifier = Modifier.testTag("new_chat_model_${spec.id}")
                        ) {
                            Text("New Chat", color = SapphireBright)
                        }
                        if (spec.supportsOfflineInstall) {
                            val downloadInfo = state.modelDownloadProgress[spec.id]
                            OutlinedButton(
                                onClick = { if (downloadInfo == null) onToggleInstallModel(spec.id) },
                                border = BorderStroke(1.dp, if (isInstalled) EmeraldOk else CardBorder),
                                modifier = Modifier.testTag("install_offline_${spec.id}")
                            ) {
                                Icon(
                                    imageVector = if (isInstalled) Icons.Filled.DownloadDone else Icons.Filled.Download,
                                    contentDescription = null,
                                    tint = if (isInstalled) EmeraldOk else TextPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = when {
                                        downloadInfo != null -> "Downloading ${downloadInfo.first}%"
                                        isInstalled -> "Loaded Offline (${spec.sizeLabel.substringBefore(" ")})"
                                        else -> "Download (${spec.sizeLabel})"
                                    },
                                    color = if (isInstalled) EmeraldOk else TextPrimary
                                )
                            }
                            if (!isInstalled && downloadInfo == null) {
                                OutlinedButton(
                                    onClick = {
                                        pendingImportModelId = spec.id
                                        importTaskLauncher.launch(arrayOf("*/*"))
                                    },
                                    border = BorderStroke(1.dp, CardBorder),
                                    modifier = Modifier.testTag("import_local_${spec.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.FolderOpen,
                                        contentDescription = "Import local .task weights",
                                        tint = SapphireBright,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Import .task", color = SapphireBright)
                                }
                            }
                        }
                        if (state.ensembleEnabled) {
                            OutlinedButton(onClick = { onToggleEnsembleMember(spec.id) }) {
                                Text(if (inEnsemble) "In Mix" else "+ Mix")
                            }
                        }
                    }
                    state.modelDownloadProgress[spec.id]?.let { (pct, statusText) ->
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { (pct.coerceIn(0, 100)) / 100f },
                            color = SapphirePulse,
                            trackColor = GraphiteSurface,
                            modifier = Modifier.fillMaxWidth().height(5.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$statusText ($pct%)",
                            style = MaterialTheme.typography.labelSmall,
                            color = SapphireBright
                        )
                    }
                }
            }
        }

        // 8. Offline Voice Models (STT & TTS) with Download Sizes
        item {
            Text(
                text = "Offline Voice Models (STT & TTS)",
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        items(ModelCatalog.VOICE_MODELS, key = { it.id }) { voice ->
            val cached = state.installedVoiceIds.contains(voice.id)
            val dl = state.voiceDownloadProgress[voice.id]
            Surface(shape = RoundedCornerShape(12.dp), color = CardSlate, border = BorderStroke(1.dp, CardBorder)) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(voice.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text(
                                text = "${voice.sizeLabel} · ${voice.hfRepo}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (cached) EmeraldOk else TextSecondary
                            )
                        }
                        OutlinedButton(
                            onClick = { if (dl == null) onToggleInstallVoice(voice.id) },
                            border = BorderStroke(1.dp, if (cached) EmeraldOk else CardBorder),
                            modifier = Modifier.testTag("install_voice_${voice.id}")
                        ) {
                            Text(
                                text = when {
                                    dl != null -> "${dl.first}%"
                                    cached -> "Installed"
                                    else -> "Download (${voice.sizeLabel.substringBefore(" ")})"
                                },
                                color = if (cached) EmeraldOk else TextPrimary
                            )
                        }
                    }
                    if (dl != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { (dl.first.coerceIn(0, 100)) / 100f },
                            color = SapphirePulse,
                            trackColor = GraphiteSurface,
                            modifier = Modifier.fillMaxWidth().height(4.dp)
                        )
                        Text(dl.second, style = MaterialTheme.typography.labelSmall, color = SapphireBright, modifier = Modifier.padding(top = 3.dp))
                    }
                }
            }
        }

        // 9. Add Custom HF Model
        item {
            AddCustomHfModelCard(onAddCustomModel = onAddCustomModel)
        }

        // 10. Local Ollama Endpoint
        item {
            LocalOllamaEndpointCard(
                ollamaEndpoint = state.ollamaEndpoint,
                serverBaseUrl = state.serverBaseUrl,
                testStatus = state.ollamaTestStatus,
                onSaveEndpointsAndTest = onSaveEndpointsAndTest
            )
        }
    }
}
