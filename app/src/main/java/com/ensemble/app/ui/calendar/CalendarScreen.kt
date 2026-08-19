package com.ensemble.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ensemble.app.R
import com.ensemble.app.data.model.DecryptedEvent
import com.ensemble.app.data.model.EventCategory
import com.ensemble.app.ui.components.FormDialog
import com.ensemble.app.util.formatDate
import com.ensemble.app.util.localDateToPickerMillis
import com.ensemble.app.util.occurrenceInYear
import com.ensemble.app.util.pickerMillisToLocalDayMillis
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private val WEEKDAY_LABELS = listOf("L", "M", "M", "J", "V", "S", "D")

private fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

private fun LocalDate.toEpochMillisAtStartOfDay(): Long =
    atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** Regroupe les événements par jour pour le mois affiché, en projetant les événements récurrents dans son année. */
private fun eventsForMonth(events: List<DecryptedEvent>, month: YearMonth): Map<LocalDate, List<DecryptedEvent>> {
    val result = mutableMapOf<LocalDate, MutableList<DecryptedEvent>>()
    events.forEach { event ->
        val date = if (event.recurringYearly) {
            occurrenceInYear(event.dateMillis, month.year)?.toLocalDate()
        } else {
            event.dateMillis.toLocalDate()
        }
        if (date != null && YearMonth.from(date) == month) {
            result.getOrPut(date) { mutableListOf() }.add(event)
        }
    }
    return result
}

/** Événements d'un jour donné, récurrents (même jour+mois, année quelconque) ou non (date exacte). */
private fun eventsOnDate(events: List<DecryptedEvent>, date: LocalDate): List<DecryptedEvent> =
    events.filter { event ->
        if (event.recurringYearly) {
            val original = event.dateMillis.toLocalDate()
            original.month == date.month && original.dayOfMonth == date.dayOfMonth
        } else {
            event.dateMillis.toLocalDate() == date
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(viewModel: CalendarViewModel, onBack: () -> Unit = {}) {
    val events by viewModel.events.collectAsStateWithLifecycle()
    var visibleMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var deletingEventId by remember { mutableStateOf<String?>(null) }

    val eventsByDate = remember(events, visibleMonth) { eventsForMonth(events, visibleMonth) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.calendar_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.calendar_add))
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            MonthHeader(
                visibleMonth = visibleMonth,
                onPrevious = { visibleMonth = visibleMonth.minusMonths(1) },
                onNext = { visibleMonth = visibleMonth.plusMonths(1) }
            )
            MonthGrid(
                visibleMonth = visibleMonth,
                selectedDate = selectedDate,
                eventsByDate = eventsByDate,
                onDayClick = { selectedDate = it }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            val dayEvents = remember(events, selectedDate) { eventsOnDate(events, selectedDate) }
            Text(
                formatDate(selectedDate.toEpochMillisAtStartOfDay()),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            if (dayEvents.isEmpty()) {
                Text(
                    stringResource(R.string.calendar_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(dayEvents, key = { it.id }) { event ->
                        EventRow(event, onDelete = { deletingEventId = event.id })
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddEventDialog(
            initialDateMillis = localDateToPickerMillis(selectedDate),
            onDismiss = { showAddDialog = false },
            onConfirm = { category, title, dateMillis, recurring ->
                viewModel.addEvent(category, title, dateMillis, recurring)
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
private fun MonthHeader(visibleMonth: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Mois précédent")
        }
        val monthName = visibleMonth.month.getDisplayName(TextStyle.FULL, Locale.FRENCH)
            .replaceFirstChar { it.uppercase() }
        Text("$monthName ${visibleMonth.year}", style = MaterialTheme.typography.titleLarge)
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Mois suivant")
        }
    }
}

@Composable
private fun MonthGrid(
    visibleMonth: YearMonth,
    selectedDate: LocalDate,
    eventsByDate: Map<LocalDate, List<DecryptedEvent>>,
    onDayClick: (LocalDate) -> Unit
) {
    val firstOfMonth = visibleMonth.atDay(1)
    val leadingBlanks = firstOfMonth.dayOfWeek.value - 1
    val daysInMonth = visibleMonth.lengthOfMonth()
    val trailingBlanks = (7 - (leadingBlanks + daysInMonth) % 7) % 7
    val cells = buildList {
        repeat(leadingBlanks) { add(null) }
        for (day in 1..daysInMonth) add(visibleMonth.atDay(day))
        repeat(trailingBlanks) { add(null) }
    }
    val today = LocalDate.now()

    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            WEEKDAY_LABELS.forEach { label ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(
                        modifier = Modifier.weight(1f).aspectRatio(1f).padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (date != null) {
                            DayCell(
                                date = date,
                                isToday = date == today,
                                isSelected = date == selectedDate,
                                events = eventsByDate[date].orEmpty(),
                                onClick = { onDayClick(date) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    isSelected: Boolean,
    events: List<DecryptedEvent>,
    onClick: () -> Unit
) {
    val background = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(background, CircleShape)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "${date.dayOfMonth}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = textColor
        )
        if (events.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                events.map { it.category }.distinct().take(3).forEach { category ->
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(Color(EventCategory.colorHex(category)), CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
private fun EventRow(event: DecryptedEvent, onDelete: () -> Unit) {
    Card(shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(Color(EventCategory.colorHex(event.category)), CircleShape)
            )
            Spacer(Modifier.width(10.dp))
            Text(EventCategory.emoji(event.category), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(event.title, style = MaterialTheme.typography.bodyLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        EventCategory.label(event.category),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (event.recurringYearly) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Default.Repeat,
                            contentDescription = stringResource(R.string.event_recurring_yearly),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
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
    initialDateMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (category: String, title: String, dateMillis: Long, recurring: Boolean) -> Unit
) {
    var category by remember { mutableStateOf(EventCategory.ENSEMBLE) }
    var title by remember { mutableStateOf("") }
    var recurring by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialDateMillis)

    FormDialog(
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.pairing_confirm),
        onConfirm = {
            val selected = datePickerState.selectedDateMillis
            if (selected != null && title.isNotBlank()) onConfirm(category, title, pickerMillisToLocalDayMillis(selected), recurring)
        }
    ) {
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = recurring, onCheckedChange = { recurring = it })
            Text(stringResource(R.string.event_recurring_yearly), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
