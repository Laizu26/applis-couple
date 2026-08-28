package com.ensemble.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_SCALE = 2.5f

/**
 * Image plein écran zoomable au pincement, avec panoramique une fois zoomée et double-tap
 * pour zoomer/dézoomer. Tant que l'image n'est pas zoomée (scale == 1), un glissement à un
 * seul doigt n'est jamais consommé ici : il remonte intact au HorizontalPager parent, qui
 * continue de changer de photo au swipe comme avant. Une fois zoomée, le pan à un doigt
 * prend le dessus sur le changement de page.
 */
@Composable
fun ZoomableImage(bitmap: ImageBitmap, onTap: () -> Unit, modifier: Modifier = Modifier) {
    var scale by remember(bitmap) { mutableStateOf(MIN_SCALE) }
    var offset by remember(bitmap) { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    fun clamp(candidate: Offset, currentScale: Float): Offset {
        val maxX = (containerSize.width * (currentScale - 1) / 2f).coerceAtLeast(0f)
        val maxY = (containerSize.height * (currentScale - 1) / 2f).coerceAtLeast(0f)
        return Offset(candidate.x.coerceIn(-maxX, maxX), candidate.y.coerceIn(-maxY, maxY))
    }

    Image(
        bitmap = bitmap,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .onSizeChanged { containerSize = it }
            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
            .pointerInput(bitmap) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > MIN_SCALE) {
                            scale = MIN_SCALE
                            offset = Offset.Zero
                        } else {
                            scale = DOUBLE_TAP_SCALE
                        }
                    }
                )
            }
            .pointerInput(bitmap) {
                awaitEachGesture {
                    while (true) {
                        val event = awaitPointerEvent()
                        val isMultiTouch = event.changes.size > 1
                        if (isMultiTouch || scale > MIN_SCALE) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            scale = (scale * zoomChange).coerceIn(MIN_SCALE, MAX_SCALE)
                            offset = clamp(offset + panChange, scale)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                        if (event.changes.none { it.pressed }) break
                    }
                    if (scale <= MIN_SCALE) {
                        scale = MIN_SCALE
                        offset = Offset.Zero
                    }
                }
            }
    )
}
