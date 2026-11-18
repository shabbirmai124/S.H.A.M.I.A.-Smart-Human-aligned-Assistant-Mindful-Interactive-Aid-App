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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.shamia.assistant.engine.OpenSourceAiRepository
import com.shamia.assistant.ui.ShamiaUiState
import com.shamia.assistant.ui.theme.CardBorder
import com.shamia.assistant.ui.theme.CardSlate
import com.shamia.assistant.ui.theme.EmeraldOk
import com.shamia.assistant.ui.theme.GraphiteSurface
import com.shamia.assistant.ui.theme.ObsidianVoid
import com.shamia.assistant.ui.theme.SapphireBright
import com.shamia.assistant.ui.theme.SapphirePulse
import com.shamia.assistant.ui.theme.TextPrimary
import com.shamia.assistant.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModelsHubTab(
    state: ShamiaUiState,
    onSelectModel: (String) -> Unit,
    onToggleInstallModel: (String) -> Unit,
    onToggleInstallVoice: (String) -> Unit,
    onToggleResearch: () -> Unit,
    onToggleEnsemble: () -> Unit,
    onToggleEnsembleMember: (String) -> Unit,
    onAddCustomModel: (name: String, repoOrTag: String, minRamGb: Int) -> Unit,
    onSaveEndpointsAndTest: (ollamaUrl: String, serverUrl: String) -> Unit,
    onStartNewChatWithActiveModel: () -> Unit
) {
    var customName by remember { mutableStateOf("") }
    var customRepo by remember { mutableStateOf("") }
    var customRam by remember { mutableStateOf("4") }
    var ollamaInput by remember(state.ollamaEndpoint) { mutableStateOf(state.ollamaEndpoint) }
    var serverInput by remember(state.serverBaseUrl) { mutableStateOf(state.serverBaseUrl) }

    val activeSpec = state.selectedModel

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Active Model Synchronization Banner
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = GraphiteSurface,
                border = BorderStroke(1.dp, SapphirePulse)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "STRICTLY ACTIVE MODEL",
                        style = MaterialTheme.typography.labelSmall,
                        color = SapphireBright,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = activeSpec.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Text(
                        text = "ID: ${activeSpec.id} · Ollama: ${activeSpec.ollamaTag ?: "none"} · Cloud: ${activeSpec.cloudModelId}",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldOk,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onStartNewChatWithActiveModel,
                        colors = ButtonDefaults.buttonColors(containerColor = SapphirePulse, contentColor = ObsidianVoid),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("models_start_new_chat_button")
                    ) {
                        Text("Start New Chat with ${activeSpec.name.substringBefore(" (").substringBefore(" ·")}")
                    }
                }
            }
        }

        // Hardware Profiler & Switches
        item {
            Surface(shape = RoundedCornerShape(14.dp), color = CardSlate, border = BorderStroke(1.dp, CardBorder)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("DEVICE PROFILER", style = MaterialTheme.typography.labelMedium, color = SapphireBright)
                    Text(
                        text = "${state.hardware.deviceLabel} · ${state.hardware.ramGb} GB RAM · ${state.hardware.cpuCores} CPU Cores",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = state.hardware.recommendedTier,
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldOk
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Real-Time Google & Open Web Search", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                            Text("Grounding via Google News RSS + DuckDuckGo + Wikipedia", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = state.useResearch, onCheckedChange = { onToggleResearch() })
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Multi-Model Ensemble (${state.ensembleModelIds.size}/3)", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                            Text("Run up to 3 open models concurrently & compare", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = state.ensembleEnabled, onCheckedChange = { onToggleEnsemble() })
                    }
                }
            }
        }

        // Open-Weight Model Catalog
        item {
            Text(
                text = "OPEN-WEIGHT MODELS (${state.allModels.size})",
                style = MaterialTheme.typography.labelMedium,
                color = SapphireBright,
                fontWeight = FontWeight.Bold
            )
        }

        items(state.allModels, key = { it.id }) { spec ->
            val isActive = spec.id == state.selectedModelId
            val isInstalled = state.installedModelIds.contains(spec.id)
            val inEnsemble = state.ensembleModelIds.contains(spec.id)
            val compatible = state.hardware.ramGb >= spec.minRamGb || spec.isBuiltInOffline

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
                            Text(spec.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text(
                                text = "${spec.provider} · ${spec.params} · ${spec.sizeLabel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SapphireBright
                            )
                        }
                        Text(
                            text = if (compatible) "${spec.minRamGb}GB+ OK" else "Cloud / High RAM",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (compatible) EmeraldOk else TextSecondary
                        )
                    }

                    Text(
                        text = spec.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Text(
                        text = "Ollama: ${spec.ollamaTag ?: "Built-in"} · Cloud ID: ${spec.cloudModelId}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = { onSelectModel(spec.id) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isActive) EmeraldOk else SapphirePulse,
                                contentColor = ObsidianVoid
                            ),
                            modifier = Modifier.testTag("select_model_${spec.id}")
                        ) {
                            Text(if (isActive) "Active" else "Use Model")
                        }

                        if (!spec.isBuiltInOffline) {
                            OutlinedButton(onClick = { onToggleInstallModel(spec.id) }) {
                                Text(if (isInstalled) "Cached" else "Mark Offline")
                            }
                        }

                        OutlinedButton(onClick = { onToggleEnsembleMember(spec.id) }) {
                            Text(if (inEnsemble) "In Mix" else "+ Mix")
                        }
                    }
                }
            }
        }

        // Custom Open-Weight Model Loader
        item {
            Surface(shape = RoundedCornerShape(14.dp), color = CardSlate, border = BorderStroke(1.dp, CardBorder)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("CUSTOM HUGGING FACE / OLLAMA MODEL", style = MaterialTheme.typography.labelMedium, color = SapphireBright)
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text("Display Name (e.g. Qwen 3 1.7B)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = customRepo,
                        onValueChange = { customRepo = it },
                        label = { Text("Ollama tag or HF repo (e.g. qwen3:1.7b)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = customRam,
                        onValueChange = { customRam = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Minimum RAM (GB)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            if (customName.isNotBlank() && customRepo.isNotBlank()) {
                                onAddCustomModel(customName, customRepo, customRam.toIntOrNull() ?: 4)
                                customName = ""
                                customRepo = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Load & Activate Custom Model")
                    }
                }
            }
        }

        // Offline Voice Models (STT / TTS)
        item {
            Text(
                text = "OFFLINE VOICE MODELS (${OpenSourceAiRepository.VOICE_MODELS.size})",
                style = MaterialTheme.typography.labelMedium,
                color = SapphireBright,
                fontWeight = FontWeight.Bold
            )
        }

        items(OpenSourceAiRepository.VOICE_MODELS, key = { it.id }) { voice ->
            val cached = state.installedVoiceIds.contains(voice.id)
            Surface(shape = RoundedCornerShape(12.dp), color = CardSlate, border = BorderStroke(1.dp, CardBorder)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(voice.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text("${voice.kind.name} · ${voice.sizeLabel} · ${voice.hfRepo}", style = MaterialTheme.typography.labelSmall, color = SapphireBright)
                        Text(voice.summary, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(onClick = { onToggleInstallVoice(voice.id) }) {
                        Text(if (cached) "Ready" else "Cache")
                    }
                }
            }
        }

        // Termux Ollama & Paired Server Config
        item {
            Surface(shape = RoundedCornerShape(14.dp), color = CardSlate, border = BorderStroke(1.dp, CardBorder)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("TERMUX OLLAMA & PAIRED SERVER", style = MaterialTheme.typography.labelMedium, color = SapphireBright)
                    OutlinedTextField(
                        value = ollamaInput,
                        onValueChange = { ollamaInput = it },
                        label = { Text("Local Ollama URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = serverInput,
                        onValueChange = { serverInput = it },
                        label = { Text("Optional Paired Server URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { onSaveEndpointsAndTest(ollamaInput, serverInput) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_ollama_button")
                    ) {
                        Text("Save & Test Ollama")
                    }
                    if (state.ollamaTestStatus.isNotBlank()) {
                        Text(
                            text = state.ollamaTestStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = SapphireBright
                        )
                    }
                }
            }
        }
    }
}
