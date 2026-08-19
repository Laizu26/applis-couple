package com.ensemble.app.ui.update

import com.ensemble.app.BuildConfig
import com.ensemble.app.data.AppContainer
import com.ensemble.app.data.model.AppUpdateInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class UpdateViewModel(private val container: AppContainer) : ViewModel() {

    private val _availableUpdate = MutableStateFlow<AppUpdateInfo?>(null)
    val availableUpdate: StateFlow<AppUpdateInfo?> = _availableUpdate

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading

    private val _downloadedApk = MutableStateFlow<File?>(null)
    val downloadedApk: StateFlow<File?> = _downloadedApk

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    init {
        viewModelScope.launch {
            container.updateRepository.observeLatestUpdate().collect { info ->
                _availableUpdate.value = info?.takeIf { it.versionCode > BuildConfig.VERSION_CODE && it.apkStoragePath.isNotBlank() }
            }
        }
    }

    fun downloadUpdate() {
        val info = _availableUpdate.value ?: return
        if (_isDownloading.value) return
        _isDownloading.value = true
        viewModelScope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.IO) { container.updateRepository.downloadApk(info.apkStoragePath) }
                val dir = File(container.appContext.cacheDir, "app_updates").apply { mkdirs() }
                val file = File(dir, "mon-amour-${info.versionName}.apk")
                withContext(Dispatchers.IO) { file.writeBytes(bytes) }
                file
            }.onSuccess { file ->
                _downloadedApk.value = file
            }.onFailure {
                _errorMessage.value = "Échec du téléchargement de la mise à jour"
            }
            _isDownloading.value = false
        }
    }

    fun consumeDownloadedApk() {
        _downloadedApk.value = null
    }

    fun dismissError() {
        _errorMessage.value = null
    }
}
