package com.ensemble.app.ui.journal

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Today
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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ensemble.app.R
import com.ensemble.app.data.media.MediaSaver
import com.ensemble.app.data.model.DecryptedJournalDay
import com.ensemble.app.data.model.JournalPhotoRef
import com.ensemble.app.ui.components.FormDialog
import com.ensemble.app.ui.components.PhotoSourceMenu
import com.ensemble.app.ui.components.RichTextToolbar
import com.ensemble.app.ui.components.ZoomableImage
import com.ensemble.app.util.formatDate
import com.ensemble.app.util.localDateToPickerMillis
import com.ensemble.app.util.parseMarkup
import com.ensemble.app.util.pickerMillisToLocalDate
import com.ensemble.app.util.toStartOfDayMillis
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private enum class SectionKind { MINE, COMMON }
private data class EditTarget(val dateKey: String, val dateMillis: Long, val kind: SectionKind, val initialText: String)
private data class PhotoViewTarget(
    val dateKey: String,
    val field: String,
    val photos: List<JournalPhotoRef>,
    val initialIndex: Int,
    val canRemove: Boolean
)

private fun emptyDay(dateKey: String, dateMillis: Long) = DecryptedJournalDay(
    id = dateKey,
    dateMillis = dateMillis,
    myText = "",
    partnerText = "",
    commonText = "",
    myPhotos = emptyList(),
    partnerPhotos = emptyList(),
    commonPhotos = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun JournalScreen(viewModel: JournalViewModel, onBack: () -> Unit = {}) {
    val days by viewModel.days.collectAsStateWithLifecycle()
    val partnerLabel by viewModel.partnerLabel.collectAsStateWithLifecycle()
    val daysByKey = remember(days) { days.associateBy { it.id } }
    val scope = rememberCoroutineScope()

    val anchorDate = remember { LocalDate.now().minusYears(10) }
    val lastDate = remember { LocalDate.now().plusYears(5) }
    val pageCount = remember { ChronoUnit.DAYS.between(anchorDate, lastDate).toInt() + 1 }
    val todayPageIndex = remember { ChronoUnit.DAYS.between(anchorDate, LocalDate.now()).toInt() }
    val pagerState = rememberPagerState(initialPage = todayPageIndex) { pageCount }

    val currentDate by remember { derivedStateOf { anchorDate.plusDays(pagerState.currentPage.toLong()) } }
    val currentDateKey = currentDate.toDateKey()
    val currentDateMillis = currentDate.toStartOfDayMillis()
    val currentDay = daysByKey[currentDateKey] ?: emptyDay(currentDateKey, currentDateMillis)
    val partnerLabelResolved = partnerLabel?.takeIf { it.isNotBlank() } ?: stringResource(R.string.journal_partner_generic)

    var editingSection by remember { mutableStateOf<EditTarget?>(null) }
    var photoViewTarget by remember { mutableStateOf<PhotoViewTarget?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(formatDate(currentDateMillis)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                },
                actions = {
                    if (pagerState.currentPage != todayPageIndex) {
                        IconButton(onClick = { scope.launch { pagerState.animateScrollToPage(todayPageIndex) } }) {
                            Icon(Icons.Default.Today, contentDescription = stringResource(R.string.journal_today))
                        }
                    }
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = stringResource(R.string.journal_pick_date))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editingSection = EditTarget(currentDateKey, currentDateMillis, SectionKind.MINE, currentDay.myText)
            }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.journal_add))
            }
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) { page ->
            val pageDate = anchorDate.plusDays(page.toLong())
            val pageKey = pageDate.toDateKey()
            val pageMillis = pageDate.toStartOfDayMillis()
            val day = daysByKey[pageKey] ?: emptyDay(pageKey, pageMillis)
            JournalPage(
                day = day,
                partnerLabel = partnerLabelResolved,
                loadBitmap = { photo -> viewModel.loadPhotoBitmap(photo) },
                onEditMine = { editingSection = EditTarget(day.id, day.dateMillis, SectionKind.MINE, day.myText) },
                onEditCommon = { editingSection = EditTarget(day.id, day.dateMillis, SectionKind.COMMON, day.commonText) },
                onAddMyPhoto = { bytes -> viewModel.addMyPhoto(day.id, day.dateMillis, bytes) },
                onAddCommonPhoto = { bytes -> viewModel.addCommonPhoto(day.id, day.dateMillis, bytes) },
                onPhotoClick = { field, photos, index ->
                    photoViewTarget = PhotoViewTarget(day.id, field, photos, index, canRemove = field != "partner")
                }
            )
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = localDateToPickerMillis(currentDate),
            selectableDates = remember(anchorDate, lastDate) {
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                        val date = pickerMillisToLocalDate(utcTimeMillis)
                        return !date.isBefore(anchorDate) && !date.isAfter(lastDate)
                    }
                }
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showDatePicker = false
                    val millis = datePickerState.selectedDateMillis
                    if (millis != null) {
                        val picked = pickerMillisToLocalDate(millis)
                        val target = ChronoUnit.DAYS.between(anchorDate, picked).toInt().coerceIn(0, pageCount - 1)
                        scope.launch { pagerState.scrollToPage(target) }
                    }
                }) { Text(stringResource(R.string.pairing_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.messages_cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
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

    photoViewTarget?.let { target ->
        FullScreenJournalPhotoPagerDialog(
            target = target,
            loadBitmap = { photo -> viewModel.loadPhotoBitmap(photo) },
            onDismiss = { photoViewTarget = null },
            onRemove = { photo ->
                when (target.field) {
                    "common" -> viewModel.removeCommonPhoto(target.dateKey, photo)
                    "mine" -> viewModel.removeMyPhoto(target.dateKey, photo)
                }
            }
        )
    }
}

@Composable
private fun JournalPage(
    day: DecryptedJournalDay,
    partnerLabel: String,
    loadBitmap: suspend (JournalPhotoRef) -> ImageBitmap?,
    onEditMine: () -> Unit,
    onEditCommon: () -> Unit,
    onAddMyPhoto: (ByteArray) -> Unit,
    onAddCommonPhoto: (ByteArray) -> Unit,
    onPhotoClick: (field: String, photos: List<JournalPhotoRef>, index: Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        PassageSection(
            label = stringResource(R.string.journal_section_mine),
            text = day.myText,
            photos = day.myPhotos,
            onEditText = onEditMine,
            onAddPhoto = onAddMyPhoto,
            loadBitmap = loadBitmap,
            onPhotoClick = { index -> onPhotoClick("mine", day.myPhotos, index) }
        )
        Spacer(Modifier.height(14.dp))
        PassageSection(
            label = partnerLabel,
            text = day.partnerText,
            photos = day.partnerPhotos,
            onEditText = null,
            onAddPhoto = null,
            loadBitmap = loadBitmap,
            onPhotoClick = { index -> onPhotoClick("partner", day.partnerPhotos, index) }
        )
        Spacer(Modifier.height(14.dp))
        PassageSection(
            label = stringResource(R.string.journal_section_common),
            text = day.commonText,
            photos = day.commonPhotos,
            onEditText = onEditCommon,
            onAddPhoto = onAddCommonPhoto,
            loadBitmap = loadBitmap,
            onPhotoClick = { index -> onPhotoClick("common", day.commonPhotos, index) }
        )
    }
}

@Composable
private fun PassageSection(
    label: String,
    text: String,
    photos: List<JournalPhotoRef>,
    onEditText: (() -> Unit)?,
    onAddPhoto: ((ByteArray) -> Unit)?,
    loadBitmap: suspend (JournalPhotoRef) -> ImageBitmap?,
    onPhotoClick: (index: Int) -> Unit
) {
    Column {
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
            if (onEditText != null) {
                IconButton(onClick = onEditText, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.messages_edit), modifier = Modifier.size(18.dp))
                }
            }
        }

        if (photos.isNotEmpty() || onAddPhoto != null) {
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                photos.forEachIndexed { index, photo ->
                    PhotoThumb(photo, loadBitmap, onClick = { onPhotoClick(index) })
                }
                if (onAddPhoto != null) {
                    AddPhotoThumb(onAddPhoto)
                }
            }
        }
    }
}

