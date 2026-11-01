package com.jarvis.assistant.android

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.jarvis.assistant.core.PermissionChecker
import com.jarvis.assistant.modules.memory.KeyValueStore

class AndroidPermissionChecker(private val context: Context) : PermissionChecker {
    override fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

/** Private app storage. Not included in backups (allowBackup is false). */
class SharedPrefsStore(private val prefs: SharedPreferences) : KeyValueStore {
    override fun get(key: String): String? = prefs.getString(key, null)
    override fun put(key: String, value: String) = prefs.edit().putString(key, value).apply()
    override fun remove(key: String) = prefs.edit().remove(key).apply()
    override fun keysWithPrefix(prefix: String): List<String> = prefs.all.keys.filter { it.startsWith(prefix) }
    override fun clear() = prefs.edit().clear().apply()
}

/** Starts an activity from any context; returns false instead of crashing when nothing can handle it. */
fun Context.startSafely(intent: Intent): Boolean = try {
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
    true
} catch (e: ActivityNotFoundException) {
    false
} catch (e: SecurityException) {
    false
}
