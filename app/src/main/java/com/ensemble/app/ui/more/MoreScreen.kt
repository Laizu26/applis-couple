package com.ensemble.app.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ensemble.app.R

@Composable
fun MoreScreen(onCalendarClick: () -> Unit, onJournalClick: () -> Unit, onSettingsClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(top = 12.dp)) {
        Text(
            stringResource(R.string.nav_more),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )
        MoreItem(
            icon = Icons.Default.CalendarMonth,
            label = stringResource(R.string.more_calendar),
            onClick = onCalendarClick
        )
        HorizontalDivider()
        MoreItem(
            icon = Icons.Default.AutoStories,
            label = stringResource(R.string.more_journal),
            onClick = onJournalClick
        )
        HorizontalDivider()
        MoreItem(
            icon = Icons.Default.Settings,
            label = stringResource(R.string.more_settings),
            onClick = onSettingsClick
        )
        HorizontalDivider()
    }
}

@Composable
private fun MoreItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
