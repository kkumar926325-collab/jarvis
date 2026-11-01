package com.jarvis.assistant

import com.jarvis.assistant.core.Format
import com.jarvis.assistant.core.Match
import com.jarvis.assistant.core.NameMatcher
import com.jarvis.assistant.core.PermissionCatalog
import com.jarvis.assistant.core.Permissions
import com.jarvis.assistant.core.PhoneNumbers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreUtilTest {
    @Test fun nameMatcherRanks() {
        val names = listOf("Momo Singh", "Mom", "Sunita Devi")
        val m = NameMatcher.resolve(names, "mom") { it }
        assertEquals(Match.Single("Mom"), m)
    }

    @Test fun nameMatcherAmbiguousAndNone() {
        val names = listOf("Rahul Kumar", "Rahul Singh")
        assertTrue(NameMatcher.resolve(names, "rahul") { it } is Match.Many)
        assertEquals(Match.None, NameMatcher.resolve(names, "zzz") { it })
        assertEquals(Match.Single("Rahul Kumar"), NameMatcher.resolve(names, "rahul kumar") { it })
    }

    @Test fun nameMatcherHandlesDevanagariMarks() {
        val names = listOf("सुनीता देवी", "राहुल")
        assertEquals(Match.Single("सुनीता देवी"), NameMatcher.resolve(names, "सुनीता") { it })
    }

    @Test fun phoneNumbers() {
        assertEquals("+919876543210", PhoneNumbers.sanitize("+91 98765-43210"))
        assertEquals("1234", PhoneNumbers.sanitize("12+34"))
        assertTrue(PhoneNumbers.looksLikeNumber("98765 43210"))
        assertFalse(PhoneNumbers.looksLikeNumber("mom"))
        assertFalse(PhoneNumbers.looksLikeNumber("12"))
    }

    @Test fun formatBytes() {
        assertEquals("1.5 GB", Format.bytes(1_610_612_736L))
        assertEquals("5 MB", Format.bytes(5L * 1_048_576))
        assertEquals("12 B", Format.bytes(12))
    }

    @Test fun everyRuntimePermissionHasAnExplanation() {
        val all = listOf(Permissions.READ_CONTACTS, Permissions.READ_SMS, Permissions.SEND_SMS, Permissions.RECORD_AUDIO)
        all.forEach { p ->
            assertTrue(p in PermissionCatalog.knownPermissions())
            assertTrue(PermissionCatalog.describe(p).why.length > 40)
        }
    }
}
