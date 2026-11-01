package com.jarvis.assistant.android

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.documentfile.provider.DocumentFile
import com.jarvis.assistant.modules.apps.AppCatalog
import com.jarvis.assistant.modules.apps.AppEntry
import com.jarvis.assistant.modules.device.BatteryStatus
import com.jarvis.assistant.modules.device.DeviceInfoSource
import com.jarvis.assistant.modules.device.DeviceSummary
import com.jarvis.assistant.modules.files.FileEntry
import com.jarvis.assistant.modules.files.FileSource
import com.jarvis.assistant.modules.memory.KeyValueStore
import com.jarvis.assistant.modules.memory.PersonalMemory
import com.jarvis.assistant.modules.notifications.CapturedNotification
import com.jarvis.assistant.modules.notifications.NotificationBuffer
import com.jarvis.assistant.modules.notifications.NotificationSource
import com.jarvis.assistant.modules.settings.SettingsLauncher
import com.jarvis.assistant.modules.settings.SettingsShortcut

class AndroidNotificationSource(
    private val context: Context,
    private val buffer: NotificationBuffer,
) : NotificationSource {
    override fun isAccessEnabled(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    override fun recent(limit: Int): List<CapturedNotification> = buffer.recent(limit)
    override fun clear() = buffer.clear()
}

/** Storage Access Framework: the user picks one folder, JARVIS can read only inside it. */
class AndroidFileSource(
    private val context: Context,
    private val store: KeyValueStore,
) : FileSource {
    override fun rootUri(): String? = store.get(PersonalMemory.KEY_FILES_ROOT)

    override fun rootName(): String? =
        rootUri()?.let { DocumentFile.fromTreeUri(context, Uri.parse(it))?.name }

    override fun setRoot(uriString: String) = store.put(PersonalMemory.KEY_FILES_ROOT, uriString)

    override fun clearRoot() {
        rootUri()?.let {
            try {
                context.contentResolver.releasePersistableUriPermission(Uri.parse(it), Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                // Permission may already be gone; nothing else to do.
            }
        }
        store.remove(PersonalMemory.KEY_FILES_ROOT)
    }

    override fun children(dirUri: String): List<FileEntry> = try {
        val dir = DocumentFile.fromTreeUri(context, Uri.parse(dirUri))
        dir?.listFiles().orEmpty()
            .map { FileEntry(it.name ?: "(unnamed)", it.isDirectory, it.length(), it.uri.toString()) }
            .sortedWith(compareByDescending<FileEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
    } catch (e: SecurityException) {
        emptyList()
    }
}

class AndroidAppCatalog(private val context: Context) : AppCatalog {
    @Suppress("DEPRECATION")
    override fun launchableApps(): List<AppEntry> {
        val pm = context.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val infos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(query, PackageManager.ResolveInfoFlags.of(0))
        } else {
            pm.queryIntentActivities(query, 0)
        }
        return infos
            .map { AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    override fun launch(packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        return context.startSafely(intent)
    }

    override fun openAppInfo(packageName: String): Boolean =
        context.startSafely(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
}

class AndroidDeviceInfoSource(private val context: Context) : DeviceInfoSource {
    override fun battery(): BatteryStatus? {
        val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val source = when {
            plugged and BatteryManager.BATTERY_PLUGGED_AC != 0 -> "AC"
            plugged and BatteryManager.BATTERY_PLUGGED_USB != 0 -> "USB"
            plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 -> "wireless"
            else -> null
        }
        val temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        return BatteryStatus(level * 100 / scale, charging, source, if (temp == Int.MIN_VALUE) null else temp / 10f)
    }

    override fun summary(): DeviceSummary {
        val stat = StatFs(Environment.getDataDirectory().path)
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mem)
        return DeviceSummary(
            manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            totalStorageBytes = stat.totalBytes,
            freeStorageBytes = stat.availableBytes,
            totalRamBytes = mem.totalMem,
        )
    }
}

class AndroidSettingsLauncher(private val context: Context) : SettingsLauncher {
    override fun open(shortcut: SettingsShortcut): Boolean {
        val action = when (shortcut) {
            SettingsShortcut.WIFI -> Settings.ACTION_WIFI_SETTINGS
            SettingsShortcut.BLUETOOTH -> Settings.ACTION_BLUETOOTH_SETTINGS
            SettingsShortcut.DISPLAY -> Settings.ACTION_DISPLAY_SETTINGS
            SettingsShortcut.SOUND -> Settings.ACTION_SOUND_SETTINGS
            SettingsShortcut.BATTERY_SAVER -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            SettingsShortcut.LOCATION -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            SettingsShortcut.AIRPLANE -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
            SettingsShortcut.APPS -> Settings.ACTION_APPLICATION_SETTINGS
            SettingsShortcut.DATE -> Settings.ACTION_DATE_SETTINGS
            SettingsShortcut.STORAGE -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
            SettingsShortcut.NFC -> Settings.ACTION_NFC_SETTINGS
            SettingsShortcut.NOTIFICATION_ACCESS -> Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
            SettingsShortcut.MAIN -> Settings.ACTION_SETTINGS
        }
        return context.startSafely(Intent(action))
    }
}
