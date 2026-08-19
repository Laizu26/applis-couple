package com.ensemble.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.appwidget.provideContent
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ensemble.app.R
import com.ensemble.app.data.model.EventCategory
import com.ensemble.app.util.countdownTo
import com.ensemble.app.util.formatDate

private val WidgetBackground = Color(0xFFFFF6F3)
private val WidgetPrimary = Color(0xFFE05271)
private val WidgetText = Color(0xFF3A2529)

class CountdownWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetPrefs.read(context)
        provideContent {
            WidgetContent(snapshot)
        }
    }
}

@Composable
private fun WidgetContent(snapshot: WidgetPrefs.Snapshot) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(WidgetBackground))
            .cornerRadius(24.dp)
            .padding(16.dp),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        val dateMillis = snapshot.dateMillis
        if (snapshot.locked) {
            Image(
                provider = ImageProvider(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = GlanceModifier.size(28.dp)
            )
            Text(
                "🔒 Ouvre l'app",
                style = TextStyle(color = ColorProvider(WidgetText), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            )
        } else if (dateMillis == null) {
            Image(
                provider = ImageProvider(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = GlanceModifier.size(28.dp)
            )
            Text(
                "Ouvre l'app pour définir une date",
                style = TextStyle(color = ColorProvider(WidgetText), fontSize = 12.sp)
            )
        } else {
            val countdown = countdownTo(dateMillis)
            val defaultLabel = when {
                countdown.isPast -> "Ensemble depuis"
                snapshot.category == EventCategory.DEPART -> "avant de se quitter"
                else -> "Retrouvailles"
            }
            Text(
                EventCategory.emoji(snapshot.category) + " " +
                    (snapshot.label?.takeIf { it.isNotBlank() } ?: defaultLabel),
                style = TextStyle(color = ColorProvider(WidgetPrimary), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            )
            Text(
                "${countdown.days} jours",
                style = TextStyle(color = ColorProvider(WidgetText), fontSize = 28.sp, fontWeight = FontWeight.Bold)
            )
            Text(
                formatDate(dateMillis),
                style = TextStyle(color = ColorProvider(WidgetText), fontSize = 11.sp)
            )
        }
    }
}
