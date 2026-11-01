package com.jarvis.assistant.core

/** Everything JARVIS can be asked to do. A future AI brain only has to produce these. */
sealed class Command {
    data class Call(val name: String) : Command()
    data class SendSms(val name: String, val body: String) : Command()
    data class ReadSms(val count: Int = 5) : Command()
    data class FindContact(val name: String) : Command()

    data class OpenApp(val name: String) : Command()
    data class AppInfo(val name: String) : Command()
    data object ListApps : Command()

    data object Battery : Command()
    data object DeviceInfo : Command()
    data class OpenSettings(val target: String, val fromToggle: Boolean = false) : Command()

    data object ReadNotifications : Command()

    data object PickFolder : Command()
    data class ListFiles(val path: String?) : Command()
    data class SearchFiles(val query: String) : Command()

    data class Remember(val alias: String, val target: String) : Command()
    data class Forget(val name: String) : Command()
    data class Teach(val phrase: String, val command: String) : Command()
    data class SetSmsMode(val direct: Boolean) : Command()
    data object ListMemory : Command()
    data object ClearMemory : Command()

    data object Help : Command()
    data class Unknown(val text: String) : Command()
}
