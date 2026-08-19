package com.ensemble.app.ui.journal

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ensemble.app.R
import com.ensemble.app.data.media.MediaSaver
import com.ensemble.app.data.model.DecryptedJournalDay
import com.ensemble.app.ui.components.FormDialog
import com.ensemble.app.ui.components.PhotoSourceMenu
import com.ensemble.app.util.formatDate
import com.ensemble.app.util.parseMarkup

private enum class SectionKind { MINE, COMMON }
private data class EditTarget(val dateKey: String, val dateMillis: Long, val kind: SectionKind, val initialText: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(viewModel: JournalViewModel, onBack: () -> Unit = {}) {
    val days by viewModel.days.collectAsStateWithLifecycle()
    val partnerLabel by viewModel.partnerLabel.collectAsStateWithLifecycle()
    var editingSection by remember { mutableStateOf<EditTarget?>(null) }
    var fullScreenPhotoDay by remember { mutableStateOf<DecryptedJournalDay?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.journal_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                val todayKey = viewModel.todayDateKey()
                val existingToday = days.find { it.id == todayKey }
                editingSection = EditTarget(
                    dateKey = todayKey,
                    dateMillis = viewModel.todayDateMillis(),
                    kind = SectionKind.MINE,
                    initialText = existingToday?.myText.orEmpty()
                )
            }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.journal_add))
            }
        }
    ) { padding ->
        if (days.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.AutoStories,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.journal_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                items(days, key = { it.id }) { day ->
                    DayCard(
                        day = day,
                        partnerLabel = partnerLabel?.takeIf { it.isNotBlank() } ?: stringResource(R.string.journal_partner_generic),
                        loadBitmap = { viewModel.loadDayBitmap(day) },
                        onEditMine = {
                            editingSection = EditTarget(day.id, day.dateMillis, SectionKind.MINE, day.myText)
                        },
                        onEditCommon = {
                            editingSection = EditTarget(day.id, day.dateMillis, SectionKind.COMMON, day.commonText)
                        },
                        onPhotoClick = { fullScreenPhotoDay = day },
                        onAddPhoto = { bytes -> viewModel.setDayPhoto(day.id, day.dateMillis, bytes) }
                    )
                }
            }
        }
    }

    editingSection?.let { target ->
        EditPassageDialog(
            initialText = target.initialText,
            onDismiss = { editingSection = null },
            onConfirm = { text ->
                when (target.kind) {
                    SectionKind.MINE -> viewModel.saveMyText(target.dateKey, target.dateMillis, text)
                    SectionKind.COMMON -> viewModel.saveCommonText(target.dateKey, target.dateMillis, text)
                }
                editingSection = null
            }
        )
    }

    fullScreenPhotoDay?.let { day ->
        FullScreenDayPhotoDialog(
            day,
            loadBitmap = { viewModel.loadDayBitmap(day) },
            onDismiss = { fullScreenPhotoDay = null },
            onRemove = {
                viewModel.removeDayPhoto(day.id, day.photoStoragePath)
                fullScreenPhotoDay = null
            }
        )
    }
}

@Composable
private fun DayCard(
    day: DecryptedJournalDay,
    partnerLabel: String,
    loadBitmap: suspend () -> ImageBitmap?,
    onEditMine: () -> Unit,
    onEditCommon: () -> Unit,
    onPhotoClick: () -> Unit,
    onAddPhoto: (ByteArray) -> Unit
) {
    val context = LocalContext.current
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) onAddPhoto(bytes)
        }
    }

    Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column {
            if (day.photoStoragePath != null) {
                var bitmap by remember(day.id) { mutableStateOf<ImageBitmap?>(null) }
                LaunchedEffect(day.id) { bitmap = loadBitmap() }
                val current = bitmap
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(MaterialTheme.shapes.large)
                        .clickable(enabled = current != null, onClick = onPhotoClick),
                    contentAlignment = Alignment.Center
                ) {
                    if (current != null) {
                        Image(
                            bitmap = current,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Text(formatDate(day.dateMillis), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(10.dp))
                PassageSection(stringResource(R.string.journal_section_mine), day.myText, onEditMine)
                Spacer(Modifier.height(12.dp))
                PassageSection(partnerLabel, day.partnerText, onEdit = null)
                Spacer(Modifier.height(12.dp))
                PassageSection(stringResource(R.string.journal_section_common), day.commonText, onEditCommon)

                if (day.photoStoragePath == null) {
                    Spacer(Modifier.height(10.dp))
                    PhotoSourceMenu(
                        onGalleryClick = { pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        onCameraCaptured = onAddPhoto
                    ) { openMenu ->
                        OutlinedButton(onClick = openMenu, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.journal_add_photo))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PassageSection(label: String, text: String, onEdit: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            if (text.isNotBlank()) {
                Text(parseMarkup(text), style = MaterialTheme.typography.bodyLarge)
            } else {
                Text(
                    stringResource(R.string.journal_section_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
        if (onEdit != null) {
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.messages_edit), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun EditPassageDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var textField by remember { mutableStateOf(TextFieldValue(initialText)) }

    FormDialog(
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.pairing_confirm),
        onConfirm = { onConfirm(textField.text) }
    ) {
        RichTextToolbar(value = textField, onValueChange = { textField = it })
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = textField,
            onValueChange = { textField = it },
            minLines = 5,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Petite barre d'outils qui entoure la sélection (ou insère à la position du curseur) avec la syntaxe markdown légère lue par parseMarkup(). */
@Composable
private fun RichTextToolbar(value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit) {
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

@Composable
private fun FullScreenDayPhotoDialog(
    day: DecryptedJournalDay,
    loadBitmap: suspend () -> ImageBitmap?,
    onDismiss: () -> Unit,
    onRemove: () -> Unit
) {
    var bitmap by remember(day.id) { mutableStateOf<ImageBitmap?>(null) }
    var showRemoveConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current
    LaunchedEffect(day.id) { bitmap = loadBitmap() }

    fun downloadPhoto() {
        val current = bitmap ?: return
        val saved = MediaSaver.saveImage(context, current.asAndroidBitmap())
        Toast.makeText(
            context,
            context.getString(if (saved) R.string.photos_download_success else R.string.photos_download_error),
            Toast.LENGTH_SHORT
        ).show()
    }

    val storagePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) downloadPhoto() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            val current = bitmap
            if (current != null) {
                Image(
                    bitmap = current,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(onClick = onDismiss)
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            ) {
                IconButton(
                    onClick = {
                        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                            PackageManager.PERMISSION_GRANTED
                        if (needsPermission) {
                            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        } else {
                            downloadPhoto()
                        }
                    },
                    enabled = bitmap != null,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), MaterialTheme.shapes.small)
                ) {
                    Icon(Icons.Default.Download, contentDescription = stringResource(R.string.photos_download), tint = Color.White)
                }
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = { showRemoveConfirm = true },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), MaterialTheme.shapes.small)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.messages_delete), tint = Color.White)
                }
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), MaterialTheme.shapes.small)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Fermer", tint = Color.White)
                }
            }
        }
    }

    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = { Text(stringResource(R.string.journal_remove_photo)) },
            confirmButton = {
                TextButton(onClick = {
                    showRemoveConfirm = false
                    onRemove()
                }) { Text(stringResource(R.string.messages_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) { Text(stringResource(R.string.messages_cancel)) }
            }
        )
    }
}
