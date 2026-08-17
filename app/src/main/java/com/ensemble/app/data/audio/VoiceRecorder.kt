package com.ensemble.app.data.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

private const val MIN_DURATION_MS = 500L

/** Enregistre une note vocale chiffrée à la volée dans un fichier temporaire (supprimé après lecture). */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startTime: Long = 0L

    fun start() {
        val file = File.createTempFile("voice_", ".m4a", context.cacheDir)
        outputFile = file
        @Suppress("DEPRECATION")
        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        mr.setAudioSource(MediaRecorder.AudioSource.MIC)
        mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        mr.setOutputFile(file.absolutePath)
        mr.prepare()
        mr.start()
        recorder = mr
        startTime = System.currentTimeMillis()
    }

    /** Arrête l'enregistrement. Retourne les octets + la durée, ou null si trop court/échec. */
    fun stop(): Pair<ByteArray, Long>? {
        val mr = recorder ?: return null
        val file = outputFile
        return try {
            mr.stop()
            mr.release()
            recorder = null
            val duration = System.currentTimeMillis() - startTime
            val bytes = file?.readBytes()
            file?.delete()
            if (bytes != null && duration >= MIN_DURATION_MS) bytes to duration else null
        } catch (e: Exception) {
            recorder = null
            file?.delete()
            null
        }
    }

    fun cancel() {
        runCatching { recorder?.stop() }
        recorder?.release()
        recorder = null
        outputFile?.delete()
        outputFile = null
    }
}
