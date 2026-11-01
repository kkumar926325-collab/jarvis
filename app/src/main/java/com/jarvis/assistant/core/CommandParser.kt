package com.jarvis.assistant.core

/**
 * Offline, rule-based English command parser. Pure Kotlin, no Android types.
 * Typed text and voice transcripts go through the same path.
 */
object CommandParser {
    private val opts = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    private fun rx(p: String) = Regex(p, opts)

    private val wake = rx("""^(?:hey\s+|ok\s+|okay\s+)?jarvis\b[,:]?\s*""")
    private val pleaseStart = rx("""^please\s+""")
    private val pleaseEnd = rx("""\s+please$""")
    private val trailing = rx("""[\s.!?]+$""")

    private val helpRx = rx("""(?:help|what can you do|what do you do|commands|show commands)""")
    private val clearRx = rx("""(?:forget everything|clear (?:my )?memory|erase (?:my )?memory|reset (?:my )?memory)""")
    private val listMemRx = rx("""(?:what do you (?:remember|know)(?: about me)?|show (?:my )?memory|list (?:my )?(?:aliases|memory|shortcuts))""")
    private val teachRx = rx("""when i say\s+(.+?)\s*[,:]?\s+(?:you\s+)?(?:do|run)\s+(.+)""")
    private val rememberRx = rx("""remember(?:\s+that)?\s+(.+?)\s+(?:is|means|as)\s+(.+)""")
    private val forgetRx = rx("""forget\s+(?:alias\s+|phrase\s+)?(.+)""")
    private val smsModeRx = rx("""set sms mode(?:\s+to)?\s+(direct|compose)""")

    private val readSmsRx = rx("""(?:read|show|check)\s+(?:my\s+)?(?:(?:last|latest|recent|new)\s+)?(?:(\d{1,2})\s+)?(?:sms|texts?|messages?|inbox)""")
    private val sendSmsRx = rx("""(?:send\s+(?:an?\s+)?(?:sms|text|message)\s+(?:to\s+)?|(?:sms|text|message)\s+(?:to\s+)?)(.+?)(?:\s+(?:saying|that says|say)\s+|\s*:\s*)(.+)""")
    private val callRx = rx("""(?:call|dial|phone|ring)\s+(.+)""")
    private val notificationsRx = rx("""(?:read|show|check|list|what are)\s+(?:my\s+)?(?:(?:latest|recent)\s+)?notifications?""")

    private val openSettingsRx = rx("""(?:open|show|go to|take me to)\s+(.+?)\s+settings?""")
    private val toggleRx = rx("""(?:turn|switch)\s+(?:on|off)\s+(.+)""")
    private val mainSettingsRx = rx("""(?:open|show)\s+(?:system\s+)?settings""")
    private val batteryWordRx = rx(""".*\bbattery\b.*""")
    private val deviceInfoRx = rx("""(?:(?:device|phone)\s+(?:info|information|details|status)|about\s+(?:this\s+|my\s+)?phone|(?:how much\s+)?storage(?:\s+(?:left|free))?)""")

    private val pickFolderRx = rx("""(?:choose|pick|select|change)\s+(?:a\s+|the\s+|my\s+)?(?:file\s+)?folder""")
    private val listFilesRx = rx("""(?:list|show|browse)\s+(?:my\s+)?files(?:\s+in\s+(.+))?""")
    private val searchFilesRx = rx("""(?:find|search(?:\s+for)?)\s+(?:a\s+)?files?\s+(?:named\s+|called\s+|for\s+)?(.+)""")

    private val findContactRx = rx("""(?:find|search|lookup|look up|show)\s+contact\s+(.+)""")
    private val numberOfRx = rx("""what(?:'s|\s+is)\s+(.+?)(?:'s|s)\s+(?:phone\s+)?number""")

    private val listAppsRx = rx("""(?:list|show)\s+(?:my\s+)?apps""")
    private val appInfoRx = rx("""(?:manage|app info(?:\s+for)?|info for)\s+(.+?)(?:\s+app)?""")
    private val openAppRx = rx("""(?:open|launch|start)\s+(.+)""")

