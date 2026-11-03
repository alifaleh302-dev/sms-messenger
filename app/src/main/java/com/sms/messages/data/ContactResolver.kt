package com.sms.messages.data

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.util.LruCache
import com.sms.messages.util.PhoneNumbers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Contact(
    val name: String,
    val number: String,
    val normalized: String,
    val photoUri: Uri?,
)

/**
 * Reads the device address book. Contact access is optional: when the permission is
 * missing every lookup degrades to "no name" and the raw number is displayed instead.
 */
class ContactResolver(private val context: Context) {

    private val cache = LruCache<String, String?>(2048)

    suspend fun displayName(number: String): String? = withContext(Dispatchers.IO) {
        val key = PhoneNumbers.normalize(number)
        if (key.isEmpty()) return@withContext null
        cache[key]?.let { return@withContext it }
        if (cache.get(key) == null && cache.snapshot().containsKey(key)) return@withContext null
        val name = queryName(key)
        cache.put(key, name)
        name
    }

    private fun queryName(normalized: String): String? = runCatching {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(normalized)
        )
        context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    suspend fun loadContacts(): List<Contact> = withContext(Dispatchers.IO) {
        val result = LinkedHashMap<String, Contact>()
        runCatching {
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            )
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection, null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndexOrThrow(projection[0])
                val numberIdx = cursor.getColumnIndexOrThrow(projection[1])
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIdx) ?: continue
                    val number = cursor.getString(numberIdx) ?: continue
                    val normalized = PhoneNumbers.normalize(number)
                    if (normalized.isEmpty()) continue
                    val existing = result[normalized]
                    if (existing == null || existing.name.length > name.length) {
                        result[normalized] = Contact(name, number, normalized, null)
                    }
                }
            }
        }
        result.values.sortedBy { it.name.lowercase() }
    }

    fun invalidate() = cache.evictAll()
}
