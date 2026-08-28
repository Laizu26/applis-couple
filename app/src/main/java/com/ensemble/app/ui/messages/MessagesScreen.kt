package com.ensemble.app.ui.messages

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ensemble.app.R
import com.ensemble.app.data.audio.VoiceRecorder
import com.ensemble.app.data.media.MediaSaver
import com.ensemble.app.data.model.DecryptedMessage
import com.ensemble.app.data.model.MessageType
import com.ensemble.app.notifications.ConversationVisibilityTracker
import com.ensemble.app.ui.components.FormDialog
import com.ensemble.app.ui.components.PhotoSourceMenu
import com.ensemble.app.ui.components.RichTextToolbar
import com.ensemble.app.util.formatDuration
import com.ensemble.app.util.formatMessageTime
import com.ensemble.app.util.parseMarkup
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val QUICK_REACTIONS = listOf("❤️", "😂", "😮", "😢", "👍")

private class PendingPhoto(val bytes: ByteArray) {
    var caption by mutableStateOf("")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(viewModel: MessagesViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val partnerTyping by viewModel.partnerTyping.collectAsStateWithLifecycle()
    val partnerReadTimestamp by viewModel.partnerReadTimestamp.collectAsStateWithLifecycle()
    val partnerOnline by viewModel.partnerOnline.collectAsStateWithLifecycle()
    val partnerLabel by viewModel.partnerLabel.collectAsStateWithLifecycle()
    val playingMessageId by viewModel.playingMessageId.collectAsStateWithLifecycle()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var actionsForMessage by remember { mutableStateOf<DecryptedMessage?>(null) }
    var editingMessage by remember { mutableStateOf<DecryptedMessage?>(null) }
    var deletingMessageId by remember { mutableStateOf<String?>(null) }
    var fullScreenPhoto by remember { mutableStateOf<DecryptedMessage?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var pendingPhotos by remember { mutableStateOf<List<PendingPhoto>>(emptyList()) }

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

    val visibleMessages = remember(uiState.messages, searchQuery) {
        if (searchQuery.isBlank()) uiState.messages
        else uiState.messages.filter { it.text.contains(searchQuery, ignoreCase = true) }
    }

    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris ->
        if (uris.isNotEmpty()) {
            val bytesList = uris.mapNotNull { uri ->
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }
            if (bytesList.isNotEmpty()) pendingPhotos = bytesList.map { PendingPhoto(it) }
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

    DisposableEffect(Unit) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> ConversationVisibilityTracker.setVisible(true)
                Lifecycle.Event.ON_PAUSE -> ConversationVisibilityTracker.setVisible(false)
                else -> {}
            }
        }
        val lifecycle = ProcessLifecycleOwner.get().lifecycle
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            ConversationVisibilityTracker.setVisible(false)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearching) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(stringResource(R.string.messages_search_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Column {
                            Text(partnerLabel ?: stringResource(R.string.messages_title))
                            Text(
                                stringResource(if (partnerOnline) R.string.messages_partner_online else R.string.messages_partner_offline),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (partnerOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (isSearching) searchQuery = ""
                        isSearching = !isSearching
                    }) {
                        Icon(
                            if (isSearching) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = stringResource(R.string.messages_search_hint)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(visibleMessages, key = { it.id }) { message ->
                val isMine = message.senderId == viewModel.currentUserId
                val quoted = viewModel.findMessage(message.replyToId)
                MessageBubble(
                    message = message,
                    isMine = isMine,
                    showSeen = isMine && message.id == myLastMessageId && lastMessageIsSeen,
                    isPlaying = playingMessageId == message.id,
                    quoted = quoted,
                    quotedSenderLabel = quoted?.let {
                        if (it.senderId == viewModel.currentUserId) stringResource(R.string.messages_reply_you)
                        else partnerLabel ?: stringResource(R.string.messages_title)
                    },
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

        uiState.replyingTo?.let { replyMsg ->
            ReplyPreviewBar(
                message = replyMsg,
                senderLabel = if (replyMsg.senderId == viewModel.currentUserId) stringResource(R.string.messages_reply_you)
                    else partnerLabel ?: stringResource(R.string.messages_title),
                onCancel = { viewModel.setReplyingTo(null) }
            )
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
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                if (uiState.draft.text.isNotEmpty()) {
                    RichTextToolbar(value = uiState.draft, onValueChange = viewModel::onDraftChange)
                    Spacer(Modifier.height(4.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PhotoSourceMenu(
                        onGalleryClick = {
                            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onCameraCaptured = { bytes -> pendingPhotos = listOf(PendingPhoto(bytes)) }
                    ) { openMenu ->
                        IconButton(onClick = openMenu) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = stringResource(R.string.photos_add))
                        }
                    }
                    OutlinedTextField(
                        value = uiState.draft,
                        onValueChange = viewModel::onDraftChange,
                        placeholder = { Text(stringResource(R.string.messages_hint)) },
                        shape = MaterialTheme.shapes.extraLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(4.dp))
                    if (uiState.draft.text.isBlank()) {
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
            onReply = {
                viewModel.setReplyingTo(message)
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

    if (pendingPhotos.isNotEmpty()) {
        ComposePhotosDialog(
            photos = pendingPhotos,
            onRemove = { photo -> pendingPhotos = pendingPhotos.filter { it !== photo } },
            onDismiss = { pendingPhotos = emptyList() },
            onSend = {
                viewModel.sendPhotos(pendingPhotos.map { it.bytes to it.caption })
                pendingPhotos = emptyList()
            }
        )
    }
}

@Composable
private fun ReplyPreviewBar(message: DecryptedMessage, senderLabel: String, onCancel: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Reply,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.messages_replying_to, senderLabel),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                replyPreviewText(message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        IconButton(onClick = onCancel, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.messages_cancel), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun replyPreviewText(message: DecryptedMessage): String = when (message.type) {
    MessageType.PHOTO -> "📷 " + (message.text.ifBlank { stringResource(R.string.messages_reply_photo) })
    MessageType.AUDIO -> "🎤 " + stringResource(R.string.messages_reply_audio)
    else -> message.text
}

@Composable
private fun ComposePhotosDialog(
    photos: List<PendingPhoto>,
    onRemove: (PendingPhoto) -> Unit,
    onDismiss: () -> Unit,
    onSend: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(0.94f).heightIn(max = 640.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    stringResource(R.string.messages_send_photos_title, photos.size),
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                ) {
                    photos.forEach { photo ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 6.dp)
                        ) {
                            val bitmap = remember(photo) {
                                BitmapFactory.decodeByteArray(photo.bytes, 0, photo.bytes.size)?.asImageBitmap()
                            }
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            OutlinedTextField(
                                value = photo.caption,
                                onValueChange = { photo.caption = it },
                                placeholder = { Text(stringResource(R.string.photos_caption_hint)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onRemove(photo) }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.messages_delete))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.messages_cancel)) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onSend, enabled = photos.isNotEmpty()) {
                        Text(stringResource(R.string.messages_send))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: DecryptedMessage,
    isMine: Boolean,
    showSeen: Boolean,
    isPlaying: Boolean,
    quoted: DecryptedMessage?,
    quotedSenderLabel: String?,
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
            val bubbleColor = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer
            val onBubbleColor = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
            Column(
                modifier = Modifier
                    .background(
                        color = bubbleColor,
                        shape = MaterialTheme.shapes.large
                    )
                    .combinedClickable(onClick = {}, onLongClick = onLongPress)
                    .padding(if (message.type == MessageType.PHOTO) 6.dp else 14.dp)
                    .widthIn(max = 260.dp)
            ) {
                if (quoted != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(onBubbleColor.copy(alpha = 0.12f), MaterialTheme.shapes.small)
                            .padding(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(onBubbleColor.copy(alpha = 0.6f))
                        )
                        Spacer(Modifier.width(6.dp))
                        Column {
                            if (quotedSenderLabel != null) {
                                Text(
                                    quotedSenderLabel,
                                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                                    color = onBubbleColor
                                )
                            }
                            Text(
                                replyPreviewText(quoted),
                                style = MaterialTheme.typography.bodyMedium,
                                color = onBubbleColor.copy(alpha = 0.85f),
                                maxLines = 2
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }

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
                            parseMarkup(message.text),
                            color = onBubbleColor,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                } else if (message.type == MessageType.AUDIO) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onTogglePlayback, modifier = Modifier.size(32.dp)) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = onBubbleColor
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            formatDuration(message.audioDurationMs ?: 0L),
                            color = onBubbleColor
                        )
                    }
                } else {
                    Text(
                        text = parseMarkup(message.text),
                        color = onBubbleColor
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Text(
                        text = formatMessageTime(message.timestamp) + if (message.editedAt != null) " " + stringResource(R.string.messages_edited) else "",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 10.sp),
                        color = onBubbleColor.copy(alpha = 0.7f)
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
                .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.large)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(stringResource(R.string.messages_typing), color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun MessageActionsDialog(
    message: DecryptedMessage,
    isMine: Boolean,
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
    onReply: () -> Unit,
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
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onReply, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.messages_reply))
                }
                if (isMine) {
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
    var textField by remember { mutableStateOf(TextFieldValue(initialText)) }
    FormDialog(
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.messages_save),
        onConfirm = { onConfirm(textField.text) }
    ) {
        RichTextToolbar(value = textField, onValueChange = { textField = it })
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(value = textField, onValueChange = { textField = it }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun FullScreenChatPhotoDialog(message: DecryptedMessage, viewModel: MessagesViewModel, onDismiss: () -> Unit) {
    var bitmap by remember(message.id) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(message.id) { bitmap = viewModel.loadPhotoBitmap(message) }
    val context = LocalContext.current

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
