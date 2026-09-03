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

data class TodayStatsSnapshot(
    val postsToday: Long = 0L,
    val newsToday: Long = 0L,
    val photosToday: Long = 0L,
    val downsToday: Long = 0L
)

fun detectTodayUpdates(previous: TodayStatsSnapshot?, current: TodayStatsSnapshot): List<String> {
    if (previous == null) return emptyList()
    val updates = mutableListOf<String>()
    if (current.postsToday > previous.postsToday) {
        updates.add("Форум (+${current.postsToday - previous.postsToday})")
    }
    if (current.newsToday > previous.newsToday) {
        updates.add("Новости (+${current.newsToday - previous.newsToday})")
    }
    if (current.photosToday > previous.photosToday) {
        updates.add("Галерея (+${current.photosToday - previous.photosToday})")
    }
    if (current.downsToday > previous.downsToday) {
        updates.add("Загрузки (+${current.downsToday - previous.downsToday})")
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

        val currentSnapshot = TodayStatsSnapshot(
            postsToday = newStats.sections?.posts?.today ?: 0L,
            newsToday = newStats.sections?.news?.today ?: 0L,
            photosToday = newStats.sections?.photos?.today ?: 0L,
            downsToday = newStats.sections?.downs?.today ?: 0L
        )

        val hasBaseline = prefs.getBoolean("has_stats_baseline", false)
        val previousSnapshot = if (hasBaseline) {
            TodayStatsSnapshot(
                postsToday = prefs.getLong("prev_today_posts", 0L),
                newsToday = prefs.getLong("prev_today_news", 0L),
                photosToday = prefs.getLong("prev_today_photos", 0L),
                downsToday = prefs.getLong("prev_today_downs", 0L)
            )
        } else {
            null
        }

        // Сохраняем текущие значения как новый baseline
        prefs.edit()
            .putLong("prev_today_posts", currentSnapshot.postsToday)
            .putLong("prev_today_news", currentSnapshot.newsToday)
            .putLong("prev_today_photos", currentSnapshot.photosToday)
            .putLong("prev_today_downs", currentSnapshot.downsToday)
            .putBoolean("has_stats_baseline", true)
            .apply()

        Log.d(TAG, "checkAndNotify: enabled=$notificationsEnabled, previous=$previousSnapshot, current=$currentSnapshot")

        if (!notificationsEnabled || previousSnapshot == null) {
            return
        }

        val updates = detectTodayUpdates(previousSnapshot, currentSnapshot)
        if (updates.isNotEmpty()) {
            Log.d(TAG, "Обнаружены обновления: $updates. Отправляем уведомление.")
            showNotification(context, updates)
        } else {
            Log.d(TAG, "Нет новых обновлений today по сравнению с предыдущей проверкой.")
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

        val contentText = updates.joinToString(", ")
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle("Новые материалы на Visavi.net")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_UPDATES, notification)
    }
}
