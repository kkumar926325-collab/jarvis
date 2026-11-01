package com.jarvis.assistant.modules.sms

import com.jarvis.assistant.core.JarvisModule
import com.jarvis.assistant.core.ModuleStatus
import com.jarvis.assistant.core.Outcome
import com.jarvis.assistant.core.PendingAction
import com.jarvis.assistant.core.PermissionChecker
import com.jarvis.assistant.core.Permissions
import com.jarvis.assistant.core.PhoneNumbers
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SmsMessage(val address: String, val body: String, val timestampMillis: Long, val read: Boolean)

interface SmsGateway {
    /** Sends directly through SmsManager. Needs SEND_SMS. */
    fun send(number: String, body: String): Boolean

    /** Opens the user's messaging app with the text pre-filled. The user presses send there. */
    fun compose(number: String, body: String): Boolean

    /** Reads the newest inbox messages. Read-only; never marks anything as read. */
    fun inbox(limit: Int): List<SmsMessage>
}

class SmsModule(
    private val gateway: SmsGateway,
    private val permissions: PermissionChecker,
) : JarvisModule {
    override val id = "sms"
    override val displayName = "SMS"
    override val requiredPermissions = listOf(Permissions.READ_SMS, Permissions.SEND_SMS)

    override fun status(): ModuleStatus {
        val read = permissions.isGranted(Permissions.READ_SMS)
        val send = permissions.isGranted(Permissions.SEND_SMS)
        return ModuleStatus(
            read || send,
            "Read: ${if (read) "allowed" else "not yet allowed"}, send: ${if (send) "allowed" else "not yet allowed"} (asked only when used)",
        )
    }

    /**
     * Nothing is sent here. The returned [PendingAction] sends only after the user confirms,
     * and only once. In compose mode (direct = false) no SMS permission is needed at all.
     */
    fun prepareSend(recipientName: String, number: String, body: String, direct: Boolean): Outcome {
        val clean = PhoneNumbers.sanitize(number)
        if (clean.length < 3) return Outcome.Failed("\"$number\" doesn't look like a phone number.")
        if (body.isBlank()) return Outcome.Failed("The message is empty.")
        if (direct && !permissions.isGranted(Permissions.SEND_SMS)) {
            return Outcome.NeedsPermission(listOf(Permissions.SEND_SMS))
        }
        val details = buildString {
            append("To: $recipientName ($clean)\n\n$body")
            if (!direct) append("\n\nThis opens your messaging app with the text ready. You press send there.")
        }
        return Outcome.NeedsConfirmation(
            PendingAction(if (direct) "Send this SMS?" else "Open messaging app?", details) {
                if (direct) {
                    if (gateway.send(clean, body)) Outcome.Done("SMS sent to $recipientName.")
                    else Outcome.Failed("Android didn't let me send that SMS.")
                } else {
                    if (gateway.compose(clean, body)) Outcome.Done("Opened your messaging app for $recipientName. Press send there.")
                    else Outcome.Failed("I couldn't open a messaging app.")
                }
            },
        )
    }

    fun readInbox(limit: Int): Outcome {
        if (!permissions.isGranted(Permissions.READ_SMS)) return Outcome.NeedsPermission(listOf(Permissions.READ_SMS))
        val messages = try {
            gateway.inbox(limit.coerceIn(1, 20))
        } catch (e: SecurityException) {
            return Outcome.Failed("Android blocked access to your messages.")
        }
        if (messages.isEmpty()) return Outcome.Done("Your inbox is empty.")
        val fmt = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())
        return Outcome.Done(
            messages.joinToString("\n\n") {
                "From ${it.address} (${fmt.format(Date(it.timestampMillis))}):\n${it.body.take(300)}"
            },
        )
    }
}
