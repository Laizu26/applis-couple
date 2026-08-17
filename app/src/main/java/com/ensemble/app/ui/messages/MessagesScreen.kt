package com.ensemble.app.ui.messages

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ensemble.app.R
import com.ensemble.app.data.audio.VoiceRecorder
import com.ensemble.app.data.model.DecryptedMessage
import com.ensemble.app.data.model.MessageType
import com.ensemble.app.ui.theme.BubbleMine
import com.ensemble.app.ui.theme.BubbleTheirs
import com.ensemble.app.util.formatDuration
import com.ensemble.app.util.formatMessageTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val QUICK_REACTIONS = listOf("❤️", "😂", "😮", "😢", "👍")

@Composable
fun MessagesScreen(viewModel: MessagesViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val partnerTyping by viewModel.partnerTyping.collectAsStateWithLifecycle()
    val partnerReadTimestamp by viewModel.partnerReadTimestamp.collectAsStateWithLifecycle()
    val playingMessageId by viewModel.playingMessageId.collectAsStateWithLifecycle()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var actionsForMessage by remember { mutableStateOf<DecryptedMessage?>(null) }
    var editingMessage by remember { mutableStateOf<DecryptedMessage?>(null) }
    var deletingMessageId by remember { mutableStateOf<String?>(null) }
    var fullScreenPhoto by remember { mutableStateOf<DecryptedMessage?>(null) }

    val voiceRecorder = remember { VoiceRecorder(context) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingElapsedMs by remember { mutableStateOf(0L) }
    val recordAudioPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) { voiceRecorder.start(); isRecording = true } }

    LaunchedEffect(isRecording) {
        val start = System.currentTimeMillis()
        while (isRecording) {
            recordingElapsedMs = System.currentTimeMillis() - start
            delay(200)
        }
    }

    val myLastMessageId = uiState.messages.lastOrNull { it.senderId == viewModel.currentUserId }?.id
    val lastMessageIsSeen = uiState.messages.lastOrNull { it.senderId == viewModel.currentUserId }
        ?.let { it.timestamp <= partnerReadTimestamp && partnerReadTimestamp > 0 } == true

    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris ->
        if (uris.isNotEmpty()) {
            val bytesList = uris.mapNotNull { uri ->
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }
            if (bytesList.isNotEmpty()) viewModel.sendPhotos(bytesList)
        }
    }

    LaunchedEffect(uiState.messages.size, partnerTyping) {
        if (uiState.messages.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(uiState.messages.lastIndex) }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(uiState.messages, key = { it.id }) { message ->
                val isMine = message.senderId == viewModel.currentUserId
                MessageBubble(
                    message = message,
                    isMine = isMine,
                    showSeen = isMine && message.id == myLastMessageId && lastMessageIsSeen,
                    isPlaying = playingMessageId == message.id,
                    loadBitmap = { viewModel.loadPhotoBitmap(message) },
                    onLongPress = { actionsForMessage = message },
                    onPhotoClick = { fullScreenPhoto = message },
                    onTogglePlayback = { viewModel.togglePlayback(message) }
                )
            }
            if (partnerTyping) {
                item(key = "typing") { TypingIndicatorBubble() }
            }
        }

        if (isRecording) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    voiceRecorder.cancel()
                    isRecording = false
                }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.messages_cancel))
                }
                Box(
                    modifier = Modifier.size(10.dp).background(MaterialTheme.colorScheme.error, CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    formatDuration(recordingElapsedMs),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    isRecording = false
                    val result = voiceRecorder.stop()
                    if (result != null) viewModel.sendVoiceNote(result.first, result.second)
                }) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Envoyer", tint = MaterialTheme.colorScheme.primary)
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = stringResource(R.string.photos_add))
                }
                OutlinedTextField(
                    value = uiState.draft,
                    onValueChange = viewModel::onDraftChange,
                    placeholder = { Text(stringResource(R.string.messages_hint)) },
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(4.dp))
                if (uiState.draft.isBlank()) {
                    IconButton(onClick = {
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            voiceRecorder.start()
                            isRecording = true
                        } else {
                            recordAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }) {
                        Icon(Icons.Default.Mic, contentDescription = "Note vocale", tint = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    IconButton(onClick = viewModel::sendMessage) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Envoyer", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
    }

    actionsForMessage?.let { message ->
        MessageActionsDialog(
            message = message,
            isMine = message.senderId == viewModel.currentUserId,
            onDismiss = { actionsForMessage = null },
            onReact = { emoji ->
                viewModel.toggleReaction(message, emoji)
                actionsForMessage = null
            },
            onEdit = {
                editingMessage = message
                actionsForMessage = null
            },
            onDelete = {
                deletingMessageId = message.id
                actionsForMessage = null
            }
        )
    }

    editingMessage?.let { message ->
        EditMessageDialog(
            initialText = message.text,
            onDismiss = { editingMessage = null },
            onConfirm = { newText ->
                viewModel.editMessage(message.id, newText)
                editingMessage = null
            }
        )
    }

    deletingMessageId?.let { messageId ->
        AlertDialog(
            onDismissRequest = { deletingMessageId = null },
            title = { Text(stringResource(R.string.messages_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMessage(messageId)
                    deletingMessageId = null
                }) { Text(stringResource(R.string.messages_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deletingMessageId = null }) { Text(stringResource(R.string.messages_cancel)) }
            }
        )
    }

    fullScreenPhoto?.let { message ->
        FullScreenChatPhotoDialog(message, viewModel, onDismiss = { fullScreenPhoto = null })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: DecryptedMessage,
    isMine: Boolean,
    showSeen: Boolean,
    isPlaying: Boolean,
    loadBitmap: suspend () -> androidx.compose.ui.graphics.ImageBitmap?,
    onLongPress: () -> Unit,
    onPhotoClick: () -> Unit,
    onTogglePlayback: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
        ) {
            Column(
                modifier = Modifier
                    .background(
                        color = if (isMine) BubbleMine else BubbleTheirs,
                        shape = MaterialTheme.shapes.large
                    )
                    .combinedClickable(onClick = {}, onLongClick = onLongPress)
                    .padding(if (message.type == MessageType.PHOTO) 6.dp else 14.dp)
                    .widthIn(max = 260.dp)
            ) {
                if (message.type == MessageType.PHOTO) {
                    var bitmap by remember(message.id) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
                    LaunchedEffect(message.id) { bitmap = loadBitmap() }
                    val current = bitmap
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .clip(MaterialTheme.shapes.medium)
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
                    if (message.text.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            message.text,
                            color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                } else if (message.type == MessageType.AUDIO) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onTogglePlayback, modifier = Modifier.size(32.dp)) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = if (isMine) Color.White else MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            formatDuration(message.audioDurationMs ?: 0L),
                            color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else {
                    Text(
                        text = message.text,
                        color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Text(
                        text = formatMessageTime(message.timestamp) + if (message.editedAt != null) " " + stringResource(R.string.messages_edited) else "",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 10.sp),
                        color = if (isMine) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (message.reactions.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .align(if (isMine) Alignment.End else Alignment.Start)
                    .padding(top = 2.dp, start = 4.dp, end = 4.dp)
            ) {
                message.reactions.values.distinct().forEach { emoji ->
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(emoji, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        if (showSeen) {
            Text(
                stringResource(R.string.messages_seen),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 2.dp, end = 4.dp)
            )
        }
    }
}

@Composable
private fun TypingIndicatorBubble() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Box(
            modifier = Modifier
                .background(BubbleTheirs, MaterialTheme.shapes.large)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(stringResource(R.string.messages_typing), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MessageActionsDialog(
    message: DecryptedMessage,
    isMine: Boolean,
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.messages_cancel)) } },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                    QUICK_REACTIONS.forEach { emoji ->
                        Text(
                            emoji,
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.clickable { onReact(emoji) }
                        )
                    }
                }
                if (isMine) {
                    Spacer(Modifier.height(16.dp))
                    if (message.type == MessageType.TEXT) {
                        TextButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.messages_edit))
                        }
                    }
                    TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.messages_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    )
}

@Composable
private fun EditMessageDialog(initialText: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initialText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text(stringResource(R.string.messages_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.messages_cancel)) } },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth())
        }
    )
}

@Composable
private fun FullScreenChatPhotoDialog(message: DecryptedMessage, viewModel: MessagesViewModel, onDismiss: () -> Unit) {
    var bitmap by remember(message.id) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(message.id) { bitmap = viewModel.loadPhotoBitmap(message) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            val current = bitmap
            if (current != null) {
                Image(
                    bitmap = current,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().clickable(onClick = onDismiss)
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.4f), MaterialTheme.shapes.small)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Fermer", tint = Color.White)
            }
        }
    }
}
