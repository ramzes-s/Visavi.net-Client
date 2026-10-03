package com.ramzes.visavinet

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.ramzes.visavinet.network.GitHubRelease
import com.ramzes.visavinet.network.VisaviApi
import com.ramzes.visavinet.network.isNewerVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

sealed class UpdateCheckState {
    object Idle : UpdateCheckState()
    object Checking : UpdateCheckState()
    data class UpToDate(val version: String, val checkedAt: Long = System.currentTimeMillis()) : UpdateCheckState()
    data class UpdateAvailable(
        val currentVersion: String,
        val newVersion: String,
        val releaseUrl: String,
        val downloadUrl: String?,
        val releaseName: String?,
        val releaseNotes: String? = null,
        val apkSize: Long = 0L
    ) : UpdateCheckState()
    data class Throttled(val remainingSeconds: Long, val message: String) : UpdateCheckState()
    data class Error(val message: String) : UpdateCheckState()
}

sealed class UpdateDownloadState {
    object Idle : UpdateDownloadState()
    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : UpdateDownloadState()
    data class ReadyToInstall(val apkFile: File) : UpdateDownloadState()
    data class Error(val message: String) : UpdateDownloadState()
}

class SettingsViewModel : ViewModel() {

    var apiToken: String? by mutableStateOf(null)
        private set

    var updateCheckState by mutableStateOf<UpdateCheckState>(UpdateCheckState.Idle)
        private set

    var updateDownloadState by mutableStateOf<UpdateDownloadState>(UpdateDownloadState.Idle)
        private set

    var showUpdateDialog by mutableStateOf(false)
        private set

    var remainingCheckSeconds by mutableStateOf(0L)
        private set

    private var downloadJob: Job? = null
    private var downloadCall: Call? = null

    fun updateRemainingCheckTime(context: Context) {
        val prefs = context.getSharedPreferences("visavi_prefs", Context.MODE_PRIVATE)
        val lastCheckTime = prefs.getLong("last_github_update_check_time", 0L)
        if (lastCheckTime <= 0L) {
            remainingCheckSeconds = 0L
            return
        }
        val elapsed = System.currentTimeMillis() - lastCheckTime
        remainingCheckSeconds = if (elapsed < MANUAL_UPDATE_CHECK_INTERVAL_MS) {
            ((MANUAL_UPDATE_CHECK_INTERVAL_MS - elapsed) / 1000L).coerceAtLeast(1L)
        } else {
            0L
        }
    }

    fun loadApiToken(context: Context) {
        val prefs = context.getSharedPreferences("visavi_prefs", Context.MODE_PRIVATE)
        apiToken = prefs.getString("api_key", null)
    }

    fun openUpdateDialog() {
        showUpdateDialog = true
    }

    fun dismissUpdateDialog() {
        showUpdateDialog = false
    }

    fun checkAutoUpdateIfDayPassed(context: Context, currentVersion: String) {
        val prefs = context.getSharedPreferences("visavi_prefs", Context.MODE_PRIVATE)
        if (com.ramzes.visavinet.util.SleepModeHelper.isSleepModeActive(prefs)) {
            return
        }
        val lastCheckTime = prefs.getLong("last_github_update_check_time", 0L)
        val now = System.currentTimeMillis()
        if (now - lastCheckTime >= AUTO_UPDATE_CHECK_INTERVAL_MS) {
            checkForUpdates(context, currentVersion, isAutoCheck = true)
        }
    }

    fun checkAutoUpdateIfWeekPassed(context: Context, currentVersion: String) {
        checkAutoUpdateIfDayPassed(context, currentVersion)
    }

    fun checkForUpdates(
        context: Context,
        currentVersion: String,
        isAutoCheck: Boolean = false
    ) {
        if (updateCheckState is UpdateCheckState.Checking) return

        val prefs = context.getSharedPreferences("visavi_prefs", Context.MODE_PRIVATE)
        val lastCheckTime = prefs.getLong("last_github_update_check_time", 0L)
        val now = System.currentTimeMillis()
        val intervalMs = if (isAutoCheck) AUTO_UPDATE_CHECK_INTERVAL_MS else MANUAL_UPDATE_CHECK_INTERVAL_MS
        val elapsed = now - lastCheckTime

        if (lastCheckTime > 0 && elapsed < intervalMs) {
            if (isAutoCheck) {
                return
            }
            val remainingSec = ((intervalMs - elapsed) / 1000).coerceAtLeast(1)
            val formattedTime = formatRemainingTime(remainingSec)
            updateCheckState = UpdateCheckState.Throttled(
                remainingSeconds = remainingSec,
                message = "Проверка уже выполнялась. Повторите через $formattedTime"
            )
            return
        }

        updateCheckState = UpdateCheckState.Checking

        viewModelScope.launch {
            try {
                val release = withContext(Dispatchers.IO) {
                    val client = OkHttpClient.Builder()
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(15, TimeUnit.SECONDS)
                        .build()

                    val request = Request.Builder()
                        .url("https://api.github.com/repos/ramzes-s/Visavi.net-Client/releases/latest")
                        .header("User-Agent", VisaviApi.USER_AGENT)
                        .header("Accept", "application/vnd.github.v3+json")
                        .build()

                    val response = client.newCall(request).execute()
                    if (!response.isSuccessful) {
                        val code = response.code
                        if (code == 403 || code == 429) {
                            throw Exception("Превышен лимит запросов GitHub. Попробуйте позже.")
                        } else if (code == 404) {
                            throw Exception("Релизы на GitHub не найдены")
                        } else {
                            throw Exception("Ошибка GitHub сервера ($code)")
                        }
                    }

                    val bodyStr = response.body?.string()
                    if (bodyStr.isNullOrBlank()) {
                        throw Exception("Пустой ответ от GitHub")
                    }

                    Gson().fromJson(bodyStr, GitHubRelease::class.java)
                }

                prefs.edit().putLong("last_github_update_check_time", System.currentTimeMillis()).apply()
                withContext(Dispatchers.Main) {
                    updateRemainingCheckTime(context)
                }

                val latestTag = release?.tagName ?: ""
                if (latestTag.isNotBlank() && isNewerVersion(currentVersion, latestTag)) {
                    updateCheckState = UpdateCheckState.UpdateAvailable(
                        currentVersion = currentVersion,
                        newVersion = latestTag,
                        releaseUrl = release.htmlUrl ?: "https://github.com/ramzes-s/Visavi.net-Client/releases",
                        downloadUrl = release.apkDownloadUrl,
                        releaseName = release.name,
                        releaseNotes = release.body,
                        apkSize = release.apkSize
                    )
                    showUpdateDialog = true
                } else {
                    updateCheckState = UpdateCheckState.UpToDate(version = currentVersion)
                }
            } catch (e: Exception) {
                if (!isAutoCheck) {
                    updateCheckState = UpdateCheckState.Error(
                        message = e.message ?: "Не удалось проверить обновления"
                    )
                } else {
                    updateCheckState = UpdateCheckState.Idle
                }
            }
        }
    }

