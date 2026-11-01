package com.jarvis.assistant

import com.jarvis.assistant.core.Outcome
import com.jarvis.assistant.core.Permissions
import com.jarvis.assistant.modules.calls.CallModule
import com.jarvis.assistant.modules.contacts.ContactLookup
import com.jarvis.assistant.modules.contacts.ContactsModule
import com.jarvis.assistant.modules.sms.SmsMessage
import com.jarvis.assistant.modules.sms.SmsModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactsCallSmsTest {
    @Test fun contactsNeedPermission() {
        val m = ContactsModule(FakeContactsSource(listOf(contact("Mom", "111"))), FakePermissions())
        assertEquals(ContactLookup.PermissionDenied, m.find("mom"))
        assertTrue(!m.status().ready)
    }

    @Test fun contactsFoundAmbiguousNotFound() {
        val src = FakeContactsSource(listOf(contact("Rahul Kumar", "111"), contact("Rahul Singh", "222"), contact("Mom", "333", "444")))
        val m = ContactsModule(src, FakePermissions(Permissions.READ_CONTACTS))
        assertTrue(m.find("rahul") is ContactLookup.Ambiguous)
        assertEquals("333", (m.find("mom") as ContactLookup.Found).number)
        assertEquals(ContactLookup.NotFound, m.find("nobody"))
    }

    @Test fun callOnlyOpensDialerAfterConfirmation() {
        val dialer = FakeDialer()
        val outcome = CallModule(dialer).prepareCall("Mom", "+91 98765 43210")
        assertTrue(outcome is Outcome.NeedsConfirmation)
        assertTrue(dialer.dialed.isEmpty())
        (outcome as Outcome.NeedsConfirmation).action.confirm()
        assertEquals(listOf("+919876543210"), dialer.dialed)
    }

    @Test fun callRejectsGarbageNumber() {
        assertTrue(CallModule(FakeDialer()).prepareCall("X", "ab") is Outcome.Failed)
    }

    @Test fun smsDirectNeedsSendPermission() {
        val gw = FakeSmsGateway()
        val sms = SmsModule(gw, FakePermissions())
        assertEquals(Outcome.NeedsPermission(listOf(Permissions.SEND_SMS)), sms.prepareSend("Mom", "111", "hi", direct = true))
        assertTrue(gw.sent.isEmpty())
    }

    @Test fun smsSendsOnlyAfterConfirmationAndOnlyOnce() {
        val gw = FakeSmsGateway()
        val sms = SmsModule(gw, FakePermissions(Permissions.SEND_SMS))
        val outcome = sms.prepareSend("Mom", "111", "hi", direct = true) as Outcome.NeedsConfirmation
        assertTrue(gw.sent.isEmpty())
        assertTrue(outcome.action.details.contains("hi"))
        assertTrue(outcome.action.confirm() is Outcome.Done)
        assertTrue(outcome.action.confirm() is Outcome.Failed)
        assertEquals(listOf("111" to "hi"), gw.sent)
    }

    @Test fun smsComposeModeNeedsNoPermission() {
        val gw = FakeSmsGateway()
        val sms = SmsModule(gw, FakePermissions())
        val outcome = sms.prepareSend("Mom", "111", "hi", direct = false) as Outcome.NeedsConfirmation
        outcome.action.confirm()
        assertTrue(gw.sent.isEmpty())
        assertEquals(listOf("111" to "hi"), gw.composed)
    }

    @Test fun smsRejectsEmptyBody() {
        val sms = SmsModule(FakeSmsGateway(), FakePermissions(Permissions.SEND_SMS))
        assertTrue(sms.prepareSend("Mom", "111", "  ", direct = true) is Outcome.Failed)
    }

    @Test fun readingInboxNeedsPermissionThenShowsMessages() {
        val gw = FakeSmsGateway().apply { inboxItems = listOf(SmsMessage("+1555", "hello there", 0L, false)) }
        assertEquals(Outcome.NeedsPermission(listOf(Permissions.READ_SMS)), SmsModule(gw, FakePermissions()).readInbox(5))
        val done = SmsModule(gw, FakePermissions(Permissions.READ_SMS)).readInbox(5) as Outcome.Done
        assertTrue(done.message.contains("hello there"))
    }
}