    fun parse(raw: String): Command {
        var cleaned = raw.trim()
        cleaned = wake.replace(cleaned, "")
        cleaned = pleaseStart.replace(cleaned, "").trim()

        var t = trailing.replace(cleaned, "")
        t = pleaseEnd.replace(t, "")
        t = trailing.replace(t, "").trim()
        if (t.isEmpty()) return Command.Unknown(raw.trim())

        return meta(t)
            ?: readSms(t)
            ?: sendSms(cleaned)
            ?: deviceInfo(t) // before call(): "phone info" must not be read as "call info"
            ?: call(t)
            ?: notifications(t)
            ?: settings(t)
            ?: battery(t)
            ?: files(t)
            ?: contacts(t)
            ?: apps(t)
            ?: Command.Unknown(raw.trim())
    }

    private fun meta(t: String): Command? {
        if (helpRx.matches(t)) return Command.Help
        if (clearRx.matches(t)) return Command.ClearMemory
        if (listMemRx.matches(t)) return Command.ListMemory
        teachRx.matchEntire(t)?.let { return Command.Teach(it.groupValues[1].trim(), it.groupValues[2].trim()) }
        rememberRx.matchEntire(t)?.let { return Command.Remember(it.groupValues[1].trim(), it.groupValues[2].trim()) }
        forgetRx.matchEntire(t)?.let { return Command.Forget(it.groupValues[1].trim()) }
        smsModeRx.matchEntire(t)?.let { return Command.SetSmsMode(it.groupValues[1].equals("direct", ignoreCase = true)) }
        return null
    }

    private fun readSms(t: String): Command? {
        val m = readSmsRx.matchEntire(t) ?: return null
        val count = m.groupValues[1].toIntOrNull() ?: 5
        return Command.ReadSms(count)
    }

    /** Uses the text with its original punctuation so the message body is not altered. */
    private fun sendSms(cleaned: String): Command? {
        val m = sendSmsRx.matchEntire(cleaned.trim()) ?: return null
        val name = m.groupValues[1].trim()
        val body = m.groupValues[2].trim()
        if (name.isEmpty() || body.isEmpty()) return null
        return Command.SendSms(name, body)
    }

    private fun call(t: String): Command? =
        callRx.matchEntire(t)?.let { Command.Call(it.groupValues[1].trim()) }

    private fun notifications(t: String): Command? =
        if (notificationsRx.matches(t)) Command.ReadNotifications else null

    private fun settings(t: String): Command? {
        if (mainSettingsRx.matches(t)) return Command.OpenSettings("settings")
        openSettingsRx.matchEntire(t)?.let { return Command.OpenSettings(it.groupValues[1].trim()) }
        toggleRx.matchEntire(t)?.let { return Command.OpenSettings(it.groupValues[1].trim(), fromToggle = true) }
        if (batteryWordRx.matches(t) && t.contains("saver", ignoreCase = true)) return Command.OpenSettings("battery saver")
        return null
    }

    private fun battery(t: String): Command? =
        if (batteryWordRx.matches(t)) Command.Battery else null

    private fun deviceInfo(t: String): Command? =
        if (deviceInfoRx.matches(t)) Command.DeviceInfo else null

    private fun files(t: String): Command? {
        if (pickFolderRx.matches(t)) return Command.PickFolder
        listFilesRx.matchEntire(t)?.let {
            val path = it.groupValues[1].trim().ifEmpty { null }
            return Command.ListFiles(path)
        }
        searchFilesRx.matchEntire(t)?.let { return Command.SearchFiles(it.groupValues[1].trim()) }
        return null
    }

    private fun contacts(t: String): Command? {
        findContactRx.matchEntire(t)?.let { return Command.FindContact(it.groupValues[1].trim()) }
        numberOfRx.matchEntire(t)?.let { return Command.FindContact(it.groupValues[1].trim()) }
        return null
    }

    private fun apps(t: String): Command? {
        if (listAppsRx.matches(t)) return Command.ListApps
        appInfoRx.matchEntire(t)?.let { return Command.AppInfo(it.groupValues[1].trim()) }
        openAppRx.matchEntire(t)?.let { return Command.OpenApp(it.groupValues[1].trim()) }
        return null
    }
}
