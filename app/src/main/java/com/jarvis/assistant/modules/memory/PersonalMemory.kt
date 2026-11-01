package com.jarvis.assistant.modules.memory

import com.jarvis.assistant.core.JarvisModule
import com.jarvis.assistant.core.ModuleStatus
import com.jarvis.assistant.core.NameMatcher

/** Tiny key-value abstraction: SharedPreferences on the phone, a map in tests. */
interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
    fun keysWithPrefix(prefix: String): List<String>
    fun clear()
}

class InMemoryStore : KeyValueStore {
    private val map = LinkedHashMap<String, String>()
    override fun get(key: String): String? = map[key]
    override fun put(key: String, value: String) { map[key] = value }
    override fun remove(key: String) { map.remove(key) }
    override fun keysWithPrefix(prefix: String): List<String> = map.keys.filter { it.startsWith(prefix) }
    override fun clear() = map.clear()
}

/**
 * Personal command preferences, kept only in the app's private storage on this phone:
 * aliases ("mom" -> a contact name), taught phrases, and a few settings.
 */
class PersonalMemory(private val store: KeyValueStore) : JarvisModule {
    companion object {
        const val KEY_FILES_ROOT = "files:root"
        private const val ALIAS = "alias:"
        private const val MACRO = "macro:"
        private const val PREF_SMS_DIRECT = "pref:sms_direct"
        private const val PREF_SPEAK = "pref:speak_replies"
    }

    override val id = "memory"
    override val displayName = "Personal memory"
    override val requiredPermissions: List<String> = emptyList()
    override fun status() = ModuleStatus(true, "Ready (${aliases().size} aliases, ${phrases().size} phrases, stored on this phone only)")

    fun setAlias(alias: String, target: String) {
        store.put(ALIAS + NameMatcher.normalize(alias), target.trim())
    }

    fun removeAlias(alias: String): Boolean {
        val key = ALIAS + NameMatcher.normalize(alias)
        if (store.get(key) == null) return false
        store.remove(key)
        return true
    }

    /** Returns what [name] stands for, or [name] itself when no alias exists. */
    fun resolveAlias(name: String): String = store.get(ALIAS + NameMatcher.normalize(name)) ?: name

    fun aliases(): Map<String, String> =
        store.keysWithPrefix(ALIAS).associate { it.removePrefix(ALIAS) to (store.get(it) ?: "") }

    fun teach(phrase: String, command: String) {
        store.put(MACRO + NameMatcher.normalize(phrase), command.trim())
    }

    fun forgetPhrase(phrase: String): Boolean {
        val key = MACRO + NameMatcher.normalize(phrase)
        if (store.get(key) == null) return false
        store.remove(key)
        return true
    }

    fun phrases(): Map<String, String> =
        store.keysWithPrefix(MACRO).associate { it.removePrefix(MACRO) to (store.get(it) ?: "") }

    /** Replaces a taught phrase with the command it stands for (one level only). */
    fun expand(text: String): String = store.get(MACRO + NameMatcher.normalize(text)) ?: text

    /** true = send SMS directly (needs SEND_SMS); false = open the messaging app instead. */
    var smsDirect: Boolean
        get() = store.get(PREF_SMS_DIRECT)?.toBooleanStrictOrNull() ?: true
        set(value) = store.put(PREF_SMS_DIRECT, value.toString())

    /** Speak replies aloud, but only for commands given by voice. */
    var speakReplies: Boolean
        get() = store.get(PREF_SPEAK)?.toBooleanStrictOrNull() ?: true
        set(value) = store.put(PREF_SPEAK, value.toString())

    fun summary(): String {
        val a = aliases()
        val p = phrases()
        val sb = StringBuilder()
        sb.append("Stored only on this phone.\n")
        sb.append(if (a.isEmpty()) "Aliases: none." else "Aliases:\n" + a.entries.joinToString("\n") { "  ${it.key} = ${it.value}" })
        sb.append("\n")
        sb.append(if (p.isEmpty()) "Taught phrases: none." else "Taught phrases:\n" + p.entries.joinToString("\n") { "  \"${it.key}\" -> ${it.value}" })
        sb.append("\nSMS mode: ${if (smsDirect) "direct" else "compose in messaging app"}")
        return sb.toString()
    }

    fun clearAll() = store.clear()
}
