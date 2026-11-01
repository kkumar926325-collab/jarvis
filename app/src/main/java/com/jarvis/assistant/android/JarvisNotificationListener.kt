package com.jarvis.assistant.android

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.modules.notifications.CapturedNotification

/**
 * Optional. Android only binds this service after the user switches it on in
 * Settings > Notification access. It keeps recent notifications in memory only.
 */
class JarvisNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName || sbn.isOngoing) return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return

        val label = try {
            val info = packageManager.getApplicationInfo(sbn.packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            sbn.packageName
        }
        (application as JarvisApp).container.notificationBuffer.add(
            CapturedNotification(label, title, text, sbn.postTime),
        )
    }

    override fun onListenerDisconnected() {
        (application as JarvisApp).container.notificationBuffer.clear()
        super.onListenerDisconnected()
    }
}
