package com.ishantsil.fawkes.actions

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import com.ishantsil.fawkes.commands.ConversationMemory

/** WhatsApp via the jid extra: opens the chat with the message prefilled. */
object WhatsAppAction {

    fun send(context: Context, name: String?, message: String): String {
        val contact = resolveContact(context, name)
            ?: return if (name == null) {
                "I don't know who you mean yet."
            } else {
                "I couldn't find $name in your contacts."
            }
        val digits = normaliseNumber(contact.number)
        return try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                setPackage("com.whatsapp")
                putExtra(Intent.EXTRA_TEXT, message)
                putExtra("jid", "$digits@s.whatsapp.net")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ConversationMemory.rememberContact(context, contact.name, contact.number)
            "Opening WhatsApp to ${contact.name}."
        } catch (_: ActivityNotFoundException) {
            "WhatsApp isn't installed, or I couldn't open that chat."
        } catch (_: Exception) {
            "I couldn't open WhatsApp."
        }
    }

    private fun resolveContact(context: Context, name: String?): ContactsHelper.Contact? {
        if (name == null) {
            val lastName = ConversationMemory.lastContactName
            val lastNumber = ConversationMemory.lastContactNumber
            return if (lastName != null && lastNumber != null) {
                ContactsHelper.Contact(lastName, lastNumber)
            } else {
                null
            }
        }
        return ContactsHelper.findContact(context, name)
    }

    /** Strips formatting; bare 10-digit numbers are assumed Indian (+91). */
    private fun normaliseNumber(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return when {
            digits.length == 10 -> "91$digits"
            digits.length == 11 && digits.startsWith("0") -> "91${digits.substring(1)}"
            else -> digits
        }
    }
}
