package com.jarvis.assistant.core

object Permissions {
    const val READ_CONTACTS = "android.permission.READ_CONTACTS"
    const val READ_SMS = "android.permission.READ_SMS"
    const val SEND_SMS = "android.permission.SEND_SMS"
    const val RECORD_AUDIO = "android.permission.RECORD_AUDIO"
}

data class PermissionInfo(val permission: String, val title: String, val why: String)

/** Plain-language explanations shown BEFORE Android's own permission prompt. */
object PermissionCatalog {
    private val all = listOf(
        PermissionInfo(
            Permissions.READ_CONTACTS,
            "Contacts",
            "JARVIS looks up a contact's name and number when you ask it to call or text someone. " +
                "Contacts stay on this phone; JARVIS has no internet permission and cannot upload them.",
        ),
        PermissionInfo(
            Permissions.READ_SMS,
            "Read text messages",
            "JARVIS reads your inbox only when you ask (for example \"read my messages\"). " +
                "Messages are shown on screen, not stored, and nothing is marked as read.",
        ),
        PermissionInfo(
            Permissions.SEND_SMS,
            "Send text messages",
            "JARVIS sends an SMS only after you confirm the recipient and the exact text on screen. " +
                "Your carrier may charge for messages. If you'd rather not allow this, say " +
                "\"set sms mode to compose\" and JARVIS will open your messaging app instead.",
        ),
        PermissionInfo(
            Permissions.RECORD_AUDIO,
            "Microphone",
            "Used only while you tap the mic button, to turn your speech into a command. " +
                "Android's speech recognizer does the listening; depending on your device and " +
                "installed language packs it may use an online service.",
        ),
    )

    fun knownPermissions(): List<String> = all.map { it.permission }

    fun describe(permission: String): PermissionInfo =
        all.firstOrNull { it.permission == permission }
            ?: PermissionInfo(permission, permission.substringAfterLast('.'), "Needed for the feature you just asked for.")

    fun rationale(permissions: List<String>): String =
        permissions.joinToString("\n\n") {
            val info = describe(it)
            "${info.title}: ${info.why}"
        }
}
