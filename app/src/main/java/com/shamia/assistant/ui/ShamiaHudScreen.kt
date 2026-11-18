package com.shamia.assistant.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shamia.assistant.R
import com.shamia.assistant.ui.components.AccountIdentityDialog
import com.shamia.assistant.ui.components.HudConversationPane
import com.shamia.assistant.ui.components.TopCockpitBar
import com.shamia.assistant.ui.tabs.ModelsHubTab
import com.shamia.assistant.ui.tabs.PhoneAndVaultTab
import com.shamia.assistant.ui.tabs.PlannerTab
import com.shamia.assistant.ui.theme.GraphiteSurface
import com.shamia.assistant.ui.theme.ObsidianVoid
import com.shamia.assistant.ui.theme.SapphirePulse

enum class WorkspaceTab(val label: String) {
    HUD("HUD"),
    MODELS("Models"),
    PLANNER("Planner"),
    PHONE("Controls")
}

@Composable
fun ShamiaHudScreen(
    vm: ShamiaViewModel,
    onRequestMicPermissionAndListen: () -> Unit
) {
    val state by vm.ui.collectAsStateWithLifecycle()
    var currentTab by remember { mutableStateOf(WorkspaceTab.HUD) }
    var showIdentityDialog by remember { mutableStateOf(false) }
    var inputDraft by remember { mutableStateOf("") }
    var modelDropdownExpanded by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler(enabled = currentTab != WorkspaceTab.HUD) {
        currentTab = WorkspaceTab.HUD
    }

    LaunchedEffect(state.statusBanner) {
        val msg = state.statusBanner
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            vm.clearBanner()
        }
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
        Image(
            painter = painterResource(id = R.drawable.img_obsidian_backdrop),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = 0.14f,
            modifier = Modifier.fillMaxSize()
        )

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWideTablet = maxWidth >= 840.dp

            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    TopCockpitBar(
                        state = state,
                        modelDropdownExpanded = modelDropdownExpanded,
                        onToggleModelDropdown = { modelDropdownExpanded = !modelDropdownExpanded },
                        onDismissModelDropdown = { modelDropdownExpanded = false },
                        onSelectModel = { id ->
                            vm.selectModel(id)
                            modelDropdownExpanded = false
                        },
                        onNewChat = {
                            vm.startNewChat()
                            currentTab = WorkspaceTab.HUD
                        },
                        onToggleVoiceReply = vm::toggleVoiceReply,
                        onOpenIdentity = { showIdentityDialog = true }
                    )
                },
                bottomBar = {
                    if (!isWideTablet) {
                        NavigationBar(containerColor = GraphiteSurface) {
                            NavigationBarItem(
                                selected = currentTab == WorkspaceTab.HUD,
                                onClick = { currentTab = WorkspaceTab.HUD },
                                icon = { Icon(Icons.Filled.Hub, contentDescription = "HUD Cockpit") },
                                label = { Text("HUD") }
                            )
                            NavigationBarItem(
                                selected = currentTab == WorkspaceTab.MODELS,
                                onClick = { currentTab = WorkspaceTab.MODELS },
                                icon = { Icon(Icons.Filled.Memory, contentDescription = "Models Hub") },
                                label = { Text("Models") }
                            )
                            NavigationBarItem(
                                selected = currentTab == WorkspaceTab.PLANNER,
                                onClick = { currentTab = WorkspaceTab.PLANNER },
                                icon = { Icon(Icons.Filled.DateRange, contentDescription = "Routine and Syllabus Planner") },
                                label = { Text("Planner") }
                            )
                            NavigationBarItem(
                                selected = currentTab == WorkspaceTab.PHONE,
                                onClick = { currentTab = WorkspaceTab.PHONE },
                                icon = { Icon(Icons.Filled.SettingsInputComponent, contentDescription = "Phone Controls and Vault") },
                                label = { Text("Controls") }
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
                                compactCore = false,
                                inputDraft = inputDraft,
                                onInputChange = { inputDraft = it },
                                onSend = {
                                    if (inputDraft.isNotBlank()) {
                                        vm.sendMessage(inputDraft)
                                        inputDraft = ""
                                    }
                                },
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
                        Column(
                            modifier = Modifier
                                .weight(0.44f)
                                .fillMaxHeight()
                                .background(GraphiteSurface.copy(alpha = 0.72f))
                        ) {
                            val sideTabs = listOf(WorkspaceTab.MODELS, WorkspaceTab.PLANNER, WorkspaceTab.PHONE)
                            TabRow(
                                selectedTabIndex = sideTabs.indexOf(sideTab).coerceAtLeast(0),
                                containerColor = GraphiteSurface,
                                contentColor = SapphirePulse
                            ) {
                                sideTabs.forEach { tab ->
                                    Tab(
                                        selected = sideTab == tab,
                                        onClick = { currentTab = tab },
                                        text = { Text(tab.label) }
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
                                inputDraft = inputDraft,
                                onInputChange = { inputDraft = it },
                                onSend = {
                                    if (inputDraft.isNotBlank()) {
                                        vm.sendMessage(inputDraft)
                                        inputDraft = ""
                                    }
                                },
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
            onToggleInstallModel = vm::toggleInstallModel,
            onToggleInstallVoice = vm::toggleInstallVoiceModel,
            onToggleResearch = vm::toggleWebResearch,
            onToggleEnsemble = vm::toggleEnsembleEnabled,
            onToggleEnsembleMember = vm::toggleEnsembleMember,
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
            onStageAction = vm::stageOrExecuteAction,
            onSendCommand = { cmd ->
                vm.sendMessage(cmd)
                onSwitchToHud()
            },
            onPlayTrack = vm::playAmbientTrack,
            onDismissAlarm = vm::dismissAlarm,
            onSetPermission = vm::setPermissionPolicy
        )
    }
}
