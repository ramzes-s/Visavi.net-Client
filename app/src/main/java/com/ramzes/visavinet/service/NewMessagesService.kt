package com.ramzes.visavinet.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ramzes.visavinet.MainActivity
import com.ramzes.visavinet.R
import com.ramzes.visavinet.network.NewMessageInfo
import com.ramzes.visavinet.network.VisaviApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Сервис фонового мониторинга новых сообщений
 * Проверяет новые сообщения каждые 30 секунд
 */
class NewMessagesService : Service() {

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "new_messages_channel"
        private const val NOTIFICATION_CHANNEL_ID_PERSISTENT = "service_channel"
        private const val NOTIFICATION_ID_PERSISTENT = 1001  // Постоянное уведомление сервиса
        private const val NOTIFICATION_ID_MESSAGES = 1002   // Уведомление о новых сообщениях
        private const val CHECK_INTERVAL_MS = 30_000L // 30 секунд

        private val _newMessagesCount = MutableStateFlow(0)
        val newMessagesCount: StateFlow<Int> = _newMessagesCount.asStateFlow()

        private var serviceInstance: NewMessagesService? = null
        private var lastNotificationCount = 0
        // Время последнего сообщения, для которого показали уведомление
        private var lastNotifiedMessageTime: Long = 0
        // Флаг для предотвращения повторного запуска мониторинга
        private var isMonitoring = false

        fun start(context: Context) {
            val intent = Intent(context, NewMessagesService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, NewMessagesService::class.java)
            context.stopService(intent)
        }

        fun isRunning(): Boolean = serviceInstance != null
        
        /**
         * Сбросить время последнего уведомления (после прочтения сообщений)
         */
        fun markAsRead() {
            lastNotifiedMessageTime = 0
        }
    }

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onCreate() {
        super.onCreate()
        serviceInstance = this
        createNotificationChannel()
        createPersistentNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val token = getApiToken()

        if (token == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Устанавливаем токен для всех запросов API
        com.ramzes.visavinet.network.VisaviApi.setToken(token)

        // Запускаем как foreground сервис с постоянным уведомлением
        val notification = createPersistentNotification()
        startForeground(NOTIFICATION_ID_PERSISTENT, notification)

        startMonitoring()

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceInstance = null
        serviceJob.cancel()
        _newMessagesCount.value = 0
        lastNotificationCount = 0
        lastNotifiedMessageTime = 0
        isMonitoring = false

        // Убираем оба уведомления при остановке сервиса
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.cancel(NOTIFICATION_ID_PERSISTENT)
        notificationManager.cancel(NOTIFICATION_ID_MESSAGES)
        notificationManager.deleteNotificationChannel(NOTIFICATION_CHANNEL_ID_PERSISTENT)
        notificationManager.deleteNotificationChannel(NOTIFICATION_CHANNEL_ID)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Новые сообщения",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления о новых сообщениях"
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Канал для постоянного уведомления сервиса (скрытый, минимальная важность)
     */
    private fun createPersistentNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID_PERSISTENT,
                "Сервис",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Фоновый сервис"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Постоянное уведомление для foreground сервиса (минимально заметное)
     */
    private fun createPersistentNotification(): android.app.Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID_PERSISTENT)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle("")
            .setContentText("")
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)

        // На Android 11+ скрываем уведомление в shade
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
        }

        return builder.build()
    }

    /**
     * Уведомление о новых сообщениях (показывается поверх постоянного)
     */
    private fun showNotification(count: Int, dialogues: List<NewMessageInfo>) {
        if (count == 0) {
            // Убираем уведомление, если сообщений нет
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.cancel(NOTIFICATION_ID_MESSAGES)
            return
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("OPEN_DIALOGUES", true)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Формируем текст уведомления
        val title = when {
            count == 1 -> "Новое сообщение"
            count in 2..4 -> "Новых сообщения"
            else -> "Новых сообщений"
        }

        // Получаем имена отправителей для превью
        val sendersPreview = dialogues.take(3).mapNotNull { it.name ?: it.login }.joinToString(", ")
        val subtitle = if (dialogues.size > 3) {
            "$sendersPreview и ещё ${dialogues.size - 3}"
        } else {
            sendersPreview
        }

        // Создаём стиль InboxStyle
        val inboxStyle = NotificationCompat.InboxStyle()
        dialogues.take(7).forEach { dialogue ->
            val name = dialogue.name ?: dialogue.login ?: "Неизвестно"
            val msgCount = dialogue.count
            inboxStyle.addLine("$msgCount от $name")
        }
        inboxStyle.setSummaryText("$title: $count")

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle("$count $title")
            .setContentText(subtitle)
            .setStyle(inboxStyle)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID_MESSAGES, notification)

        lastNotificationCount = count
    }

    private fun startMonitoring() {
        // Предотвращаем повторный запуск мониторинга
        if (isMonitoring) {
            android.util.Log.d("NewMessagesService", "Мониторинг уже запущен, пропускаем повторный запуск")
            return
        }
        isMonitoring = true

        serviceScope.launch {
            try {
                while (isActive) {
                    try {
                        val response = VisaviApi.instance.getNewMessages()

                        if (response.isSuccessful) {
                            val body = response.body()
                            val count = body?.count ?: 0
                            val dialogues = body?.dialogues ?: emptyList()

                            val oldCount = _newMessagesCount.value
                            android.util.Log.d("NewMessagesService", "Проверка: count=$count, oldCount=$oldCount")

                            _newMessagesCount.value = count

                            // Находим самое новое сообщение по времени
                            val latestMessageTime = dialogues.maxOfOrNull { it.lastMessageAt ?: 0L } ?: 0L
                            android.util.Log.d("NewMessagesService", "lastMessageAt=$latestMessageTime, lastNotifiedMessageTime=$lastNotifiedMessageTime")

                            // Показываем уведомление если:
                            // 1. Есть новые сообщения (count > 0)
                            // 2. Время последнего сообщения новее последнего уведомлённого
                            if (count > 0 && latestMessageTime > lastNotifiedMessageTime) {
                                android.util.Log.d("NewMessagesService", "Показываем уведомление: $count новых, lastMessageAt=$latestMessageTime")
                                showNotification(count, dialogues)
                                lastNotifiedMessageTime = latestMessageTime
                            } else if (count == 0 && oldCount > 0) {
                                // Убираем уведомление, если сообщений больше нет
                                android.util.Log.d("NewMessagesService", "Убираем уведомление")
                                val notificationManager = getSystemService(NotificationManager::class.java)
                                notificationManager.cancel(NOTIFICATION_ID_MESSAGES)
                                lastNotifiedMessageTime = 0
                            } else if (count > 0 && count < oldCount) {
                                // Количество уменьшилось (прочитали) - обновляем уведомление
                                android.util.Log.d("NewMessagesService", "Обновляем уведомление: было $oldCount, стало $count")
                                showNotification(count, dialogues)
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("NewMessagesService", "Ошибка проверки: ${e.message}")
                    }

                    delay(CHECK_INTERVAL_MS)
                }
            } finally {
                isMonitoring = false
            }
        }
    }

    private fun getApiToken(): String? {
        val prefs = getSharedPreferences("visavi_prefs", Context.MODE_PRIVATE)
        return prefs.getString("api_token", null)
    }
}