@Composable
private fun PhotoThumb(photo: JournalPhotoRef, loadBitmap: suspend (JournalPhotoRef) -> ImageBitmap?, onClick: () -> Unit) {
    var bitmap by remember(photo.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(photo.id) { bitmap = loadBitmap(photo) }

    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = bitmap != null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val current = bitmap
        if (current != null) {
            Image(bitmap = current, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun AddPhotoThumb(onAdd: (ByteArray) -> Unit) {
    val context = LocalContext.current
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) onAdd(bytes)
        }
    }
    PhotoSourceMenu(
        onGalleryClick = { pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onCameraCaptured = onAdd
    ) { openMenu ->
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = openMenu),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.journal_add_photo))
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FullScreenJournalPhotoPagerDialog(
    target: PhotoViewTarget,
    loadBitmap: suspend (JournalPhotoRef) -> ImageBitmap?,
    onDismiss: () -> Unit,
    onRemove: (JournalPhotoRef) -> Unit
) {
    if (target.photos.isEmpty()) {
        onDismiss()
        return
    }
    val pagerState = rememberPagerState(initialPage = target.initialIndex.coerceIn(0, target.photos.lastIndex)) { target.photos.size }
    val context = LocalContext.current
    val photo = target.photos[pagerState.currentPage.coerceIn(0, target.photos.lastIndex)]
    var bitmap by remember(photo.id) { mutableStateOf<ImageBitmap?>(null) }
    var showRemoveConfirm by remember { mutableStateOf(false) }
    LaunchedEffect(photo.id) { bitmap = loadBitmap(photo) }

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
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val pagePhoto = target.photos[page]
                var pageBitmap by remember(pagePhoto.id) { mutableStateOf<ImageBitmap?>(null) }
                LaunchedEffect(pagePhoto.id) { pageBitmap = loadBitmap(pagePhoto) }
                val current = pageBitmap
                if (current != null) {
                    ZoomableImage(bitmap = current, onTap = onDismiss, modifier = Modifier.fillMaxSize())
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }

            Row(modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                IconButton(
                    onClick = {
                        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                            PackageManager.PERMISSION_GRANTED
                        if (needsPermission) storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else downloadPhoto()
                    },
                    enabled = bitmap != null,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), MaterialTheme.shapes.small)
                ) {
                    Icon(Icons.Default.Download, contentDescription = stringResource(R.string.photos_download), tint = Color.White)
                }
                if (target.canRemove) {
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { showRemoveConfirm = true },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), MaterialTheme.shapes.small)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.messages_delete), tint = Color.White)
                    }
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
            text = { Text(stringResource(R.string.journal_remove_photo_explain)) },
            confirmButton = {
                TextButton(onClick = {
                    showRemoveConfirm = false
                    onRemove(photo)
                    if (target.photos.size <= 1) onDismiss()
                }) { Text(stringResource(R.string.messages_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) { Text(stringResource(R.string.messages_cancel)) }
            }
        )
    }
}
