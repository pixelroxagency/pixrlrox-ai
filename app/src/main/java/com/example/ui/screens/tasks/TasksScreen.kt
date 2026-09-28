package com.example.ui.screens.tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.database.entity.PersonalTaskEntity
import com.example.data.repository.PersonalTaskRepository
import com.example.ui.components.SectionHeader
import com.example.ui.theme.BentoBorderLavender
import com.example.ui.theme.BentoPrimary
import com.example.ui.theme.BentoPrimaryContainer
import com.example.ui.theme.EmeraldGreen
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    taskRepository: PersonalTaskRepository,
    onBack: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()

    val pendingTasks by taskRepository.getPendingTasks().collectAsStateWithLifecycle(initialValue = emptyList())
    val completedTasks by taskRepository.getCompletedTasks().collectAsStateWithLifecycle(initialValue = emptyList())

    var isAddEditDialogOpen by remember { mutableStateOf(false) }
    var taskToEdit by remember { mutableStateOf<PersonalTaskEntity?>(null) }
    var taskToDelete by remember { mutableStateOf<PersonalTaskEntity?>(null) }
    var showCompletedSection by remember { mutableStateOf(false) }

    val todayCal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val todayStartMillis = todayCal.timeInMillis
    val tomorrowStartMillis = todayStartMillis + 86400000L
    val nowMillis = System.currentTimeMillis()

    val overdueTasks = pendingTasks.filter { task ->
        val trigger = task.dueDateEpoch + (task.dueTimeMinutes.coerceAtLeast(0) * 60_000L)
        trigger < nowMillis && task.dueDateEpoch < todayStartMillis
    }
    val todayTasks = pendingTasks.filter { task ->
        task.dueDateEpoch in todayStartMillis until tomorrowStartMillis
    }
    val upcomingTasks = pendingTasks.filter { task ->
        task.dueDateEpoch >= tomorrowStartMillis
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Tasks", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    taskToEdit = null
                    isAddEditDialogOpen = true
                },
                containerColor = BentoPrimary,
                contentColor = Color.White,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.testTag("add_task_fab")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Task")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = BentoPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${pendingTasks.size} pending • ${completedTasks.size} completed",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            if (overdueTasks.isNotEmpty()) {
                item { SectionHeader(title = "Overdue", subtitle = "${overdueTasks.size} tasks") }
                items(overdueTasks, key = { it.id }) { task ->
                    TaskItemCard(
                        task = task,
                        isOverdue = true,
                        onToggleComplete = { scope.launch { taskRepository.completeTask(task.id) } },
                        onEdit = { taskToEdit = task; isAddEditDialogOpen = true },
                        onDelete = { taskToDelete = task }
                    )
                }
            }

            if (todayTasks.isNotEmpty()) {
                item { SectionHeader(title = "Due Today", subtitle = "${todayTasks.size} tasks") }
                items(todayTasks, key = { it.id }) { task ->
                    TaskItemCard(
                        task = task,
                        isOverdue = false,
                        onToggleComplete = { scope.launch { taskRepository.completeTask(task.id) } },
                        onEdit = { taskToEdit = task; isAddEditDialogOpen = true },
                        onDelete = { taskToDelete = task }
                    )
                }
            }

            if (upcomingTasks.isNotEmpty()) {
                item { SectionHeader(title = "Upcoming", subtitle = "${upcomingTasks.size} tasks") }
                items(upcomingTasks, key = { it.id }) { task ->
                    TaskItemCard(
                        task = task,
                        isOverdue = false,
                        onToggleComplete = { scope.launch { taskRepository.completeTask(task.id) } },
                        onEdit = { taskToEdit = task; isAddEditDialogOpen = true },
                        onDelete = { taskToDelete = task }
                    )
                }
            }

            if (pendingTasks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(imageVector = Icons.Default.TaskAlt, contentDescription = null, tint = BentoPrimary, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("All caught up!", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Tap + to add a new task.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (completedTasks.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .clickable { showCompletedSection = !showCompletedSection }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionHeader(title = "Completed", subtitle = "${completedTasks.size} tasks")
                        Icon(
                            imageVector = if (showCompletedSection) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Toggle Completed"
                        )
                    }
                }

                if (showCompletedSection) {
                    items(completedTasks, key = { it.id }) { task ->
                        CompletedTaskItemCard(
                            task = task,
                            onRestore = { scope.launch { taskRepository.restoreTask(task.id) } },
                            onDelete = { taskToDelete = task }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    if (isAddEditDialogOpen) {
        AddEditTaskDialog(
            taskToEdit = taskToEdit,
            onDismiss = { isAddEditDialogOpen = false },
            onSave = { title, desc, dueEpoch, dueMin ->
                scope.launch {
                    taskRepository.saveTask(
                        id = taskToEdit?.id ?: 0L,
                        title = title,
                        description = desc,
                        dueDateEpoch = dueEpoch,
                        dueTimeMinutes = dueMin,
                        hasReminder = true
                    )
                    isAddEditDialogOpen = false
                }
            }
        )
    }

    if (taskToDelete != null) {
        AlertDialog(
            onDismissRequest = { taskToDelete = null },
            title = { Text("Delete Task?") },
            text = { Text("Are you sure you want to delete \"${taskToDelete!!.title}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val t = taskToDelete!!
                        taskToDelete = null
                        scope.launch { taskRepository.deleteTask(t.id) }
                    }
                ) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { taskToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun TaskItemCard(
    task: PersonalTaskEntity,
    isOverdue: Boolean,
    onToggleComplete: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("EEE, dd MMM", Locale.getDefault()) }
    val formattedDate = remember(task.dueDateEpoch) { dateFormat.format(Date(task.dueDateEpoch)) }
    val formattedTime = remember(task.dueTimeMinutes) {
        if (task.dueTimeMinutes >= 0) {
            val h = task.dueTimeMinutes / 60
            val m = task.dueTimeMinutes % 60
            String.format(Locale.getDefault(), "%02d:%02d", h, m)
        } else ""
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (isOverdue) Color(0x66FF5252) else BentoBorderLavender),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleComplete, modifier = Modifier.size(36.dp)) {
                Icon(imageVector = Icons.Default.RadioButtonUnchecked, contentDescription = "Complete task", tint = BentoPrimary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = task.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                if (task.description.isNotBlank()) {
                    Text(text = task.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.CalendarToday, contentDescription = null, tint = if (isOverdue) Color(0xFFFF5252) else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = formattedDate, fontSize = 11.sp, color = if (isOverdue) Color(0xFFFF5252) else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (formattedTime.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(imageVector = Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = formattedTime, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun CompletedTaskItemCard(
    task: PersonalTaskEntity,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, BentoBorderLavender),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = task.title, fontWeight = FontWeight.Normal, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            IconButton(onClick = onRestore, modifier = Modifier.size(32.dp)) {
                Icon(imageVector = Icons.Default.Restore, contentDescription = "Restore", tint = BentoPrimary, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditTaskDialog(
    taskToEdit: PersonalTaskEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String, Long, Int) -> Unit
) {
    var title by remember { mutableStateOf(taskToEdit?.title ?: "") }
    var description by remember { mutableStateOf(taskToEdit?.description ?: "") }
    var dueDateEpoch by remember { mutableLongStateOf(taskToEdit?.dueDateEpoch ?: System.currentTimeMillis()) }
    var dueTimeMinutes by remember { mutableIntStateOf(taskToEdit?.dueTimeMinutes ?: -1) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (taskToEdit == null) "New Task" else "Edit Task", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Due Date: ${dateFormat.format(Date(dueDateEpoch))}")
                }

                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (dueTimeMinutes < 0) "Set Time (Optional)" else "Time: ${dueTimeMinutes / 60}:${String.format(Locale.getDefault(), "%02d", dueTimeMinutes % 60)}")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onSave(title, description, dueDateEpoch, dueTimeMinutes)
                    }
                },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = BentoPrimary, contentColor = Color.White)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dueDateEpoch)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { dueDateEpoch = it }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = dueTimeMinutes / 60,
            initialMinute = dueTimeMinutes % 60
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Select Time") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    dueTimeMinutes = timePickerState.hour * 60 + timePickerState.minute
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            }
        )
    }
}
