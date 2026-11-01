package com.jarvis.assistant.core

import com.jarvis.assistant.modules.apps.AppsModule
import com.jarvis.assistant.modules.calls.CallModule
import com.jarvis.assistant.modules.contacts.ContactLookup
import com.jarvis.assistant.modules.contacts.ContactsModule
import com.jarvis.assistant.modules.device.DeviceModule
import com.jarvis.assistant.modules.files.FilesModule
import com.jarvis.assistant.modules.files.FilesResult
import com.jarvis.assistant.modules.memory.PersonalMemory
import com.jarvis.assistant.modules.notifications.NotificationsModule
import com.jarvis.assistant.modules.settings.SettingsModule
import com.jarvis.assistant.modules.sms.SmsModule

/**
 * Routes a [Command] to the right module. It holds no Android types, so the whole flow
 * (including permission and confirmation behaviour) is unit-testable on the JVM.
 */
class CommandExecutor(
    private val brain: AiBrain,
    private val memory: PersonalMemory,
    private val contacts: ContactsModule,
    private val sms: SmsModule,
    private val calls: CallModule,
    private val notifications: NotificationsModule,
    private val files: FilesModule,
    private val apps: AppsModule,
    private val device: DeviceModule,
    private val settings: SettingsModule,
) {
    companion object {
        val HELP = """
            Things I can do right now:
            - "call <name or number>" (opens the dialer after you confirm)
            - "text <name> saying <message>" (asks you to confirm first)
            - "read my messages" / "read my last 3 messages"
            - "find contact <name>"
            - "open <app>", "manage <app>", "list apps"
            - "battery", "device info"
            - "open wifi settings", "turn on bluetooth" (opens the settings screen)
            - "read notifications" (optional; needs notification access)
            - "choose folder", "list files", "find file <name>"
            - "remember mom is <contact name>", "forget mom", "what do you remember"
            - "when I say <phrase> do <command>"
            - "set sms mode to compose" or "direct"
            - "forget everything"
        """.trimIndent()
    }

    fun interpret(text: String): Command =
        brain.interpret(memory.expand(text), BrainContext(memory.aliases()))

    fun handle(text: String): Outcome = execute(interpret(text))

    fun execute(command: Command): Outcome = when (command) {
        is Command.Call -> call(command)
        is Command.SendSms -> sendSms(command)
        is Command.ReadSms -> sms.readInbox(command.count)
        is Command.FindContact -> findContact(command)
        is Command.OpenApp -> apps.open(memory.resolveAlias(command.name))
        is Command.AppInfo -> apps.openInfo(memory.resolveAlias(command.name))
        Command.ListApps -> apps.list()
        Command.Battery -> Outcome.Done(device.batteryMessage())
        Command.DeviceInfo -> Outcome.Done(device.deviceMessage())
        is Command.OpenSettings -> openSettings(command)
        Command.ReadNotifications -> notifications.read(10)
        Command.PickFolder -> Outcome.NeedsUi(UiRequest.PICK_FOLDER, FilesModule.EXPLANATION)
        is Command.ListFiles -> filesResult(files.list(command.path))
        is Command.SearchFiles -> filesResult(files.search(command.query))
        is Command.Remember -> remember(command)
        is Command.Forget -> forget(command)
        is Command.Teach -> teach(command)
        is Command.SetSmsMode -> setSmsMode(command)
        Command.ListMemory -> Outcome.Done(memory.summary())
        Command.ClearMemory -> clearMemory()
        Command.Help -> Outcome.Done(HELP)
        is Command.Unknown -> Outcome.Done("I didn't understand that. Say \"help\" to see what I can do.")
    }

    // ---- calling / texting -------------------------------------------------------------

    private sealed class Recipient {
        data class Ok(val name: String, val number: String) : Recipient()
        data class Stop(val outcome: Outcome) : Recipient()
    }

    private fun resolveRecipient(raw: String): Recipient {
        val name = memory.resolveAlias(raw)
        if (PhoneNumbers.looksLikeNumber(name)) return Recipient.Ok(name, PhoneNumbers.sanitize(name))
        return when (val r = contacts.find(name)) {
            ContactLookup.PermissionDenied -> Recipient.Stop(Outcome.NeedsPermission(listOf(Permissions.READ_CONTACTS)))
            ContactLookup.NotFound -> Recipient.Stop(Outcome.Failed("I couldn't find a contact matching \"$name\"."))
            is ContactLookup.Ambiguous -> Recipient.Stop(
                Outcome.Done("Several contacts match \"$name\": ${r.matches.take(5).joinToString { it.name }}. Say the full name."),
            )
            is ContactLookup.Found -> Recipient.Ok(r.contact.name, r.number)
        }
    }

    private fun call(c: Command.Call): Outcome = when (val r = resolveRecipient(c.name)) {
        is Recipient.Stop -> r.outcome
        is Recipient.Ok -> calls.prepareCall(r.name, r.number)
    }

    private fun sendSms(c: Command.SendSms): Outcome = when (val r = resolveRecipient(c.name)) {
        is Recipient.Stop -> r.outcome
        is Recipient.Ok -> sms.prepareSend(r.name, r.number, c.body, memory.smsDirect)
    }

    private fun findContact(c: Command.FindContact): Outcome =
        when (val r = contacts.find(memory.resolveAlias(c.name))) {
            ContactLookup.PermissionDenied -> Outcome.NeedsPermission(listOf(Permissions.READ_CONTACTS))
            ContactLookup.NotFound -> Outcome.Failed("I couldn't find a contact matching \"${c.name}\".")
            is ContactLookup.Ambiguous -> Outcome.Done(r.matches.take(5).joinToString("\n") { contacts.describe(it) })
            is ContactLookup.Found -> Outcome.Done(contacts.describe(r.contact))
        }

    // ---- settings / files --------------------------------------------------------------

    private fun openSettings(c: Command.OpenSettings): Outcome {
        val shortcut = settings.resolve(c.target)
            ?: return Outcome.Failed("I don't have a shortcut for \"${c.target}\". I can open: ${settings.availableLabels()}.")
        if (!settings.open(shortcut)) return Outcome.Failed("I couldn't open ${shortcut.label} settings on this device.")
        val note = if (c.fromToggle) " Android doesn't let apps flip this switch, so you'll toggle it there." else ""
        return Outcome.Done("Opened ${shortcut.label} settings.$note")
    }

    private fun filesResult(r: FilesResult): Outcome = when (r) {
        FilesResult.NoRoot -> Outcome.NeedsUi(UiRequest.PICK_FOLDER, FilesModule.EXPLANATION)
        is FilesResult.Text -> Outcome.Done(r.text)
        is FilesResult.Problem -> Outcome.Failed(r.message)
    }

    // ---- personal memory ---------------------------------------------------------------

    private fun remember(c: Command.Remember): Outcome {
        if (NameMatcher.normalize(c.alias) == NameMatcher.normalize(c.target)) {
            return Outcome.Failed("An alias can't point to itself.")
        }
        memory.setAlias(c.alias, c.target)
        return Outcome.Done("Okay. \"${c.alias}\" now means ${c.target}. This is saved only on this phone.")
    }

    private fun forget(c: Command.Forget): Outcome = when {
        memory.removeAlias(c.name) -> Outcome.Done("Forgot the alias \"${c.name}\".")
        memory.forgetPhrase(c.name) -> Outcome.Done("Forgot the phrase \"${c.name}\".")
        else -> Outcome.Failed("I don't have anything saved called \"${c.name}\".")
    }

    private fun teach(c: Command.Teach): Outcome {
        val parsed = brain.interpret(c.command, BrainContext(memory.aliases()))
        if (parsed is Command.Unknown || parsed is Command.Teach) {
            return Outcome.Failed("I can't turn \"${c.command}\" into a command, so I won't save it.")
        }
        memory.teach(c.phrase, c.command)
        return Outcome.Done("Okay. When you say \"${c.phrase}\", I'll run: ${c.command}. Sensitive actions will still ask you to confirm.")
    }

    private fun setSmsMode(c: Command.SetSmsMode): Outcome {
        memory.smsDirect = c.direct
        return Outcome.Done(
            if (c.direct) "SMS mode: direct. I'll send after you confirm (needs the Send SMS permission)."
            else "SMS mode: compose. I'll open your messaging app with the text ready; you press send there.",
        )
    }

    private fun clearMemory(): Outcome = Outcome.NeedsConfirmation(
        PendingAction(
            "Erase JARVIS memory?",
            "This deletes every alias, taught phrase and preference stored on this phone, and forgets the chosen folder.",
        ) {
            files.clearRoot()
            memory.clearAll()
            Outcome.Done("All JARVIS memory on this phone has been erased.")
        },
    )
}
