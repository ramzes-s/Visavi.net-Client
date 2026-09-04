package com.ramzes.visavinet.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ramzes.visavinet.MainActivity
import com.ramzes.visavinet.R
import com.ramzes.visavinet.network.StatsResponse

data class TotalStatsSnapshot(
    val postsTotal: Long = 0L,
    val newsTotal: Long = 0L,
    val photosTotal: Long = 0L,
    val downsTotal: Long = 0L
)

fun detectTotalUpdates(previous: TotalStatsSnapshot?, current: TotalStatsSnapshot): List<String> {
    if (previous == null) return emptyList()
    val updates = mutableListOf<String>()
    if (current.postsTotal > previous.postsTotal) {
        updates.add("Форум +${current.postsTotal - previous.postsTotal}")
    }
    if (current.newsTotal > previous.newsTotal) {
        updates.add("Новости +${current.newsTotal - previous.newsTotal}")
    }
    if (current.photosTotal > previous.photosTotal) {
        updates.add("Галерея +${current.photosTotal - previous.photosTotal}")
    }
    if (current.downsTotal > previous.downsTotal) {
        updates.add("Загрузки +${current.downsTotal - previous.downsTotal}")
    }
    return updates
}

object SiteUpdatesNotificationManager {
    const val CHANNEL_ID = "site_updates_channel"
    const val NOTIFICATION_ID_UPDATES = 1003
    private const val TAG = "SiteUpdatesNotify"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Обновления на сайте"
            val descriptionText = "Уведомления о новых публикациях на форуме, в новостях, галерее и загрузках"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                enableLights(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun checkAndNotify(context: Context, newStats: StatsResponse) {
        val prefs = context.getSharedPreferences("visavi_prefs", Context.MODE_PRIVATE)
        val notificationsEnabled = prefs.getBoolean("notify_site_updates", false)

        val currentSnapshot = TotalStatsSnapshot(
            postsTotal = newStats.sections?.posts?.total ?: 0L,
            newsTotal = newStats.sections?.news?.total ?: 0L,
            photosTotal = newStats.sections?.photos?.total ?: 0L,
            downsTotal = newStats.sections?.downs?.total ?: 0L
        )

        val hasBaseline = prefs.getBoolean("has_total_stats_baseline", false)
        val previousSnapshot = if (hasBaseline) {
            TotalStatsSnapshot(
                postsTotal = prefs.getLong("prev_total_posts", 0L),
                newsTotal = prefs.getLong("prev_total_news", 0L),
                photosTotal = prefs.getLong("prev_total_photos", 0L),
                downsTotal = prefs.getLong("prev_total_downs", 0L)
            )
        } else {
            null
        }

        // Сохраняем текущие total значения как новый baseline
        prefs.edit()
            .putLong("prev_total_posts", currentSnapshot.postsTotal)
            .putLong("prev_total_news", currentSnapshot.newsTotal)
            .putLong("prev_total_photos", currentSnapshot.photosTotal)
            .putLong("prev_total_downs", currentSnapshot.downsTotal)
            .putBoolean("has_total_stats_baseline", true)
            .apply()

        Log.d(TAG, "checkAndNotify: enabled=$notificationsEnabled, previous=$previousSnapshot, current=$currentSnapshot")

        if (!notificationsEnabled || previousSnapshot == null) {
            return
        }

        val updates = detectTotalUpdates(previousSnapshot, currentSnapshot)
        if (updates.isNotEmpty()) {
            Log.d(TAG, "Обнаружены обновления total: $updates. Отправляем уведомление.")
            showNotification(context, updates)
        } else {
            Log.d(TAG, "Нет увеличения значений total по сравнению с предыдущей проверкой.")
        }
    }

    private fun showNotification(context: Context, updates: List<String>) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_UPDATES,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = updates.joinToString(", ")
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(text)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_UPDATES, notification)
    }
}
