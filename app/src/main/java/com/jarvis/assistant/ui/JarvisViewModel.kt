package com.jarvis.assistant.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.core.Command
import com.jarvis.assistant.core.ModuleStatus
import com.jarvis.assistant.core.Outcome
import com.jarvis.assistant.core.PendingAction
import com.jarvis.assistant.core.UiRequest
import com.jarvis.assistant.modules.settings.SettingsShortcut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatLine(val fromUser: Boolean, val text: String)

sealed class DialogState {
    data class Permission(val permissions: List<String>) : DialogState()
    data class Confirm(val action: PendingAction, val viaVoice: Boolean) : DialogState()
    data class Ui(val request: UiRequest, val message: String) : DialogState()
}

class JarvisViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as JarvisApp).container

    val messages = mutableStateListOf(
        ChatLine(false, "Hi, I'm JARVIS. Type or tap the mic and say \"help\" to see what I can do. I ask before using any permission and confirm before calling or texting."),
    )
    var dialog by mutableStateOf<DialogState?>(null)
        private set
    var listening by mutableStateOf(false)
        private set
    var showModules by mutableStateOf(false)
    var speakReplies by mutableStateOf(container.memory.speakReplies)
        private set

    /** Re-runs whatever needed a permission or a folder once the user has granted it. */
    private var retry: (() -> Unit)? = null

    fun updateSpeakReplies(value: Boolean) {
        speakReplies = value
        container.memory.speakReplies = value
    }

    fun moduleStatuses(): List<Pair<String, ModuleStatus>> =
        container.modules.map { it.displayName to it.status() }

    // ---- commands ------------------------------------------------------------------------

    fun submit(text: String, viaVoice: Boolean = false) {
        val t = text.trim()
        if (t.isEmpty()) return
        messages += ChatLine(true, t)
        viewModelScope.launch {
            val command = withContext(Dispatchers.IO) { container.executor.interpret(t) }
            execute(command, viaVoice)
        }
    }

    private fun execute(command: Command, viaVoice: Boolean) {
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) { container.executor.execute(command) }
            render(outcome, viaVoice) { execute(command, viaVoice) }
        }
    }

    private fun render(outcome: Outcome, viaVoice: Boolean, retryAction: () -> Unit) {
        when (outcome) {
            is Outcome.Done -> reply(outcome.message, viaVoice)
            is Outcome.Failed -> reply(outcome.message, viaVoice)
            is Outcome.NeedsPermission -> {
                retry = retryAction
                dialog = DialogState.Permission(outcome.permissions)
            }
            is Outcome.NeedsConfirmation -> dialog = DialogState.Confirm(outcome.action, viaVoice)
            is Outcome.NeedsUi -> {
                retry = retryAction
                dialog = DialogState.Ui(outcome.request, outcome.message)
            }
        }
    }

    private fun reply(text: String, viaVoice: Boolean) {
        messages += ChatLine(false, text)
        if (viaVoice && speakReplies) container.voice.speak(text)
    }

    // ---- dialog results ------------------------------------------------------------------

    fun confirm(action: PendingAction, viaVoice: Boolean) {
        dialog = null
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) { action.confirm() }
            render(outcome, viaVoice) {}
        }
    }

    fun dismissDialog(message: String) {
        dialog = null
        retry = null
        messages += ChatLine(false, message)
    }

    /** The user read the explanation and tapped Continue; the system prompt is about to show. */
    fun beginSystemPrompt() {
        dialog = null
    }

    fun onPermissionResult(result: Map<String, Boolean>) {
        if (result.isNotEmpty() && result.values.all { it }) {
            val action = retry
            retry = null
            action?.invoke()
        } else {
            retry = null
            messages += ChatLine(
                false,
                "Permission not granted, so I can't do that. You can allow it later in Android Settings > Apps > JARVIS > Permissions.",
            )
        }
    }

    fun onFolderPicked(uri: String) {
        val name = container.files.setRoot(uri)
        messages += ChatLine(false, "Folder connected: $name. JARVIS can read only inside it.")
        val action = retry
        retry = null
        action?.invoke()
    }

    fun onFolderPickCancelled() {
        retry = null
        messages += ChatLine(false, "No folder chosen.")
    }

    fun openNotificationAccessSettings() {
        dialog = null
        retry = null
        val opened = container.settings.open(SettingsShortcut.NOTIFICATION_ACCESS)
        messages += ChatLine(
            false,
            if (opened) "Turn JARVIS on in that list, come back, and ask again. You can turn it off the same way."
            else "I couldn't open that settings screen on this device.",
        )
    }

    // ---- voice ---------------------------------------------------------------------------

    fun toggleVoice() {
        if (listening) {
            container.voice.stop()
            listening = false
            return
        }
        val outcome = container.voice.listen(
            onResult = { text ->
                listening = false
                submit(text, viaVoice = true)
            },
            onError = { message ->
                listening = false
                messages += ChatLine(false, message)
            },
        )
        when (outcome) {
            is Outcome.NeedsPermission -> {
                retry = { toggleVoice() }
                dialog = DialogState.Permission(outcome.permissions)
            }
            is Outcome.Failed -> messages += ChatLine(false, outcome.message)
            else -> listening = true
        }
    }

    override fun onCleared() {
        container.voice.stop()
        super.onCleared()
    }
}
