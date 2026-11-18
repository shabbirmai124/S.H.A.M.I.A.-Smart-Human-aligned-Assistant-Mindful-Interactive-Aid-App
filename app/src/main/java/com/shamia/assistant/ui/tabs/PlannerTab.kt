package com.shamia.assistant.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.shamia.assistant.model.RoutineCategory
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

private enum class PlannerSubTab(val label: String) {
    TASKS_NOTES("Tasks & Notes"),
    ROUTINE_SYLLABUS("Routine & Syllabus")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlannerTab(
    state: ShamiaUiState,
    onAddRoutine: (time: String, title: String, category: RoutineCategory) -> Unit,
    onToggleRoutine: (id: String) -> Unit,
    onDeleteRoutine: (id: String) -> Unit,
    onAddSyllabus: (code: String, title: String, topic: String, examNote: String) -> Unit,
    onToggleSyllabus: (id: String) -> Unit,
    onDeleteSyllabus: (id: String) -> Unit,
    onBuildStudyPlan: () -> Unit,
    onAddTask: (title: String, highPriority: Boolean) -> Unit,
    onToggleTask: (id: String) -> Unit,
    onDeleteTask: (id: String) -> Unit,
    onClearCompletedTasks: () -> Unit,
    onAddNote: (content: String) -> Unit,
    onDeleteNote: (id: String) -> Unit
) {
    var subTab by remember { mutableStateOf(PlannerSubTab.TASKS_NOTES) }

    var taskTitle by remember { mutableStateOf("") }
    var taskHigh by remember { mutableStateOf(false) }
    var noteInput by remember { mutableStateOf("") }

    var routineTime by remember { mutableStateOf("") }
    var routineTitle by remember { mutableStateOf("") }
    var routineCat by remember { mutableStateOf(RoutineCategory.STUDY) }

    var courseCode by remember { mutableStateOf("") }
    var courseTitle by remember { mutableStateOf("") }
    var courseTopic by remember { mutableStateOf("") }
    var courseExam by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Segmented Header: Tasks & Notes | Routine & Syllabus
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = GraphiteSurface,
                border = BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PlannerSubTab.entries.forEach { tab ->
                        val selected = subTab == tab
                        Surface(
                            onClick = { subTab = tab },
                            shape = RoundedCornerShape(11.dp),
                            color = if (selected) CardSlate else GraphiteSurface,
                            border = if (selected) BorderStroke(1.dp, CardBorder) else null,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("planner_subtab_${tab.name.lowercase()}")
                        ) {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) TextPrimary else TextSecondary,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                        }
                    }
                }
            }
        }

        if (subTab == PlannerSubTab.TASKS_NOTES) {
            // Priorities Header + Input + Normal/High toggle
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Priorities",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        TextButton(
                            onClick = onClearCompletedTasks,
                            modifier = Modifier.testTag("clear_completed_tasks_button")
                        ) {
                            Text("Clear Completed", color = SapphireBright, fontWeight = FontWeight.Medium)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = taskTitle,
                            onValueChange = { taskTitle = it },
                            placeholder = { Text("Remember a priority...", color = TextSecondary) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).testTag("priority_task_input")
                        )
                        Surface(
                            onClick = {
                                if (taskTitle.isNotBlank()) {
                                    onAddTask(taskTitle, taskHigh)
                                    taskTitle = ""
                                    taskHigh = false
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = SapphireContainer,
                            border = BorderStroke(1.dp, SapphirePulse.copy(alpha = 0.55f)),
                            modifier = Modifier.size(52.dp).testTag("add_priority_task_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Add priority",
                                tint = SapphireBright,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Priority:", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        PriorityTogglePill("Normal", selected = !taskHigh) { taskHigh = false }
                        PriorityTogglePill("High", selected = taskHigh) { taskHigh = true }
                    }
                }
            }

            items(state.tasks, key = { it.id }) { task ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleTask(task.id) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (task.completed) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                        contentDescription = if (task.completed) "Completed" else "Pending",
                        tint = if (task.completed) EmeraldOk else SapphireBright,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (task.completed) TextSecondary else TextPrimary,
                            textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None
                        )
                        if (task.isHighPriority && !task.completed) {
                            Text("HIGH PRIORITY", style = MaterialTheme.typography.labelSmall, color = SapphireBright)
                        }
                    }
                    IconButton(onClick = { onDeleteTask(task.id) }) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete task", tint = RoseDeny)
                    }
                }
            }

            // Local Notes
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Local Notes",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = noteInput,
                            onValueChange = { noteInput = it },
                            placeholder = { Text("Save a quick note...", color = TextSecondary) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).testTag("local_note_input")
                        )
                        Surface(
                            onClick = {
                                if (noteInput.isNotBlank()) {
                                    onAddNote(noteInput)
                                    noteInput = ""
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = SapphireContainer,
                            border = BorderStroke(1.dp, SapphirePulse.copy(alpha = 0.55f)),
                            modifier = Modifier.size(52.dp).testTag("add_local_note_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Save note",
                                tint = SapphireBright,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
            }

            items(state.notes, key = { it.id }) { note ->
                Surface(shape = RoundedCornerShape(14.dp), color = CardSlate, border = BorderStroke(1.dp, CardBorder)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(note.content, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onDeleteNote(note.id) }) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete note", tint = TextSecondary)
                        }
                    }
                }
            }
        } else {
            // Routine & Syllabus
            item {
                Button(
                    onClick = onBuildStudyPlan,
                    colors = ButtonDefaults.buttonColors(containerColor = SapphirePulse, contentColor = ObsidianVoid),
                    modifier = Modifier.fillMaxWidth().testTag("build_study_plan_button")
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Build Study Plan from Routine & Syllabus")
                }
            }

            item {
                Text("DAILY ROUTINE (${state.routine.size})", style = MaterialTheme.typography.labelMedium, color = SapphireBright, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Surface(shape = RoundedCornerShape(14.dp), color = CardSlate, border = BorderStroke(1.dp, CardBorder)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = routineTime, onValueChange = { routineTime = it }, label = { Text("Time") }, singleLine = true, modifier = Modifier.weight(0.35f))
                            OutlinedTextField(value = routineTitle, onValueChange = { routineTitle = it }, label = { Text("Activity") }, singleLine = true, modifier = Modifier.weight(0.65f))
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            RoutineCategory.entries.forEach { cat ->
                                FilterChip(selected = routineCat == cat, onClick = { routineCat = cat }, label = { Text(cat.label) })
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                if (routineTitle.isNotBlank()) {
                                    onAddRoutine(routineTime, routineTitle, routineCat)
                                    routineTime = ""
                                    routineTitle = ""
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Routine Block")
                        }
                    }
                }
            }

            items(state.routine, key = { it.id }) { item ->
                Surface(shape = RoundedCornerShape(12.dp), color = CardSlate, border = BorderStroke(1.dp, CardBorder)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = item.completed, onCheckedChange = { onToggleRoutine(item.id) })
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${item.timeLabel} · ${item.category.label}", style = MaterialTheme.typography.labelSmall, color = SapphireBright)
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (item.completed) TextSecondary else TextPrimary,
                                textDecoration = if (item.completed) TextDecoration.LineThrough else TextDecoration.None
                            )
                        }
                        IconButton(onClick = { onDeleteRoutine(item.id) }) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete routine item", tint = RoseDeny)
                        }
                    }
                }
            }

            item {
                Text("UNIVERSITY SYLLABUS (${state.syllabus.size})", style = MaterialTheme.typography.labelMedium, color = SapphireBright, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Surface(shape = RoundedCornerShape(14.dp), color = CardSlate, border = BorderStroke(1.dp, CardBorder)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = courseCode, onValueChange = { courseCode = it }, label = { Text("Code") }, singleLine = true, modifier = Modifier.weight(0.35f))
                            OutlinedTextField(value = courseTitle, onValueChange = { courseTitle = it }, label = { Text("Course Title") }, singleLine = true, modifier = Modifier.weight(0.65f))
                        }
                        OutlinedTextField(value = courseTopic, onValueChange = { courseTopic = it }, label = { Text("Syllabus Topic / Chapters") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = courseExam, onValueChange = { courseExam = it }, label = { Text("Exam / Lab Note (Optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedButton(
                            onClick = {
                                if (courseTopic.isNotBlank()) {
                                    onAddSyllabus(courseCode, courseTitle, courseTopic, courseExam)
                                    courseCode = ""
                                    courseTitle = ""
                                    courseTopic = ""
                                    courseExam = ""
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Syllabus Topic")
                        }
                    }
                }
            }

            items(state.syllabus, key = { it.id }) { syl ->
                Surface(shape = RoundedCornerShape(12.dp), color = CardSlate, border = BorderStroke(1.dp, CardBorder)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = syl.completed, onCheckedChange = { onToggleSyllabus(syl.id) })
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${syl.courseCode} · ${syl.courseTitle}", style = MaterialTheme.typography.labelSmall, color = SapphireBright, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = syl.topic,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (syl.completed) TextSecondary else TextPrimary,
                                textDecoration = if (syl.completed) TextDecoration.LineThrough else TextDecoration.None
                            )
                            if (!syl.examNote.isNullOrBlank()) {
                                Text(syl.examNote, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            }
                        }
                        IconButton(onClick = { onDeleteSyllabus(syl.id) }) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete syllabus item", tint = RoseDeny)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PriorityTogglePill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) CardSlate else GraphiteSurface,
        border = BorderStroke(1.dp, if (selected) SapphirePulse.copy(alpha = 0.65f) else CardBorder)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) TextPrimary else TextSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
