package com.jarvis.assistant.modules.settings

import com.jarvis.assistant.core.JarvisModule
import com.jarvis.assistant.core.ModuleStatus
import com.jarvis.assistant.core.NameMatcher

/** Settings screens JARVIS can open. It cannot flip switches: Android does not allow that. */
enum class SettingsShortcut(val label: String, val aliases: List<String>) {
    WIFI("Wi-Fi", listOf("wifi", "wi fi", "wireless", "internet")),
    BLUETOOTH("Bluetooth", listOf("bluetooth")),
    DISPLAY("Display", listOf("display", "brightness", "screen")),
    SOUND("Sound", listOf("sound", "volume", "audio", "ringtone")),
    BATTERY_SAVER("Battery saver", listOf("battery saver", "power saving", "battery")),
    LOCATION("Location", listOf("location", "gps")),
    AIRPLANE("Airplane mode", listOf("airplane mode", "airplane", "flight mode")),
    APPS("Apps", listOf("apps", "applications", "manage apps")),
    DATE("Date & time", listOf("date and time", "date", "time")),
    STORAGE("Storage", listOf("storage")),
    NFC("NFC", listOf("nfc")),
    NOTIFICATION_ACCESS("Notification access", listOf("notification access", "notification listener", "notification permission")),
    MAIN("Settings", listOf("settings", "system")),
}

object SettingsShortcuts {
    private val aliasTable: List<Pair<String, SettingsShortcut>> =
        SettingsShortcut.entries
            .flatMap { s -> s.aliases.map { NameMatcher.normalize(it) to s } }
            .sortedByDescending { it.first.length }

    /** Finds the shortcut whose alias appears as whole words in [text]; longest alias wins. */
    fun resolve(text: String): SettingsShortcut? {
        val padded = " ${NameMatcher.normalize(text)} "
        return aliasTable.firstOrNull { padded.contains(" ${it.first} ") }?.second
    }

    fun labels(): String = SettingsShortcut.entries.joinToString(", ") { it.label }
}

interface SettingsLauncher {
    fun open(shortcut: SettingsShortcut): Boolean
}

class SettingsModule(private val launcher: SettingsLauncher) : JarvisModule {
    override val id = "settings"
    override val displayName = "System settings"
    override val requiredPermissions: List<String> = emptyList()
    override fun status() = ModuleStatus(true, "Ready (opens settings screens; cannot toggle them)")

    fun resolve(text: String): SettingsShortcut? = SettingsShortcuts.resolve(text)
    fun open(shortcut: SettingsShortcut): Boolean = launcher.open(shortcut)
    fun availableLabels(): String = SettingsShortcuts.labels()
}
