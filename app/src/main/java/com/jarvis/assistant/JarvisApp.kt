package com.jarvis.assistant

import android.app.Application
import android.content.Context
import com.jarvis.assistant.android.AndroidAppCatalog
import com.jarvis.assistant.android.AndroidContactsSource
import com.jarvis.assistant.android.AndroidDeviceInfoSource
import com.jarvis.assistant.android.AndroidDialerLauncher
import com.jarvis.assistant.android.AndroidFileSource
import com.jarvis.assistant.android.AndroidNotificationSource
import com.jarvis.assistant.android.AndroidPermissionChecker
import com.jarvis.assistant.android.AndroidSettingsLauncher
import com.jarvis.assistant.android.AndroidSmsGateway
import com.jarvis.assistant.android.AndroidVoiceInput
import com.jarvis.assistant.android.AndroidVoiceOutput
import com.jarvis.assistant.android.SharedPrefsStore
import com.jarvis.assistant.core.AiBrain
import com.jarvis.assistant.core.CommandExecutor
import com.jarvis.assistant.core.JarvisModule
import com.jarvis.assistant.core.RuleBasedBrain
import com.jarvis.assistant.modules.apps.AppsModule
import com.jarvis.assistant.modules.calls.CallModule
import com.jarvis.assistant.modules.contacts.ContactsModule
import com.jarvis.assistant.modules.device.DeviceModule
import com.jarvis.assistant.modules.files.FilesModule
import com.jarvis.assistant.modules.memory.PersonalMemory
import com.jarvis.assistant.modules.notifications.NotificationBuffer
import com.jarvis.assistant.modules.notifications.NotificationsModule
import com.jarvis.assistant.modules.settings.SettingsModule
import com.jarvis.assistant.modules.sms.SmsModule
import com.jarvis.assistant.modules.voice.VoiceModule

/** Wires every module to its Android implementation. Nothing here requests a permission. */
class JarvisContainer(app: Application, brain: AiBrain = RuleBasedBrain()) {
    private val permissions = AndroidPermissionChecker(app)
    private val store = SharedPrefsStore(app.getSharedPreferences("jarvis_local", Context.MODE_PRIVATE))

    val notificationBuffer = NotificationBuffer(50)
    val memory = PersonalMemory(store)
    val contacts = ContactsModule(AndroidContactsSource(app), permissions)
    val sms = SmsModule(AndroidSmsGateway(app), permissions)
    val calls = CallModule(AndroidDialerLauncher(app))
    val notifications = NotificationsModule(AndroidNotificationSource(app, notificationBuffer))
    val files = FilesModule(AndroidFileSource(app, store))
    val apps = AppsModule(AndroidAppCatalog(app))
    val device = DeviceModule(AndroidDeviceInfoSource(app))
    val settings = SettingsModule(AndroidSettingsLauncher(app))
    val voice = VoiceModule(AndroidVoiceInput(app), AndroidVoiceOutput(app), permissions)

    val executor = CommandExecutor(brain, memory, contacts, sms, calls, notifications, files, apps, device, settings)

    val modules: List<JarvisModule> =
        listOf(contacts, sms, calls, notifications, files, apps, device, settings, voice, memory)
}

class JarvisApp : Application() {
    val container: JarvisContainer by lazy { JarvisContainer(this) }
}
