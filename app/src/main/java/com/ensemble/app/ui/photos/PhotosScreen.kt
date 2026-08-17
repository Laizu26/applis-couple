package com.ensemble.app.ui.photos

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ensemble.app.R
import com.ensemble.app.data.model.CouplePhoto

@Composable
fun PhotosScreen(viewModel: PhotosViewModel) {
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val isUploading by viewModel.isUploading.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var fullScreenPhoto by remember { mutableStateOf<CouplePhoto?>(null) }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) viewModel.addPhoto(bytes)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (photos.isEmpty()) {
            Text(
                stringResource(R.string.photos_empty),
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.bodyLarge
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(photos, key = { it.id }) { photo ->
                    PhotoThumbnail(photo, viewModel) { fullScreenPhoto = photo }
                }
            }
        }

        FloatingActionButton(
            onClick = { pickMedia.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            if (isUploading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.photos_add))
            }
        }
    }

    fullScreenPhoto?.let { photo ->
        FullScreenPhotoDialog(photo, viewModel, onDismiss = { fullScreenPhoto = null })
    }
}

@Composable
private fun PhotoThumbnail(photo: CouplePhoto, viewModel: PhotosViewModel, onClick: () -> Unit) {
    var bitmap by remember(photo.id) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(photo.id) { bitmap = viewModel.loadBitmap(photo) }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
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

@Composable
private fun FullScreenPhotoDialog(photo: CouplePhoto, viewModel: PhotosViewModel, onDismiss: () -> Unit) {
    var bitmap by remember(photo.id) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(photo.id) { bitmap = viewModel.loadBitmap(photo) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            val current = bitmap
            if (current != null) {
                Image(bitmap = current, contentDescription = null, modifier = Modifier.fillMaxSize())
            } else {
                CircularProgressIndicator()
            }
        }
    }
}
