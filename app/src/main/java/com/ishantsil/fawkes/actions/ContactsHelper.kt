package com.ishantsil.fawkes.actions

import android.content.Context
import android.provider.ContactsContract

/** Contact-name lookup for calls, SMS and WhatsApp. Needs READ_CONTACTS. */
object ContactsHelper {

    data class Contact(val name: String, val number: String)

    /**
     * Finds a contact by spoken name. Prefers an exact match, then a
     * starts-with match, then a contains match. Returns null when nothing
     * matches or contacts can't be read.
     */
    fun findContact(context: Context, spokenName: String): Contact? {
        val name = spokenName.trim()
        if (name.isBlank()) return null
        val candidates = mutableListOf<Contact>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                )
                val numberIndex = cursor.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                )
                while (cursor.moveToNext()) {
                    val displayName = cursor.getString(nameIndex) ?: continue
                    val number = cursor.getString(numberIndex) ?: continue
                    candidates.add(Contact(displayName, number))
                }
            }
        } catch (_: SecurityException) {
            return null
        } catch (_: Exception) {
            return null
        }
        val lower = name.lowercase()
        return candidates.firstOrNull { it.name.lowercase() == lower }
            ?: candidates.firstOrNull { it.name.lowercase().startsWith(lower) }
            ?: candidates.firstOrNull { it.name.lowercase().contains(lower) }
    }
}
