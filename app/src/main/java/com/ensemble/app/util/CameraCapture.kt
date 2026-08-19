package com.ensemble.app.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Crée un fichier temporaire dans le cache privé de l'app (jamais dans le stockage public /
 * MediaStore) pour recevoir un cliché pris avec l'appareil photo. Le fichier n'est accessible
 * qu'à l'app elle-même et à l'appli caméra le temps de la capture (via FileProvider), et doit
 * être supprimé par l'appelant une fois la photo lue et chiffrée.
 */
object CameraCapture {
    fun newCaptureUri(context: Context): Pair<File, Uri> {
        val dir = File(context.cacheDir, "camera_captures").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return file to uri
    }
}
