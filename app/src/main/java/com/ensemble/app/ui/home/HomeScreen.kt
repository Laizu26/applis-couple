package com.ensemble.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ensemble.app.R
import com.ensemble.app.data.model.EventCategory
import com.ensemble.app.ui.theme.RoseContainer
import com.ensemble.app.ui.theme.RosePrimary
import com.ensemble.app.util.countdownTo
import com.ensemble.app.util.currentTimeInZone
import com.ensemble.app.util.formatDate
import com.ensemble.app.util.hourOffsetFromLocal
import kotlinx.coroutines.delay

private val HOME_CATEGORIES = listOf(EventCategory.ENSEMBLE, EventCategory.DEPART)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel, onOpenCalendar: () -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val partnerZoneId by viewModel.partnerTimeZoneId.collectAsStateWithLifecycle()
    val upcomingEvents by viewModel.upcomingEvents.collectAsStateWithLifecycle()
    var showEditDialog by remember { mutableStateOf(false) }

    var tick by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            tick = System.currentTimeMillis()
            delay(30_000)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val dateMillis = uiState.meetingDateMillis
            if (dateMillis == null) {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = null,
                    tint = RoseContainer,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.home_countdown_none),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))
                Button(onClick = { showEditDialog = true }) {
                    Text(stringResource(R.string.home_countdown_set))
                }
            } else {
                val countdown = countdownTo(dateMillis)
                val defaultTitleRes = when {
                    countdown.isPast -> R.string.home_countdown_since
                    uiState.category == EventCategory.DEPART -> R.string.home_countdown_departure
                    else -> R.string.home_countdown_until
                }
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 32.dp, horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(EventCategory.emoji(uiState.category), style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                EventCategory.label(uiState.category),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = uiState.label?.takeIf { it.isNotBlank() } ?: stringResource(defaultTitleRes),
                            style = MaterialTheme.typography.titleLarge,
                            color = RosePrimary
                        )
                        Spacer(Modifier.height(12.dp))

                        if (countdown.days == 0L && countdown.hours == 0L && !countdown.isPast) {
                            Text(
                                stringResource(R.string.home_countdown_today),
                                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 32.sp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        } else {
                            Text(
                                text = "${countdown.days}",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontSize = 72.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.home_countdown_days),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "${countdown.hours}h ${countdown.minutes}min",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(Modifier.height(16.dp))
                        Text(
                            formatDate(dateMillis),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                OutlinedButton(onClick = { showEditDialog = true }) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.home_countdown_edit))
                }
            }

            val zone = partnerZoneId
            if (zone != null) {
                val partnerTime = remember(zone, tick) { currentTimeInZone(zone) }
                val offset = remember(zone, tick) { hourOffsetFromLocal(zone) }
                if (partnerTime != null) {
                    Spacer(Modifier.height(28.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Public,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        val offsetText = offset?.let { if (it >= 0) " (UTC${if (it == 0) "" else "+$it"})" else " (UTC$it)" }.orEmpty()
                        Text(
                            "Il est $partnerTime chez ta moitié$offsetText",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (upcomingEvents.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                UpcomingEventsSection(upcomingEvents, onOpenCalendar)
            }
        }
    }

    if (showEditDialog) {
        EditMeetingDialog(
            initialCategory = uiState.category,
            initialLabel = uiState.label.orEmpty(),
            initialDateMillis = uiState.meetingDateMillis,
            initialRecurring = uiState.recurringYearly,
            onDismiss = { showEditDialog = false },
            onConfirm = { category, label, dateMillis, recurring ->
                viewModel.setMeeting(category, label, dateMillis, recurring)
                showEditDialog = false
            }
        )
    }
}

@Composable
private fun UpcomingEventsSection(items: List<UpcomingItem>, onOpenCalendar: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.home_upcoming_title), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onOpenCalendar) {
                Text(stringResource(R.string.home_upcoming_see_all))
            }
        }
        Spacer(Modifier.height(4.dp))
        items.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(EventCategory.emoji(item.event.category), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.width(10.dp))
                Text(
                    item.event.title,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    formatDate(item.effectiveDateMillis),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditMeetingDialog(
    initialCategory: String,
    initialLabel: String,
    initialDateMillis: Long?,
    initialRecurring: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (category: String, label: String, dateMillis: Long, recurring: Boolean) -> Unit
) {
    var category by remember { mutableStateOf(initialCategory) }
    var label by remember { mutableStateOf(initialLabel) }
    var recurring by remember { mutableStateOf(initialRecurring) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialDateMillis)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val selected = datePickerState.selectedDateMillis
                    if (selected != null) onConfirm(category, label, selected, recurring)
                }
            ) { Text(stringResource(R.string.pairing_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.messages_cancel)) } },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HOME_CATEGORIES.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(EventCategory.emoji(cat) + " " + EventCategory.label(cat)) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Titre (optionnel)") },
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
    )
}
