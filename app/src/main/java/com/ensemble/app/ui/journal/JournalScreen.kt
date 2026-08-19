package com.ensemble.app.ui.journal

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
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Image as ImageIcon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.ensemble.app.data.model.DecryptedMemory
import com.ensemble.app.ui.components.FormDialog
import com.ensemble.app.util.formatDate
import com.ensemble.app.util.localDateToPickerMillis
import com.ensemble.app.util.parseMarkup
import com.ensemble.app.util.pickerMillisToLocalDayMillis
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(viewModel: JournalViewModel, onBack: () -> Unit = {}) {
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var deletingId by remember { mutableStateOf<String?>(null) }
    var fullScreenMemory by remember { mutableStateOf<DecryptedMemory?>(null) }

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
            FloatingActionButton(onClick = { showAddDialog = true }) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.journal_add))
                }
            }
        }
    ) { padding ->
        if (memories.isEmpty()) {
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
                items(memories, key = { it.id }) { memory ->
                    MemoryCard(
                        memory,
                        loadBitmap = { viewModel.loadBitmap(memory) },
                        onDelete = { deletingId = memory.id },
                        onPhotoClick = { fullScreenMemory = memory }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddMemoryDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { text, date, photoBytes ->
                viewModel.addMemory(text, date, photoBytes)
                showAddDialog = false
            }
        )
    }

    deletingId?.let { id ->
        AlertDialog(
            onDismissRequest = { deletingId = null },
            title = { Text(stringResource(R.string.journal_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteMemory(id); deletingId = null }) {
                    Text(stringResource(R.string.messages_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deletingId = null }) { Text(stringResource(R.string.messages_cancel)) } }
        )
    }

    fullScreenMemory?.let { memory ->
        FullScreenMemoryPhotoDialog(
            memory,
            loadBitmap = { viewModel.loadBitmap(memory) },
            onDismiss = { fullScreenMemory = null }
        )
    }
}

@Composable
private fun MemoryCard(
    memory: DecryptedMemory,
    loadBitmap: suspend () -> androidx.compose.ui.graphics.ImageBitmap?,
    onDelete: () -> Unit,
    onPhotoClick: () -> Unit
) {
    Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column {
            if (memory.photoStoragePath != null) {
                var bitmap by remember(memory.id) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
                LaunchedEffect(memory.id) { bitmap = loadBitmap() }
                val current = bitmap
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(MaterialTheme.shapes.large)
                        .clickable(enabled = bitmap != null, onClick = onPhotoClick),
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
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(formatDate(memory.memoryDate), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Text(parseMarkup(memory.text), style = MaterialTheme.typography.bodyLarge)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.messages_delete))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddMemoryDialog(
    onDismiss: () -> Unit,
    onConfirm: (text: String, dateMillis: Long, photoBytes: ByteArray?) -> Unit
) {
    var textField by remember { mutableStateOf(TextFieldValue("")) }
    var photoBytes by remember { mutableStateOf<ByteArray?>(null) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = localDateToPickerMillis(java.time.LocalDate.now())
    )
    val context = LocalContext.current

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            photoBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }
    }

    FormDialog(
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.pairing_confirm),
        onConfirm = {
            val selected = datePickerState.selectedDateMillis
            if (selected != null) onConfirm(textField.text, pickerMillisToLocalDayMillis(selected), photoBytes)
        }
    ) {
        RichTextToolbar(value = textField, onValueChange = { textField = it })
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = textField,
            onValueChange = { textField = it },
            label = { Text(stringResource(R.string.journal_text_hint)) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        if (photoBytes != null) {
            OutlinedButton(
                onClick = { photoBytes = null },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.journal_remove_photo))
            }
        } else {
            OutlinedButton(
                onClick = { pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.ImageIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.journal_add_photo))
            }
        }
        Spacer(Modifier.height(8.dp))
        DatePicker(state = datePickerState, showModeToggle = false)
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
private fun FullScreenMemoryPhotoDialog(
    memory: DecryptedMemory,
    loadBitmap: suspend () -> androidx.compose.ui.graphics.ImageBitmap?,
    onDismiss: () -> Unit
) {
    var bitmap by remember(memory.id) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    val context = LocalContext.current
    LaunchedEffect(memory.id) { bitmap = loadBitmap() }

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
                    onClick = onDismiss,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), MaterialTheme.shapes.small)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Fermer", tint = Color.White)
                }
            }
        }
    }
}
