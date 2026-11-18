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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.shamia.assistant.model.RoutineCategory
import com.shamia.assistant.ui.ShamiaUiState
import com.shamia.assistant.ui.theme.CardBorder
import com.shamia.assistant.ui.theme.CardSlate
import com.shamia.assistant.ui.theme.ObsidianVoid
import com.shamia.assistant.ui.theme.RoseDeny
import com.shamia.assistant.ui.theme.SapphireBright
import com.shamia.assistant.ui.theme.SapphirePulse
import com.shamia.assistant.ui.theme.TextPrimary
import com.shamia.assistant.ui.theme.TextSecondary

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
    var routineTime by remember { mutableStateOf("") }
    var routineTitle by remember { mutableStateOf("") }
    var routineCat by remember { mutableStateOf(RoutineCategory.STUDY) }

    var courseCode by remember { mutableStateOf("") }
    var courseTitle by remember { mutableStateOf("") }
    var courseTopic by remember { mutableStateOf("") }
    var courseExam by remember { mutableStateOf("") }

    var taskTitle by remember { mutableStateOf("") }
    var taskHigh by remember { mutableStateOf(false) }
    var noteInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Button(
                onClick = onBuildStudyPlan,
                colors = ButtonDefaults.buttonColors(containerColor = SapphirePulse, contentColor = ObsidianVoid),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("build_study_plan_button")
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Build Study Plan from Routine & Syllabus")
            }
        }

        // Daily Routine
        item {
            SectionTitle("Daily Routine (${state.routine.size})")
            Surface(shape = RoundedCornerShape(14.dp), color = CardSlate, border = BorderStroke(1.dp, CardBorder)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = routineTime,
                            onValueChange = { routineTime = it },
                            label = { Text("Time") },
                            singleLine = true,
                            modifier = Modifier.weight(0.35f)
                        )
                        OutlinedTextField(
                            value = routineTitle,
                            onValueChange = { routineTitle = it },
                            label = { Text("Activity") },
                            singleLine = true,
                            modifier = Modifier.weight(0.65f)
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RoutineCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = routineCat == cat,
                                onClick = { routineCat = cat },
                                label = { Text(cat.label) }
                            )
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = item.completed, onCheckedChange = { onToggleRoutine(item.id) })
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${item.timeLabel} · ${item.category.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SapphireBright
                        )
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

        // University Syllabus
        item {
            Spacer(modifier = Modifier.height(4.dp))
            SectionTitle("University Syllabus (${state.syllabus.size})")
            Surface(shape = RoundedCornerShape(14.dp), color = CardSlate, border = BorderStroke(1.dp, CardBorder)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = courseCode,
                            onValueChange = { courseCode = it },
                            label = { Text("Code") },
                            singleLine = true,
                            modifier = Modifier.weight(0.35f)
                        )
                        OutlinedTextField(
                            value = courseTitle,
                            onValueChange = { courseTitle = it },
                            label = { Text("Course Title") },
                            singleLine = true,
                            modifier = Modifier.weight(0.65f)
                        )
                    }
                    OutlinedTextField(
                        value = courseTopic,
                        onValueChange = { courseTopic = it },
                        label = { Text("Syllabus Topic / Chapters") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = courseExam,
                        onValueChange = { courseExam = it },
                        label = { Text("Exam / Lab Note (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = syl.completed, onCheckedChange = { onToggleSyllabus(syl.id) })
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${syl.courseCode} · ${syl.courseTitle}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SapphireBright,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = syl.topic,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (syl.completed) TextSecondary else TextPrimary,
                            textDecoration = if (syl.completed) TextDecoration.LineThrough else TextDecoration.None
                        )
                        if (!syl.examNote.isNullOrBlank()) {
                            Text(text = syl.examNote, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                    }
                    IconButton(onClick = { onDeleteSyllabus(syl.id) }) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete syllabus item", tint = RoseDeny)
                    }
                }
            }
        }

        // Tasks Ledger
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionTitle("Priority Tasks (${state.tasks.count { !it.completed }} pending)")
                if (state.tasks.any { it.completed }) {
                    TextButton(onClick = onClearCompletedTasks) {
                        Text("Clear Done", color = SapphireBright)
                    }
                }
            }
            Surface(shape = RoundedCornerShape(14.dp), color = CardSlate, border = BorderStroke(1.dp, CardBorder)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = taskTitle,
                        onValueChange = { taskTitle = it },
                        label = { Text("New priority task") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = taskHigh,
                            onClick = { taskHigh = !taskHigh },
                            label = { Text("High Priority") }
                        )
                        OutlinedButton(onClick = {
                            if (taskTitle.isNotBlank()) {
                                onAddTask(taskTitle, taskHigh)
                                taskTitle = ""
                                taskHigh = false
                            }
                        }) {
                            Text("Add Task")
                        }
                    }
                }
            }
        }

        items(state.tasks, key = { it.id }) { task ->
            Surface(shape = RoundedCornerShape(12.dp), color = CardSlate, border = BorderStroke(1.dp, CardBorder)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = task.completed, onCheckedChange = { onToggleTask(task.id) })
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (task.completed) TextSecondary else TextPrimary,
                            textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None
                        )
                        if (task.isHighPriority) {
                            Text("HIGH PRIORITY", style = MaterialTheme.typography.labelSmall, color = SapphireBright)
                        }
                    }
                    IconButton(onClick = { onDeleteTask(task.id) }) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete task", tint = RoseDeny)
                    }
                }
            }
        }

        // Private Notes
        item {
            Spacer(modifier = Modifier.height(4.dp))
            SectionTitle("Private Notes (${state.notes.size})")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text("Add private note") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = {
                    if (noteInput.isNotBlank()) {
                        onAddNote(noteInput)
                        noteInput = ""
                    }
                }) {
                    Text("Save")
                }
            }
        }

        items(state.notes, key = { it.id }) { note ->
            Surface(shape = RoundedCornerShape(12.dp), color = CardSlate, border = BorderStroke(1.dp, CardBorder)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(note.content, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onDeleteNote(note.id) }) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete note", tint = RoseDeny)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = SapphireBright,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}
