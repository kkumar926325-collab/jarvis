package com.jarvis.assistant.core

/** Things only the UI can do (they need an Activity or a system screen). */
enum class UiRequest { PICK_FOLDER, ENABLE_NOTIFICATION_ACCESS }

/**
 * Every module action returns one of these. Nothing sensitive happens inside a module call
 * itself: sending an SMS or opening the dialer is wrapped in a [PendingAction] that only runs
 * after the user taps Confirm.
 */
sealed class Outcome {
    data class Done(val message: String) : Outcome()
    data class Failed(val message: String) : Outcome()

    /** Ask the user (with an explanation) for these runtime permissions, then retry the command. */
    data class NeedsPermission(val permissions: List<String>) : Outcome()

    /** Show a confirmation dialog; call [PendingAction.confirm] only if the user agrees. */
    data class NeedsConfirmation(val action: PendingAction) : Outcome()

    /** A system screen / picker must be shown first. [message] explains why. */
    data class NeedsUi(val request: UiRequest, val message: String) : Outcome()
}

/** A sensitive action that has not happened yet. It can run at most once. */
class PendingAction(
    val title: String,
    val details: String,
    private val block: () -> Outcome,
) {
    private var used = false

    @Synchronized
    fun confirm(): Outcome {
        if (used) return Outcome.Failed("That action was already handled.")
        used = true
        return block()
    }
}
