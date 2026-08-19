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
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.appwidget.provideContent
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ensemble.app.R

private val WidgetBackground = Color(0xFFFFF6F3)
private val WidgetText = Color(0xFF3A2529)

class PhotoWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = PhotoWidgetPrefs.read(context)
        provideContent {
            PhotoWidgetContent(snapshot)
        }
    }
}

@Composable
private fun PhotoWidgetContent(snapshot: PhotoWidgetPrefs.Snapshot) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(WidgetBackground))
            .cornerRadius(24.dp),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = snapshot.bitmap
        when {
            snapshot.locked -> LockedContent()
            bitmap != null -> Image(
                provider = ImageProvider(bitmap),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier.fillMaxSize().cornerRadius(24.dp)
            )
            else -> EmptyContent()
        }
    }
}

@Composable
private fun LockedContent() {
    Column(
        modifier = GlanceModifier.padding(16.dp),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = GlanceModifier.size(28.dp)
        )
        Text(
            "🔒 Ouvre l'app",
            style = TextStyle(color = ColorProvider(WidgetText), fontSize = 13.sp, fontWeight = FontWeight.Medium)
        )
    }
}

@Composable
private fun EmptyContent() {
    Column(
        modifier = GlanceModifier.padding(16.dp),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = GlanceModifier.size(28.dp)
        )
        Text(
            "Ajoutez une photo",
            style = TextStyle(color = ColorProvider(WidgetText), fontSize = 12.sp)
        )
    }
}
