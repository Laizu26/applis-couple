package com.ensemble.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

/**
 * Petite barre d'outils qui entoure la sélection (ou insère à la position du curseur) avec la
 * syntaxe markdown légère lue par parseMarkup() : **gras**, *italique*, __souligné__, "- " puces.
 */
@Composable
fun RichTextToolbar(value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit) {
    fun wrapSelection(marker: String) {
        val sel = value.selection
        val text = value.text
        onValueChange(
            if (sel.collapsed) {
                val newText = text.substring(0, sel.start) + marker + marker + text.substring(sel.start)
                value.copy(text = newText, selection = TextRange(sel.start + marker.length))
            } else {
                val start = sel.min
                val end = sel.max
                val newText = text.substring(0, start) + marker + text.substring(start, end) + marker + text.substring(end)
                value.copy(text = newText, selection = TextRange(start + marker.length, end + marker.length))
            }
        )
    }

    fun insertBullet() {
        val text = value.text
        val cursor = value.selection.start
        val lineStart = text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
        val newText = text.substring(0, lineStart) + "- " + text.substring(lineStart)
        onValueChange(value.copy(text = newText, selection = TextRange(cursor + 2)))
    }

    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        IconButton(onClick = { wrapSelection("**") }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.FormatBold, contentDescription = "Gras", modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = { wrapSelection("*") }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.FormatItalic, contentDescription = "Italique", modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = { wrapSelection("__") }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.FormatUnderlined, contentDescription = "Souligné", modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = { insertBullet() }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = "Liste à puces", modifier = Modifier.size(20.dp))
        }
    }
}
