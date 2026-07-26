package ru.maxlite.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Держит процесс живым, пока открыт системный выбор файла.
 *
 * Без этого MIUI/Android Go убивают приложение, как только пикер выходит на
 * передний план; при возврате WebView перезагружается, и колбэк выбора файла
 * теряется — отправка «молча» не срабатывает. Сервис короткоживущий
 * (тип shortService, системный лимит ~3 мин) и останавливается сразу после
 * возврата из пикера.
 */
class KeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Выбор файла", NotificationManager.IMPORTANCE_MIN)
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Выбор файла для отправки…")
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
        } else {
            startForeground(1, notification)
        }
    }

    companion object {
        private const val CHANNEL = "keepalive"
    }
}
