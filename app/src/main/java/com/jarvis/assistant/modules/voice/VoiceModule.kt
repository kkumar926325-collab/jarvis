package com.jarvis.assistant.modules.voice

import com.jarvis.assistant.core.JarvisModule
import com.jarvis.assistant.core.ModuleStatus
import com.jarvis.assistant.core.Outcome
import com.jarvis.assistant.core.PermissionChecker
import com.jarvis.assistant.core.Permissions

interface VoiceInput {
    fun isAvailable(): Boolean
    fun start(deliverResult: (String) -> Unit, deliverError: (String) -> Unit)
    fun stop()
}

interface VoiceOutput {
    fun speak(text: String)
    fun shutdown()
}

/**
 * Push-to-talk only: JARVIS listens after you tap the mic and stops after one phrase.
 * There is no always-on or wake-word listening.
 */
class VoiceModule(
    private val input: VoiceInput,
    private val output: VoiceOutput,
    private val permissions: PermissionChecker,
) : JarvisModule {
    override val id = "voice"
    override val displayName = "Voice"
    override val requiredPermissions = listOf(Permissions.RECORD_AUDIO)

    override fun status(): ModuleStatus = when {
        !input.isAvailable() -> ModuleStatus(false, "No speech recognizer found on this device")
        !permissions.isGranted(Permissions.RECORD_AUDIO) -> ModuleStatus(false, "Needs Microphone permission (asked when you first tap the mic)")
        else -> ModuleStatus(true, "Ready (push-to-talk)")
    }

    fun listen(onResult: (String) -> Unit, onError: (String) -> Unit): Outcome {
        if (!permissions.isGranted(Permissions.RECORD_AUDIO)) return Outcome.NeedsPermission(listOf(Permissions.RECORD_AUDIO))
        if (!input.isAvailable()) return Outcome.Failed("Speech recognition isn't available on this device.")
        input.start(onResult, onError)
        return Outcome.Done("Listening...")
    }

    fun stop() = input.stop()
    fun speak(text: String) = output.speak(text)
    fun shutdown() {
        input.stop()
        output.shutdown()
    }
}
