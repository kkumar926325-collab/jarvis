package com.jarvis.assistant.modules.contacts

import com.jarvis.assistant.core.JarvisModule
import com.jarvis.assistant.core.Match
import com.jarvis.assistant.core.ModuleStatus
import com.jarvis.assistant.core.NameMatcher
import com.jarvis.assistant.core.PermissionChecker
import com.jarvis.assistant.core.Permissions

data class PhoneNumber(val label: String, val value: String)
data class Contact(val id: String, val name: String, val numbers: List<PhoneNumber>)

interface ContactsSource {
    fun all(): List<Contact>
}

sealed class ContactLookup {
    data class Found(val contact: Contact, val number: String) : ContactLookup()
    data class Ambiguous(val matches: List<Contact>) : ContactLookup()
    data object NotFound : ContactLookup()
    data object PermissionDenied : ContactLookup()
}

class ContactsModule(
    private val source: ContactsSource,
    private val permissions: PermissionChecker,
) : JarvisModule {
    override val id = "contacts"
    override val displayName = "Contacts"
    override val requiredPermissions = listOf(Permissions.READ_CONTACTS)

    override fun status() =
        if (permissions.isGranted(Permissions.READ_CONTACTS)) ModuleStatus(true, "Ready")
        else ModuleStatus(false, "Needs Contacts permission (asked when you first call or text someone)")

    fun find(query: String): ContactLookup {
        if (!permissions.isGranted(Permissions.READ_CONTACTS)) return ContactLookup.PermissionDenied
        val candidates = source.all().filter { it.numbers.isNotEmpty() }
        return when (val m = NameMatcher.resolve(candidates, query) { it.name }) {
            is Match.Single -> ContactLookup.Found(m.item, m.item.numbers.first().value)
            is Match.Many -> ContactLookup.Ambiguous(m.items)
            Match.None -> ContactLookup.NotFound
        }
    }

    fun describe(contact: Contact): String =
        contact.name + ": " + contact.numbers.joinToString(", ") {
            if (it.label.isBlank()) it.value else "${it.label} ${it.value}"
        }
}
