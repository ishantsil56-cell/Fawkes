package com.ishantsil.fawkes.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.ishantsil.fawkes.commands.ConversationMemory

/** SMS via the messaging app's compose screen — no SEND_SMS permission needed. */
object SmsAction {

    fun send(context: Context, name: String?, message: String): String {
        val contact = resolveContact(context, name)
            ?: return if (name == null) {
                "I don't know who you mean yet."
            } else {
                "I couldn't find $name in your contacts."
            }
        return try {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${contact.number}")).apply {
                putExtra("sms_body", message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ConversationMemory.rememberContact(context, contact.name, contact.number)
            "Opening messages to ${contact.name}."
        } catch (_: Exception) {
            "I couldn't open the messaging app."
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
}
