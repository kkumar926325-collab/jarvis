package com.jarvis.assistant.modules.notifications

import com.jarvis.assistant.core.JarvisModule
import com.jarvis.assistant.core.ModuleStatus
import com.jarvis.assistant.core.Outcome
import com.jarvis.assistant.core.UiRequest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CapturedNotification(
    val appLabel: String,
    val title: String,
    val text: String,
    val postedAtMillis: Long,
)

/** In-memory only. Nothing here is written to disk, and it is empty again when the app process ends. */
class NotificationBuffer(private val capacity: Int = 50) {
    private val items = ArrayDeque<CapturedNotification>()

    @Synchronized
    fun add(n: CapturedNotification) {
        items.addFirst(n)
        while (items.size > capacity) items.removeLast()
    }

    @Synchronized
    fun recent(limit: Int): List<CapturedNotification> = items.take(limit)

    @Synchronized
    fun clear() = items.clear()
}

interface NotificationSource {
    fun isAccessEnabled(): Boolean
    fun recent(limit: Int): List<CapturedNotification>
    fun clear()
}

class NotificationsModule(private val source: NotificationSource) : JarvisModule {
    companion object {
        const val EXPLANATION =
            "Notification access is optional and currently off. If you turn it on, JARVIS can read " +
                "notifications that arrive while it is on. They are kept in memory only (never saved or " +
                "uploaded) and you can turn access off any time in Android settings. Open the settings screen now?"
    }

    override val id = "notifications"
    override val displayName = "Notifications"
    override val requiredPermissions: List<String> = emptyList()

    override fun status() =
        if (source.isAccessEnabled()) ModuleStatus(true, "Notification access is on")
        else ModuleStatus(false, "Optional: off until you enable notification access in Android settings")

    fun read(limit: Int): Outcome {
        if (!source.isAccessEnabled()) return Outcome.NeedsUi(UiRequest.ENABLE_NOTIFICATION_ACCESS, EXPLANATION)
        val items = source.recent(limit.coerceIn(1, 30))
        if (items.isEmpty()) {
            return Outcome.Done(
                "No notifications captured yet. JARVIS only sees notifications that arrive while access is on, " +
                    "and forgets them when the app closes.",
            )
        }
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        return Outcome.Done(
            items.joinToString("\n\n") {
                val head = "${it.appLabel} (${fmt.format(Date(it.postedAtMillis))})"
                val body = listOf(it.title, it.text).filter { s -> s.isNotBlank() }.joinToString(": ")
                "$head\n$body"
            },
        )
    }
}
