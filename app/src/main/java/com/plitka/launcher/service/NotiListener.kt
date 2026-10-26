package com.plitka.launcher.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Слушатель уведомлений: считает, сколько активных уведомлений у каждого приложения.
 * Разрешение выдаётся отдельно (Настройки → Специальные возможности → Доступ к уведомлениям).
 */
class NotiListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        Badges.connected = true
        recount()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Badges.connected = false
        Badges.clear()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        recount()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        recount()
    }

    private fun recount() {
        val map = HashMap<String, Int>()
        try {
            activeNotifications?.forEach { sbn ->
                val pkg = sbn.packageName ?: return@forEach
                if (!sbn.isOngoing) {
                    map[pkg] = (map[pkg] ?: 0) + 1
                }
            }
        } catch (e: Exception) {
            // слушатель мог отвалиться — не падаем
        }
        Badges.update(map)
    }
}
