package com.shamia.assistant.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shamia.assistant.R
import com.shamia.assistant.model.AppThemeMode
import com.shamia.assistant.ui.components.AccountIdentityDialog
import com.shamia.assistant.ui.components.HudConversationPane
import com.shamia.assistant.ui.components.NewChatModelDialog
import com.shamia.assistant.ui.components.TopCockpitBar
import com.shamia.assistant.ui.tabs.ModelsHubTab
import com.shamia.assistant.ui.tabs.PhoneAndVaultTab
import com.shamia.assistant.ui.tabs.PlannerTab
import com.shamia.assistant.ui.tabs.VaultTab
import com.shamia.assistant.ui.theme.CardSlate
import com.shamia.assistant.ui.theme.EmeraldOk
import com.shamia.assistant.ui.theme.GraphiteSurface
import com.shamia.assistant.ui.theme.ObsidianVoid
import com.shamia.assistant.ui.theme.RoseDeny
import com.shamia.assistant.ui.theme.SapphirePulse
import com.shamia.assistant.ui.theme.TextPrimary
import com.shamia.assistant.ui.theme.TextSecondary

enum class WorkspaceTab(val label: String) {
    HUD("HUD"),
    PLANNER("Tasks"),
    PHONE("Phone"),
    MODELS("Models"),
    VAULT("Vault")
}

