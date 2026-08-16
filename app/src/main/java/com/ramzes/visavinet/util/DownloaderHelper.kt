package com.ramzes.visavinet.util

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast

object DownloaderHelper {

    fun sanitizeFileName(name: String?): String {
        if (name.isNullOrBlank()) {
            return "download_${System.currentTimeMillis()}"
        }
        // Удаляем пути, относительные ссылки (../, ..\), спецсимволы и недопустимые знаки
        val cleaned = name
            .replace("\\", "/")
            .substringAfterLast("/")
            .replace(Regex("[\\\\/:*?\"<>|\u0000-\u001F]"), "_")
            .trim('.', ' ', '_')

        return if (cleaned.isBlank()) {
            "download_${System.currentTimeMillis()}"
        } else {
            cleaned.take(128)
        }
    }

    fun downloadFile(
        context: Context,
        url: String,
        fileName: String? = null,
        mimeType: String? = null
    ) {
        try {
            val uri = Uri.parse(url)
            val rawName = fileName?.ifBlank { null } ?: uri.lastPathSegment
            val effectiveFileName = sanitizeFileName(rawName)

            val request = DownloadManager.Request(uri).apply {
                setTitle(effectiveFileName)
                setDescription("Загрузка с Visavi.net")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, effectiveFileName)
                if (!mimeType.isNullOrBlank()) {
                    setMimeType(mimeType)
                }
            }

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)

            Toast.makeText(context, "Загрузка началась: $effectiveFileName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Ошибка загрузки: ${e.localizedMessage ?: "не удалось начать скачивание"}", Toast.LENGTH_LONG).show()
        }
    }
}