    fun startDownloadAndInstall(
        context: Context,
        downloadUrl: String,
        newVersion: String
    ) {
        if (updateDownloadState is UpdateDownloadState.Downloading) return

        cancelDownload()

        downloadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    updateDownloadState = UpdateDownloadState.Downloading(
                        progress = 0f,
                        downloadedBytes = 0L,
                        totalBytes = 0L
                    )
                }

                val baseDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
                val updatesDir = File(baseDir, "updates").apply { mkdirs() }
                // Очищаем старые файлы обновлений
                updatesDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.name.endsWith(".apk")) {
                        file.delete()
                    }
                }

                val cleanVersion = newVersion.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val targetFile = File(updatesDir, "VisaviClient-$cleanVersion.apk")

                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .build()

                val request = Request.Builder()
                    .url(downloadUrl)
                    .header("User-Agent", VisaviApi.USER_AGENT)
                    .header("Accept", "application/octet-stream")
                    .build()

                val call = client.newCall(request)
                downloadCall = call

                val response = call.execute()
                if (!response.isSuccessful) {
                    throw Exception("Сервер вернул ошибку: ${response.code}")
                }

                val body = response.body ?: throw Exception("Пустой ответ от сервера")
                val totalBytes = body.contentLength()
                var downloadedBytes = 0L

                body.byteStream().use { input ->
                    targetFile.outputStream().use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead: Int
                        var lastUpdateTime = 0L
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            val now = System.currentTimeMillis()
                            if (now - lastUpdateTime > 100 || (totalBytes > 0 && downloadedBytes == totalBytes)) {
                                lastUpdateTime = now
                                val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else -1f
                                withContext(Dispatchers.Main) {
                                    updateDownloadState = UpdateDownloadState.Downloading(
                                        progress = progress,
                                        downloadedBytes = downloadedBytes,
                                        totalBytes = totalBytes
                                    )
                                }
                            }
                        }
                        output.flush()
                    }
                }

                if (totalBytes > 0 && targetFile.length() < totalBytes) {
                    throw Exception("Файл обновления загружен не полностью")
                }
                targetFile.setReadable(true, false)

                withContext(Dispatchers.Main) {
                    updateDownloadState = UpdateDownloadState.ReadyToInstall(targetFile)
                    installApk(context, targetFile)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    withContext(Dispatchers.Main) {
                        updateDownloadState = UpdateDownloadState.Idle
                    }
                    return@launch
                }
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    updateDownloadState = UpdateDownloadState.Error(
                        e.localizedMessage ?: "Ошибка при скачивании обновления"
                    )
                }
            } finally {
                downloadCall = null
            }
        }
    }

    fun cancelDownload() {
        downloadCall?.cancel()
        downloadJob?.cancel()
        downloadCall = null
        downloadJob = null
        updateDownloadState = UpdateDownloadState.Idle
    }

    fun installApk(context: Context, apkFile: File) {
        try {
            if (!apkFile.exists()) {
                Toast.makeText(context, "Файл обновления не найден", Toast.LENGTH_SHORT).show()
                return
            }

            // Проверка разрешения на установку пакетов для Android 8.0+ (API 26+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    Toast.makeText(
                        context,
                        "Пожалуйста, разрешите установку приложений для Visavi.net",
                        Toast.LENGTH_LONG
                    ).show()
                    return
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(installIntent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                context,
                "Ошибка запуска установки: ${e.localizedMessage ?: "неизвестная ошибка"}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    companion object {
        const val AUTO_UPDATE_CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000L // 24 часа для автоматической проверки
        const val MANUAL_UPDATE_CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000L // 6 часов для ручной проверки
        const val UPDATE_CHECK_INTERVAL_MS = AUTO_UPDATE_CHECK_INTERVAL_MS // Для обратной совместимости

        fun formatRemainingTime(remainingSec: Long): String {
            val hours = remainingSec / 3600
            val minutes = (remainingSec % 3600) / 60
            val seconds = remainingSec % 60
            return when {
                hours > 0 && minutes > 0 -> "$hours ч. $minutes мин."
                hours > 0 -> "$hours ч."
                minutes > 0 -> "$minutes мин."
                else -> "$seconds сек."
            }
        }
    }
}
