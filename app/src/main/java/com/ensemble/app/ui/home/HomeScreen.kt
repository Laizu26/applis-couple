package com.ensemble.app.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ensemble.app.R
import com.ensemble.app.ui.theme.RosePrimary
import com.ensemble.app.ui.theme.RoseContainer
import com.ensemble.app.util.countdownTo
import com.ensemble.app.util.formatDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showEditDialog by remember { mutableStateOf(false) }

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
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 40.dp, horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = uiState.label?.takeIf { it.isNotBlank() }
                                ?: stringResource(if (countdown.isPast) R.string.home_countdown_since else R.string.home_countdown_until),
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
        }
    }

    if (showEditDialog) {
        EditMeetingDialog(
            initialLabel = uiState.label.orEmpty(),
            initialDateMillis = uiState.meetingDateMillis,
            onDismiss = { showEditDialog = false },
            onConfirm = { label, dateMillis ->
                viewModel.setMeeting(label, dateMillis)
                showEditDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditMeetingDialog(
    initialLabel: String,
    initialDateMillis: Long?,
    onDismiss: () -> Unit,
    onConfirm: (String, Long) -> Unit
) {
    var label by remember { mutableStateOf(initialLabel) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialDateMillis)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val selected = datePickerState.selectedDateMillis
                    if (selected != null) onConfirm(label, selected)
                }
            ) { Text(stringResource(R.string.pairing_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.messages_cancel)) } },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Titre (optionnel)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                DatePicker(state = datePickerState, showModeToggle = false)
            }
        }
    )
}
