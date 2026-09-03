package com.ramzes.visavinet

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.ramzes.visavinet.network.GitHubRelease
import com.ramzes.visavinet.network.isNewerVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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
        val releaseName: String?
    ) : UpdateCheckState()
    data class Throttled(val remainingSeconds: Long, val message: String) : UpdateCheckState()
    data class Error(val message: String) : UpdateCheckState()
}

class SettingsViewModel : ViewModel() {

    var apiToken: String? by mutableStateOf(null)
        private set

    var updateCheckState by mutableStateOf<UpdateCheckState>(UpdateCheckState.Idle)
        private set

    fun loadApiToken(context: Context) {
        val prefs = context.getSharedPreferences("visavi_prefs", Context.MODE_PRIVATE)
        apiToken = prefs.getString("api_key", null)
    }

    fun checkAutoUpdateIfDayPassed(context: Context, currentVersion: String) {
        val prefs = context.getSharedPreferences("visavi_prefs", Context.MODE_PRIVATE)
        val lastCheckTime = prefs.getLong("last_github_update_check_time", 0L)
        val now = System.currentTimeMillis()
        val dayInMs = 24 * 60 * 60 * 1000L
        if (now - lastCheckTime >= dayInMs) {
            checkForUpdates(context, currentVersion)
        }
    }

    fun checkForUpdates(context: Context, currentVersion: String) {
        if (updateCheckState is UpdateCheckState.Checking) return

        val prefs = context.getSharedPreferences("visavi_prefs", Context.MODE_PRIVATE)
        val lastCheckTime = prefs.getLong("last_github_update_check_time", 0L)
        val now = System.currentTimeMillis()
        val intervalMs = UPDATE_CHECK_INTERVAL_MS
        val elapsed = now - lastCheckTime

        if (lastCheckTime > 0 && elapsed < intervalMs) {
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
                        .header("User-Agent", "VisaviClient")
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

                val latestTag = release?.tagName ?: ""
                if (latestTag.isNotBlank() && isNewerVersion(currentVersion, latestTag)) {
                    updateCheckState = UpdateCheckState.UpdateAvailable(
                        currentVersion = currentVersion,
                        newVersion = latestTag,
                        releaseUrl = release.htmlUrl ?: "https://github.com/ramzes-s/Visavi.net-Client/releases",
                        downloadUrl = release.apkDownloadUrl,
                        releaseName = release.name
                    )
                } else {
                    updateCheckState = UpdateCheckState.UpToDate(version = currentVersion)
                }
            } catch (e: Exception) {
                updateCheckState = UpdateCheckState.Error(
                    message = e.message ?: "Не удалось проверить обновления"
                )
            }
        }
    }

    companion object {
        const val UPDATE_CHECK_INTERVAL_MS = 1 * 60 * 1000L // 1 минута (временно, для тестов)

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
