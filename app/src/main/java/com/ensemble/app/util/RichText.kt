package com.ensemble.app.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/**
 * Parseur minimal pour un sous-ensemble de Markdown (gras **texte**, italique *texte*,
 * souligné __texte__, listes à puces en début de ligne avec "- "). Permet un vrai mini
 * traitement de texte dans le journal sans dépendance externe ni changement de format de
 * stockage : le texte chiffré reste une simple chaîne de caractères.
 */
fun parseMarkup(raw: String): AnnotatedString = buildAnnotatedString {
    val lines = raw.split("\n")
    lines.forEachIndexed { lineIndex, line ->
        val isBullet = line.startsWith("- ")
        val content = if (isBullet) line.removePrefix("- ") else line
        if (isBullet) append("•  ")
        appendInlineMarkup(content)
        if (lineIndex != lines.lastIndex) append("\n")
    }
}

private fun AnnotatedString.Builder.appendInlineMarkup(text: String) {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end == -1) {
                    append(text.substring(i))
                    i = text.length
                } else {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(i + 2, end)) }
                    i = end + 2
                }
            }
            text.startsWith("__", i) -> {
                val end = text.indexOf("__", i + 2)
                if (end == -1) {
                    append(text.substring(i))
                    i = text.length
                } else {
                    withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append(text.substring(i + 2, end)) }
                    i = end + 2
                }
            }
            text[i] == '*' -> {
                val end = text.indexOf('*', i + 1)
                if (end == -1) {
                    append(text.substring(i))
                    i = text.length
                } else {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }
                    i = end + 1
                }
            }
            else -> {
                append(text[i])
                i++
            }
        }
    }
}
