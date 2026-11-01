package com.jarvis.assistant

import com.jarvis.assistant.core.Outcome
import com.jarvis.assistant.core.Permissions
import com.jarvis.assistant.core.UiRequest
import com.jarvis.assistant.modules.apps.AppEntry
import com.jarvis.assistant.modules.memory.InMemoryStore
import com.jarvis.assistant.modules.memory.PersonalMemory
import com.jarvis.assistant.modules.voice.VoiceInput
import com.jarvis.assistant.modules.voice.VoiceModule
import com.jarvis.assistant.modules.voice.VoiceOutput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryVoiceExecutorTest {
    // ---- personal memory ---------------------------------------------------------------

    @Test fun aliasesAndMacros() {
        val mem = PersonalMemory(InMemoryStore())
        mem.setAlias("Mom", "Sunita Devi")
        assertEquals("Sunita Devi", mem.resolveAlias("mom"))
        assertEquals("Rahul", mem.resolveAlias("Rahul"))
        mem.teach("Good morning", "battery status")
        assertEquals("battery status", mem.expand("good morning"))
        assertEquals("hello", mem.expand("hello"))
        assertTrue(mem.removeAlias("MOM"))
        assertFalse(mem.removeAlias("mom"))
    }

    @Test fun preferencesDefaultAndPersist() {
        val mem = PersonalMemory(InMemoryStore())
        assertTrue(mem.smsDirect)
        mem.smsDirect = false
        assertFalse(mem.smsDirect)
        mem.clearAll()
        assertTrue(mem.smsDirect)
        assertTrue(mem.aliases().isEmpty())
    }

    // ---- voice -------------------------------------------------------------------------

    private class FakeVoiceInput(var available: Boolean = true) : VoiceInput {
        var started = 0
        var deliver: ((String) -> Unit)? = null
        override fun isAvailable() = available
        override fun start(deliverResult: (String) -> Unit, deliverError: (String) -> Unit) { started++; deliver = deliverResult }
        override fun stop() {}
    }

    private class FakeVoiceOutput : VoiceOutput {
        val spoken = mutableListOf<String>()
        override fun speak(text: String) { spoken += text }
        override fun shutdown() {}
    }

    @Test fun voiceNeedsMicPermissionBeforeListening() {
        val input = FakeVoiceInput()
        val voice = VoiceModule(input, FakeVoiceOutput(), FakePermissions())
        assertEquals(Outcome.NeedsPermission(listOf(Permissions.RECORD_AUDIO)), voice.listen({}, {}))
        assertEquals(0, input.started)
    }

    @Test fun voiceListensAndDeliversText() {
        val input = FakeVoiceInput()
        val voice = VoiceModule(input, FakeVoiceOutput(), FakePermissions(Permissions.RECORD_AUDIO))
        var heard = ""
        assertTrue(voice.listen({ heard = it }, {}) is Outcome.Done)
        input.deliver?.invoke("call mom")
        assertEquals("call mom", heard)
    }

    @Test fun voiceUnavailableFails() {
        val voice = VoiceModule(FakeVoiceInput(available = false), FakeVoiceOutput(), FakePermissions(Permissions.RECORD_AUDIO))
        assertTrue(voice.listen({}, {}) is Outcome.Failed)
    }

    // ---- executor (modules wired together) ----------------------------------------------

    @Test fun callFlowResolvesContactThenConfirms() {
        val h = Harness(Permissions.READ_CONTACTS)
        h.contactsSource.contacts = listOf(contact("Mom", "+91 98765 43210"))
        val out = h.executor.handle("call mom")
        assertTrue(out is Outcome.NeedsConfirmation)
        assertTrue(h.dialer.dialed.isEmpty())
        (out as Outcome.NeedsConfirmation).action.confirm()
        assertEquals(listOf("+919876543210"), h.dialer.dialed)
    }

    @Test fun callWithoutContactsPermissionAsksFirst() {
        val h = Harness()
        assertEquals(Outcome.NeedsPermission(listOf(Permissions.READ_CONTACTS)), h.executor.handle("call mom"))
        assertTrue(h.dialer.dialed.isEmpty())
    }

    @Test fun smsNeverSentWithoutConfirmation() {
        val h = Harness(Permissions.READ_CONTACTS, Permissions.SEND_SMS)
        h.contactsSource.contacts = listOf(contact("Mom", "111"))
        val out = h.executor.handle("text mom saying I will be late")
        assertTrue(out is Outcome.NeedsConfirmation)
        assertTrue(h.smsGateway.sent.isEmpty())
        (out as Outcome.NeedsConfirmation).action.confirm()
        assertEquals(listOf("111" to "I will be late"), h.smsGateway.sent)
    }

    @Test fun smsWithoutSendPermissionAsksFirstThenComposeModeAvoidsIt() {
        val h = Harness(Permissions.READ_CONTACTS)
        h.contactsSource.contacts = listOf(contact("Mom", "111"))
        assertEquals(Outcome.NeedsPermission(listOf(Permissions.SEND_SMS)), h.executor.handle("text mom saying hi"))
        h.executor.handle("set sms mode to compose")
        assertTrue(h.executor.handle("text mom saying hi") is Outcome.NeedsConfirmation)
    }

    @Test fun aliasResolvesToContact() {
        val h = Harness(Permissions.READ_CONTACTS)
        h.contactsSource.contacts = listOf(contact("Sunita Devi", "555"))
        h.executor.handle("remember mom is Sunita Devi")
        val out = h.executor.handle("call mom") as Outcome.NeedsConfirmation
        assertTrue(out.action.title.contains("Sunita Devi"))
    }

    @Test fun taughtPhraseRunsItsCommand() {
        val h = Harness()
        h.executor.handle("when I say good morning do battery status")
        val out = h.executor.handle("good morning") as Outcome.Done
        assertTrue(out.message.contains("80%"))
    }

    @Test fun teachRejectsUnparseableCommand() {
        assertTrue(Harness().executor.handle("when I say hi do blorp") is Outcome.Failed)
    }

    @Test fun readSmsAsksPermissionThenReads() {
        val h = Harness()
        assertEquals(Outcome.NeedsPermission(listOf(Permissions.READ_SMS)), h.executor.handle("read my messages"))
    }

    @Test fun notificationsAndFilesAskForUiFirst() {
        val h = Harness()
        assertEquals(UiRequest.ENABLE_NOTIFICATION_ACCESS, (h.executor.handle("read notifications") as Outcome.NeedsUi).request)
        assertEquals(UiRequest.PICK_FOLDER, (h.executor.handle("list files") as Outcome.NeedsUi).request)
    }

    @Test fun openAppAndSettings() {
        val h = Harness()
        h.appCatalog.apps = listOf(AppEntry("WhatsApp", "com.whatsapp"))
        h.executor.handle("open whatsapp")
        assertEquals(listOf("com.whatsapp"), h.appCatalog.launched)
        val out = h.executor.handle("turn on wifi") as Outcome.Done
        assertTrue(out.message.contains("doesn't let apps flip"))
        assertEquals(1, h.settingsLauncher.opened.size)
        assertTrue(h.executor.handle("turn on flashlight") is Outcome.Failed)
    }

    @Test fun clearMemoryNeedsConfirmation() {
        val h = Harness()
        h.executor.handle("remember mom is Sunita")
        val out = h.executor.handle("forget everything")
        assertTrue(out is Outcome.NeedsConfirmation)
        assertEquals(1, h.memory.aliases().size)
        (out as Outcome.NeedsConfirmation).action.confirm()
        assertTrue(h.memory.aliases().isEmpty())
        assertTrue(h.fileSource.cleared)
    }

    @Test fun unknownAndHelp() {
        val h = Harness()
        assertTrue((h.executor.handle("sing a song") as Outcome.Done).message.contains("help"))
        assertTrue((h.executor.handle("help") as Outcome.Done).message.contains("call"))
    }
}
