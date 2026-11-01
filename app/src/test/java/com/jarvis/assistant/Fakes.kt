package com.jarvis.assistant

import com.jarvis.assistant.core.CommandExecutor
import com.jarvis.assistant.core.PermissionChecker
import com.jarvis.assistant.core.RuleBasedBrain
import com.jarvis.assistant.modules.apps.AppCatalog
import com.jarvis.assistant.modules.apps.AppEntry
import com.jarvis.assistant.modules.apps.AppsModule
import com.jarvis.assistant.modules.calls.CallModule
import com.jarvis.assistant.modules.calls.DialerLauncher
import com.jarvis.assistant.modules.contacts.Contact
import com.jarvis.assistant.modules.contacts.ContactsModule
import com.jarvis.assistant.modules.contacts.ContactsSource
import com.jarvis.assistant.modules.contacts.PhoneNumber
import com.jarvis.assistant.modules.device.BatteryStatus
import com.jarvis.assistant.modules.device.DeviceInfoSource
import com.jarvis.assistant.modules.device.DeviceModule
import com.jarvis.assistant.modules.device.DeviceSummary
import com.jarvis.assistant.modules.files.FileEntry
import com.jarvis.assistant.modules.files.FileSource
import com.jarvis.assistant.modules.files.FilesModule
import com.jarvis.assistant.modules.memory.InMemoryStore
import com.jarvis.assistant.modules.memory.PersonalMemory
import com.jarvis.assistant.modules.notifications.CapturedNotification
import com.jarvis.assistant.modules.notifications.NotificationSource
import com.jarvis.assistant.modules.notifications.NotificationsModule
import com.jarvis.assistant.modules.settings.SettingsLauncher
import com.jarvis.assistant.modules.settings.SettingsModule
import com.jarvis.assistant.modules.settings.SettingsShortcut
import com.jarvis.assistant.modules.sms.SmsGateway
import com.jarvis.assistant.modules.sms.SmsMessage
import com.jarvis.assistant.modules.sms.SmsModule

class FakePermissions(vararg initial: String) : PermissionChecker {
    val granted = initial.toMutableSet()
    override fun isGranted(permission: String) = permission in granted
}

class FakeContactsSource(var contacts: List<Contact> = emptyList()) : ContactsSource {
    override fun all() = contacts
}

class FakeSmsGateway : SmsGateway {
    val sent = mutableListOf<Pair<String, String>>()
    val composed = mutableListOf<Pair<String, String>>()
    var inboxItems: List<SmsMessage> = emptyList()
    override fun send(number: String, body: String): Boolean { sent += number to body; return true }
    override fun compose(number: String, body: String): Boolean { composed += number to body; return true }
    override fun inbox(limit: Int) = inboxItems.take(limit)
}

class FakeDialer : DialerLauncher {
    val dialed = mutableListOf<String>()
    override fun openDialer(number: String): Boolean { dialed += number; return true }
}

class FakeNotificationSource(var enabled: Boolean = false, var items: List<CapturedNotification> = emptyList()) : NotificationSource {
    override fun isAccessEnabled() = enabled
    override fun recent(limit: Int) = items.take(limit)
    override fun clear() { items = emptyList() }
}

/** Tree keyed by directory URI. Root is "root". */
class FakeFileSource(var tree: Map<String, List<FileEntry>> = emptyMap(), var root: String? = null) : FileSource {
    var cleared = false
    override fun rootUri() = root
    override fun rootName() = root?.let { "Root" }
    override fun setRoot(uriString: String) { root = uriString }
    override fun clearRoot() { root = null; cleared = true }
    override fun children(dirUri: String) = tree[dirUri].orEmpty()
}

class FakeAppCatalog(var apps: List<AppEntry> = emptyList()) : AppCatalog {
    val launched = mutableListOf<String>()
    val infoOpened = mutableListOf<String>()
    override fun launchableApps() = apps
    override fun launch(packageName: String): Boolean { launched += packageName; return true }
    override fun openAppInfo(packageName: String): Boolean { infoOpened += packageName; return true }
}

class FakeDeviceSource(var battery: BatteryStatus? = BatteryStatus(80, false, null, null)) : DeviceInfoSource {
    override fun battery() = battery
    override fun summary() = DeviceSummary("Acme", "Phone 1", "14", 34, 128L * 1_073_741_824, 64L * 1_073_741_824, 8L * 1_073_741_824)
}

class FakeSettingsLauncher : SettingsLauncher {
    val opened = mutableListOf<SettingsShortcut>()
    override fun open(shortcut: SettingsShortcut): Boolean { opened += shortcut; return true }
}

fun contact(name: String, vararg numbers: String) =
    Contact(name.lowercase(), name, numbers.map { PhoneNumber("Mobile", it) })

/** Every module wired to fakes, exactly like JarvisContainer wires real implementations. */
class Harness(vararg grants: String) {
    val permissions = FakePermissions(*grants)
    val memory = PersonalMemory(InMemoryStore())
    val contactsSource = FakeContactsSource()
    val smsGateway = FakeSmsGateway()
    val dialer = FakeDialer()
    val notificationSource = FakeNotificationSource()
    val fileSource = FakeFileSource()
    val appCatalog = FakeAppCatalog()
    val deviceSource = FakeDeviceSource()
    val settingsLauncher = FakeSettingsLauncher()

    val contacts = ContactsModule(contactsSource, permissions)
    val sms = SmsModule(smsGateway, permissions)
    val calls = CallModule(dialer)
    val notifications = NotificationsModule(notificationSource)
    val files = FilesModule(fileSource)
    val apps = AppsModule(appCatalog)
    val device = DeviceModule(deviceSource)
    val settings = SettingsModule(settingsLauncher)

    val executor = CommandExecutor(RuleBasedBrain(), memory, contacts, sms, calls, notifications, files, apps, device, settings)
}
