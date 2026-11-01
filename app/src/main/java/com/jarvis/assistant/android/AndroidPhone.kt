package com.jarvis.assistant.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import com.jarvis.assistant.core.PhoneNumbers
import com.jarvis.assistant.modules.calls.DialerLauncher
import com.jarvis.assistant.modules.contacts.Contact
import com.jarvis.assistant.modules.contacts.ContactsSource
import com.jarvis.assistant.modules.contacts.PhoneNumber
import com.jarvis.assistant.modules.sms.SmsGateway
import com.jarvis.assistant.modules.sms.SmsMessage

/** Reads contacts through ContactsContract. Only called after READ_CONTACTS is granted. */
class AndroidContactsSource(private val context: Context) : ContactsSource {
    override fun all(): List<Contact> {
        val phone = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL,
        )
        val names = LinkedHashMap<String, String>()
        val numbers = HashMap<String, MutableList<PhoneNumber>>()

        context.contentResolver.query(
            phone, projection, null, null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC",
        )?.use { c ->
            val idI = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameI = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numI = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val typeI = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE)
            val labelI = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LABEL)
            while (c.moveToNext()) {
                val id = c.getString(idI) ?: continue
                val number = c.getString(numI) ?: continue
                names[id] = c.getString(nameI) ?: ""
                val label = ContactsContract.CommonDataKinds.Phone
                    .getTypeLabel(context.resources, c.getInt(typeI), c.getString(labelI))
                    .toString()
                val list = numbers.getOrPut(id) { mutableListOf() }
                if (list.none { PhoneNumbers.sanitize(it.value) == PhoneNumbers.sanitize(number) }) {
                    list.add(PhoneNumber(label, number))
                }
            }
        }
        return names.map { (id, name) -> Contact(id, name, numbers[id].orEmpty()) }
    }
}

class AndroidSmsGateway(private val context: Context) : SmsGateway {
    @Suppress("DEPRECATION")
    private fun manager(): SmsManager? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getSystemService(SmsManager::class.java)
        else SmsManager.getDefault()

    override fun send(number: String, body: String): Boolean = try {
        val sm = manager()
        if (sm == null) {
            false
        } else {
            val parts = sm.divideMessage(body)
            if (parts.size > 1) sm.sendMultipartTextMessage(number, null, parts, null, null)
            else sm.sendTextMessage(number, null, body, null, null)
            true
        }
    } catch (e: Exception) {
        false
    }

    override fun compose(number: String, body: String): Boolean {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).putExtra("sms_body", body)
        return context.startSafely(intent)
    }

    override fun inbox(limit: Int): List<SmsMessage> {
        val out = ArrayList<SmsMessage>()
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.READ),
            null, null,
            "${Telephony.Sms.DATE} DESC",
        )?.use { c ->
            val a = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val b = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val d = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val r = c.getColumnIndexOrThrow(Telephony.Sms.READ)
            while (c.moveToNext() && out.size < limit) {
                out.add(SmsMessage(c.getString(a) ?: "unknown", c.getString(b) ?: "", c.getLong(d), c.getInt(r) == 1))
            }
        }
        return out
    }
}

class AndroidDialerLauncher(private val context: Context) : DialerLauncher {
    override fun openDialer(number: String): Boolean =
        context.startSafely(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))
}
