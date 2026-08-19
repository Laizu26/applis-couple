package com.ensemble.app.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.ensemble.app.R
import com.ensemble.app.util.CameraCapture
import java.io.File

/**
 * Enveloppe un bouton déclencheur (FAB, IconButton...) avec un petit menu proposant de choisir
 * entre la galerie et l'appareil photo. La capture caméra passe par un fichier temporaire dans
 * le cache privé de l'app (jamais dans la galerie/MediaStore du téléphone) : on lit les octets,
 * on supprime le fichier immédiatement, puis on transmet les octets à l'appelant pour être
 * chiffrés et envoyés, exactement comme une photo choisie dans la galerie.
 */
@Composable
fun PhotoSourceMenu(
    modifier: Modifier = Modifier,
    onGalleryClick: () -> Unit,
    onCameraCaptured: (ByteArray) -> Unit,
    trigger: @Composable (openMenu: () -> Unit) -> Unit
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var pendingFile by remember { mutableStateOf<File?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingFile
        pendingFile = null
        if (success && file != null) {
            val bytes = file.readBytes()
            file.delete()
            onCameraCaptured(bytes)
        } else {
            file?.delete()
        }
    }

    fun launchCamera() {
        val (file, uri) = CameraCapture.newCaptureUri(context)
        pendingFile = file
        cameraLauncher.launch(uri)
    }

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) launchCamera() }

    Box(modifier = modifier) {
        trigger { expanded = true }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.photo_source_gallery)) },
                leadingIcon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                onClick = {
                    expanded = false
                    onGalleryClick()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.photo_source_camera)) },
                leadingIcon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                onClick = {
                    expanded = false
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED
                    if (granted) launchCamera() else cameraPermission.launch(Manifest.permission.CAMERA)
                }
            )
        }
    }
}
