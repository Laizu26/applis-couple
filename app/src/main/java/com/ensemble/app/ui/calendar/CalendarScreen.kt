package com.ensemble.app.ui.calendar

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ensemble.app.R
import com.ensemble.app.data.model.DecryptedEvent
import com.ensemble.app.data.model.EventCategory
import com.ensemble.app.util.formatDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(viewModel: CalendarViewModel) {
    val events by viewModel.events.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var deletingEventId by remember { mutableStateOf<String?>(null) }

    val now = System.currentTimeMillis()
    val upcoming = events.filter { it.dateMillis >= now }.sortedBy { it.dateMillis }
    val past = events.filter { it.dateMillis < now }.sortedByDescending { it.dateMillis }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.calendar_title)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.calendar_add))
            }
        }
    ) { padding ->
        if (events.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.calendar_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                if (upcoming.isNotEmpty()) {
                    item(key = "header_upcoming") { SectionHeader(stringResource(R.string.calendar_upcoming)) }
                    items(upcoming, key = { it.id }) { event ->
                        EventRow(event, onDelete = { deletingEventId = event.id })
                    }
                }
                if (past.isNotEmpty()) {
                    item(key = "header_past") { SectionHeader(stringResource(R.string.calendar_past)) }
                    items(past, key = { it.id }) { event ->
                        EventRow(event, onDelete = { deletingEventId = event.id })
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddEventDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { category, title, dateMillis ->
                viewModel.addEvent(category, title, dateMillis)
                showAddDialog = false
            }
        )
    }

    deletingEventId?.let { id ->
        AlertDialog(
            onDismissRequest = { deletingEventId = null },
            title = { Text(stringResource(R.string.calendar_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteEvent(id); deletingEventId = null }) {
                    Text(stringResource(R.string.messages_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingEventId = null }) { Text(stringResource(R.string.messages_cancel)) }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun EventRow(event: DecryptedEvent, onDelete: () -> Unit) {
    Card(shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(EventCategory.emoji(event.category), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(event.title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    formatDate(event.dateMillis),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.messages_delete))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEventDialog(
    onDismiss: () -> Unit,
    onConfirm: (category: String, title: String, dateMillis: Long) -> Unit
) {
    var category by remember { mutableStateOf(EventCategory.ENSEMBLE) }
    var title by remember { mutableStateOf("") }
    val datePickerState = rememberDatePickerState()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val selected = datePickerState.selectedDateMillis
                    if (selected != null && title.isNotBlank()) onConfirm(category, title, selected)
                }
            ) { Text(stringResource(R.string.pairing_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.messages_cancel)) } },
        text = {
            Column {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                ) {
                    EventCategory.all.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(EventCategory.emoji(cat) + " " + EventCategory.label(cat)) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.calendar_event_title_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                DatePicker(state = datePickerState, showModeToggle = false)
            }
        }
    )
}