@Composable
fun ShamiaHudScreen(
    vm: ShamiaViewModel,
    onRequestMicPermissionAndListen: () -> Unit,
    onRequestHandsFreeVoiceMode: () -> Unit
) {
    val state by vm.ui.collectAsStateWithLifecycle()
    var currentTab by remember { mutableStateOf(WorkspaceTab.HUD) }
    var showIdentityDialog by remember { mutableStateOf(false) }
    var showNewChatDialog by remember { mutableStateOf(false) }
    var inputDraft by remember { mutableStateOf("") }
    var modelDropdownExpanded by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val isLight = state.themeMode == AppThemeMode.LIGHT

    BackHandler(enabled = currentTab != WorkspaceTab.HUD || state.screenTorchActive) {
        if (state.screenTorchActive) {
            vm.turnOffScreenTorch()
        } else {
            currentTab = WorkspaceTab.HUD
        }
    }

    LaunchedEffect(state.statusBanner) {
        val msg = state.statusBanner
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            vm.clearBanner()
        }
    }

    // Immediate in-place Permission Approval Dialog when tapping DND, Torch, Wi-Fi, Bluetooth, or Brightness in Phone tab
    state.pendingModalAction?.let { (msgId, action) ->
        AlertDialog(
            onDismissRequest = { vm.denyAction(msgId) },
            containerColor = CardSlate,
            title = {
                Text(
                    text = action.title,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "${action.detail}\n\nPermission Vault requires confirmation for ${action.capability.label}.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { vm.alwaysAllowAction(msgId, null) },
                        border = BorderStroke(1.dp, SapphirePulse),
                        modifier = Modifier.testTag("modal_always_allow_button")
                    ) {
                        Text("Always Allow", color = SapphirePulse)
                    }
                    Button(
                        onClick = { vm.approveActionOnce(msgId, null) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldOk,
                            contentColor = ObsidianVoid
                        ),
                        modifier = Modifier.testTag("modal_authorize_once_button")
                    ) {
                        Text("Allow Once")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { vm.denyAction(msgId) },
                    modifier = Modifier.testTag("modal_deny_button")
                ) {
                    Text("Deny", color = RoseDeny)
                }
            }
        )
    }

    if (showNewChatDialog) {
        NewChatModelDialog(
            models = state.allModels,
            selectedModelId = state.selectedModelId,
            isSafeForDevice = state::isModelSafeForDevice,
            onDismiss = { showNewChatDialog = false },
            onStartWithModel = { chosenId ->
                vm.startNewChatWithModel(chosenId)
                showNewChatDialog = false
                currentTab = WorkspaceTab.HUD
            }
        )
    }

    if (showIdentityDialog) {
        AccountIdentityDialog(
            identity = state.identity,
            onDismiss = { showIdentityDialog = false },
            onSave = { name, addr ->
                vm.updateIdentity(name, addr)
                showIdentityDialog = false
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianVoid)
    ) {
        // Show subtle obsidian texture only in Dark Mode; keep Google White pure, clean & bright!
        if (!isLight) {
            Image(
                painter = painterResource(id = R.drawable.img_obsidian_backdrop),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.12f,
                modifier = Modifier.fillMaxSize()
            )
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWideTablet = maxWidth >= 840.dp

            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = {
                    SnackbarHost(hostState = snackbarHostState) { data ->
                        androidx.compose.material3.Snackbar(
                            snackbarData = data,
                            containerColor = CardSlate,
                            contentColor = TextPrimary,
                            actionColor = SapphirePulse
                        )
                    }
                },
                topBar = {
                    TopCockpitBar(
                        state = state,
                        onNewChat = { showNewChatDialog = true },
                        onToggleVoiceReply = vm::toggleVoiceReply,
                        onToggleTheme = vm::toggleThemeMode,
                        onToggleVoiceAssistantMode = {
                            currentTab = WorkspaceTab.HUD
                            onRequestHandsFreeVoiceMode()
                        },
                        onOpenIdentity = { showIdentityDialog = true }
                    )
                },
                bottomBar = {
                    if (!isWideTablet) {
                        val pendingTasks = state.tasks.count { !it.completed }
                        NavigationBar(containerColor = GraphiteSurface) {
                            NavigationBarItem(
                                selected = currentTab == WorkspaceTab.HUD,
                                onClick = { currentTab = WorkspaceTab.HUD },
                                icon = { Icon(Icons.Filled.Hub, contentDescription = "HUD Cockpit") },
                                label = { Text("HUD") }
                            )
                            NavigationBarItem(
                                selected = currentTab == WorkspaceTab.PLANNER,
                                onClick = { currentTab = WorkspaceTab.PLANNER },
                                icon = { Icon(Icons.Filled.DateRange, contentDescription = "Tasks and Planner") },
                                label = { Text("Tasks ($pendingTasks)") }
                            )
                            NavigationBarItem(
                                selected = currentTab == WorkspaceTab.PHONE,
                                onClick = { currentTab = WorkspaceTab.PHONE },
                                icon = { Icon(Icons.Filled.SettingsInputComponent, contentDescription = "Phone Controls") },
                                label = { Text("Phone") }
                            )
                            NavigationBarItem(
                                selected = currentTab == WorkspaceTab.MODELS,
                                onClick = { currentTab = WorkspaceTab.MODELS },
                                icon = { Icon(Icons.Filled.Memory, contentDescription = "Models Hub") },
                                label = { Text("Models") }
                            )
                            NavigationBarItem(
                                selected = currentTab == WorkspaceTab.VAULT,
                                onClick = { currentTab = WorkspaceTab.VAULT },
                                icon = { Icon(Icons.Filled.Shield, contentDescription = "Permission Vault") },
                                label = { Text("Vault") }
                            )
                        }
                    }
                }
            ) { innerPadding ->
                if (isWideTablet) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(0.56f)
                                .fillMaxHeight()
                        ) {
                            HudConversationPane(
                                state = state,
                                compactCore = true,
                                modelDropdownExpanded = modelDropdownExpanded,
                                onToggleModelDropdown = { modelDropdownExpanded = !modelDropdownExpanded },
                                onDismissModelDropdown = { modelDropdownExpanded = false },
                                onSelectModel = { id ->
                                    vm.selectModel(id)
                                    modelDropdownExpanded = false
                                },
                                inputDraft = inputDraft,
                                onInputChange = { inputDraft = it },
                                onSend = {
                                    if (inputDraft.isNotBlank()) {
                                        vm.sendMessage(inputDraft)
                                        inputDraft = ""
                                    }
                                },
                                onStopGeneration = vm::stopGeneration,
                                onToggleWebSearch = vm::toggleWebResearch,
                                onQuickPrompt = vm::sendMessage,
                                onTapCore = onRequestMicPermissionAndListen,
                                onAuthorizeOnce = vm::approveActionOnce,
                                onAlwaysAllow = vm::alwaysAllowAction,
                                onDeny = vm::denyAction,
                                onSpeakText = { vm.voiceEngine.speak(it) },
                                onTogglePlayPause = vm::toggleMusicPlayPause,
                                onNextTrack = vm::nextAmbientTrack,
                                onStopMusic = vm::stopMusic,
                                onVolumeChange = vm::setMusicVolume
                            )
                        }

                        val sideTab = if (currentTab == WorkspaceTab.HUD) WorkspaceTab.MODELS else currentTab
                        val pendingTasks = state.tasks.count { !it.completed }
                        Column(
                            modifier = Modifier
                                .weight(0.44f)
                                .fillMaxHeight()
                                .background(GraphiteSurface.copy(alpha = if (isLight) 1f else 0.72f))
                        ) {
                            val sideTabs = listOf(WorkspaceTab.PLANNER, WorkspaceTab.PHONE, WorkspaceTab.MODELS, WorkspaceTab.VAULT)
                            TabRow(
                                selectedTabIndex = sideTabs.indexOf(sideTab).coerceAtLeast(0),
                                containerColor = GraphiteSurface,
                                contentColor = SapphirePulse
                            ) {
                                sideTabs.forEach { tab ->
                                    Tab(
                                        selected = sideTab == tab,
                                        onClick = { currentTab = tab },
                                        text = {
                                            Text(if (tab == WorkspaceTab.PLANNER) "Tasks ($pendingTasks)" else tab.label)
                                        }
                                    )
                                }
                            }
                            WorkspaceTabContent(
                                tab = sideTab,
                                state = state,
                                vm = vm,
                                onSwitchToHud = { currentTab = WorkspaceTab.HUD }
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (currentTab) {
                            WorkspaceTab.HUD -> HudConversationPane(
                                state = state,
                                compactCore = true,
                                modelDropdownExpanded = modelDropdownExpanded,
                                onToggleModelDropdown = { modelDropdownExpanded = !modelDropdownExpanded },
                                onDismissModelDropdown = { modelDropdownExpanded = false },
                                onSelectModel = { id ->
                                    vm.selectModel(id)
                                    modelDropdownExpanded = false
                                },
                                inputDraft = inputDraft,
                                onInputChange = { inputDraft = it },
                                onSend = {
                                    if (inputDraft.isNotBlank()) {
                                        vm.sendMessage(inputDraft)
                                        inputDraft = ""
                                    }
                                },
                                onStopGeneration = vm::stopGeneration,
                                onToggleWebSearch = vm::toggleWebResearch,
                                onQuickPrompt = vm::sendMessage,
                                onTapCore = onRequestMicPermissionAndListen,
                                onAuthorizeOnce = vm::approveActionOnce,
                                onAlwaysAllow = vm::alwaysAllowAction,
                                onDeny = vm::denyAction,
                                onSpeakText = { vm.voiceEngine.speak(it) },
                                onTogglePlayPause = vm::toggleMusicPlayPause,
                                onNextTrack = vm::nextAmbientTrack,
                                onStopMusic = vm::stopMusic,
                                onVolumeChange = vm::setMusicVolume
                            )
                            else -> WorkspaceTabContent(
                                tab = currentTab,
                                state = state,
                                vm = vm,
                                onSwitchToHud = { currentTab = WorkspaceTab.HUD }
                            )
                        }
                    }
                }
            }
        }

        // Full-Brightness Pure-White Screen Torch Fallback (for devices/emulators without a physical rear flash LED)
        if (state.screenTorchActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White),
                contentAlignment = Alignment.BottomCenter
            ) {
                Button(
                    onClick = vm::turnOffScreenTorch,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF202124),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .padding(bottom = 56.dp)
                        .testTag("turn_off_screen_torch_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.FlashlightOff,
                        contentDescription = "Turn off torch",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Turn Off Torch", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun WorkspaceTabContent(
    tab: WorkspaceTab,
    state: ShamiaUiState,
    vm: ShamiaViewModel,
    onSwitchToHud: () -> Unit
) {
    when (tab) {
        WorkspaceTab.MODELS -> ModelsHubTab(
            state = state,
            onSelectModel = vm::selectModel,
            onNewChatWithModel = { chosenId ->
                vm.startNewChatWithModel(chosenId)
                onSwitchToHud()
            },
            onToggleInstallModel = vm::toggleInstallModel,
            onPauseModelDownload = vm::pauseModelDownload,
            onClearPartialDownload = vm::clearPartialDownload,
            onPreloadModel = vm::preloadModelIntoRam,
            onImportLocalModel = vm::importLocalModelFile,
            onToggleInstallVoice = vm::toggleInstallVoiceModel,
            onSelectVoiceGender = vm::setVoiceGender,
            onSelectThemeMode = vm::setThemeMode,
            onToggleInteractionSound = vm::toggleInteractionSound,
            onToggleResearch = vm::toggleWebResearch,
            onToggleEnsemble = vm::toggleEnsembleEnabled,
            onToggleEnsembleMember = vm::toggleEnsembleMember,
            onToggleShowAllModels = vm::toggleShowAllModels,
            onAddCustomModel = vm::addCustomModel,
            onSaveEndpointsAndTest = vm::updateEndpointsAndTest,
            onStartNewChatWithActiveModel = {
                vm.startNewChat()
                onSwitchToHud()
            }
        )
        WorkspaceTab.PLANNER -> PlannerTab(
            state = state,
            onAddRoutine = vm::addRoutine,
            onToggleRoutine = vm::toggleRoutine,
            onDeleteRoutine = vm::deleteRoutine,
            onAddSyllabus = vm::addSyllabus,
            onToggleSyllabus = vm::toggleSyllabus,
            onDeleteSyllabus = vm::deleteSyllabus,
            onBuildStudyPlan = {
                vm.requestStudyPlanFromRoutineAndSyllabus()
                onSwitchToHud()
            },
            onAddTask = vm::addTask,
            onToggleTask = vm::toggleTask,
            onDeleteTask = vm::deleteTask,
            onClearCompletedTasks = vm::clearCompletedTasks,
            onAddNote = vm::addNote,
            onDeleteNote = vm::deleteNote
        )
        WorkspaceTab.PHONE, WorkspaceTab.HUD -> PhoneAndVaultTab(
            state = state,
            nowMillis = state.nowMillis,
            onStageAction = { action, promptText ->
                vm.stageOrExecuteAction(action, promptText, showModalOnAsk = true)
            },
            onSendCommand = { cmd ->
                vm.sendMessage(cmd)
                onSwitchToHud()
            },
            onPlayTrack = vm::playAmbientTrack,
            onDismissAlarm = vm::dismissAlarm,
            onSetPermission = vm::setPermissionPolicy
        )
        WorkspaceTab.VAULT -> VaultTab(
            state = state,
            onSetPermission = vm::setPermissionPolicy
        )
    }
}
