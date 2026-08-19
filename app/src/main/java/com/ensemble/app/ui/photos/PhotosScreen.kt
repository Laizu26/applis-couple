package com.ensemble.app.ui.photos

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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ensemble.app.R
import com.ensemble.app.data.media.MediaSaver
import com.ensemble.app.data.model.CouplePhoto
import com.ensemble.app.ui.components.PhotoSourceMenu

@Composable
fun PhotosScreen(viewModel: PhotosViewModel) {
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val uploadCount by viewModel.uploadCount.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var fullScreenIndex by remember { mutableStateOf<Int?>(null) }

    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
    ) { uris ->
        if (uris.isNotEmpty()) {
            val bytesList = uris.mapNotNull { uri ->
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }
            if (bytesList.isNotEmpty()) viewModel.addPhotos(bytesList)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (photos.isEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.photos_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(photos, key = { _, item -> item.id }) { index, photo ->
                    PhotoThumbnail(photo, viewModel) { fullScreenIndex = index }
                }
            }
        }

        PhotoSourceMenu(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            onGalleryClick = { pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onCameraCaptured = { bytes -> viewModel.addPhotos(listOf(bytes)) }
        ) { openMenu ->
            FloatingActionButton(onClick = openMenu) {
                if (uploadCount > 0) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.photos_add))
                }
            }
        }
    }

    fullScreenIndex?.let { index ->
        FullScreenPhotoPagerDialog(photos, index, viewModel, onDismiss = { fullScreenIndex = null })
    }
}

@Composable
private fun PhotoThumbnail(photo: CouplePhoto, viewModel: PhotosViewModel, onClick: () -> Unit) {
    var bitmap by remember(photo.id) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(photo.id) { bitmap = viewModel.loadBitmap(photo) }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = bitmap != null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val current = bitmap
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FullScreenPhotoPagerDialog(
    photos: List<CouplePhoto>,
    initialIndex: Int,
    viewModel: PhotosViewModel,
    onDismiss: () -> Unit
) {
    if (photos.isEmpty()) {
        onDismiss()
        return
    }
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, photos.lastIndex)
    ) { photos.size }
    val context = LocalContext.current
    val photo = photos[pagerState.currentPage.coerceIn(0, photos.lastIndex)]

    var bitmap by remember(photo.id) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var isEditingCaption by remember(photo.id) { mutableStateOf(false) }
    var captionDraft by remember(photo.id) { mutableStateOf(viewModel.decryptCaption(photo).orEmpty()) }
    var showDeleteConfirm by remember(photo.id) { mutableStateOf(false) }

    LaunchedEffect(photo.id) { bitmap = viewModel.loadBitmap(photo) }

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
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val pagePhoto = photos[page]
                var pageBitmap by remember(pagePhoto.id) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
                LaunchedEffect(pagePhoto.id) { pageBitmap = viewModel.loadBitmap(pagePhoto) }
                val current = pageBitmap
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
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
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
                    onClick = { showDeleteConfirm = true },
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

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(16.dp)
            ) {
                if (isEditingCaption) {
                    OutlinedTextField(
                        value = captionDraft,
                        onValueChange = { captionDraft = it },
                        placeholder = { Text(stringResource(R.string.photos_caption_hint), color = Color.White.copy(alpha = 0.6f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { isEditingCaption = false }) {
                            Text(stringResource(R.string.messages_cancel), color = Color.White)
                        }
                        TextButton(onClick = {
                            viewModel.setCaption(photo, captionDraft)
                            isEditingCaption = false
                        }) { Text(stringResource(R.string.photos_caption_save)) }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = captionDraft.ifBlank { stringResource(R.string.photos_caption_hint) },
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { isEditingCaption = true }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.photos_caption_save), tint = Color.White)
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.photos_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePhoto(photo)
                    showDeleteConfirm = false
                    onDismiss()
                }) { Text(stringResource(R.string.messages_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.messages_cancel)) }
            }
        )
    }
}
