package com.jarvis.assistant.modules.calls

import com.jarvis.assistant.core.JarvisModule
import com.jarvis.assistant.core.ModuleStatus
import com.jarvis.assistant.core.Outcome
import com.jarvis.assistant.core.PendingAction
import com.jarvis.assistant.core.PhoneNumbers

interface DialerLauncher {
    /** ACTION_DIAL: fills in the number; the user presses the call button. */
    fun openDialer(number: String): Boolean
}

/**
 * Uses the dialer intent only, so no CALL_PHONE permission is needed and a call can never
 * start without the user pressing the call button.
 */
class CallModule(private val launcher: DialerLauncher) : JarvisModule {
    override val id = "calls"
    override val displayName = "Calling"
    override val requiredPermissions: List<String> = emptyList()
    override fun status() = ModuleStatus(true, "Ready (opens the dialer; no permission needed)")

    fun prepareCall(name: String, number: String): Outcome {
        val clean = PhoneNumbers.sanitize(number)
        if (clean.length < 3) return Outcome.Failed("\"$number\" doesn't look like a phone number.")
        return Outcome.NeedsConfirmation(
            PendingAction(
                "Call $name?",
                "Number: $clean\n\nThis opens your phone's dialer. You press the call button there.",
            ) {
                if (launcher.openDialer(clean)) Outcome.Done("Opened the dialer for $name.")
                else Outcome.Failed("I couldn't open a dialer app.")
            },
        )
    }
}
