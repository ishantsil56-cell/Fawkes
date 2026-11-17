package com.ishantsil.fawkes.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.ishantsil.fawkes.commands.ConversationMemory

/** Calls via the dialer (ACTION_DIAL needs no CALL_PHONE permission). */
object CallAction {

    fun call(context: Context, name: String): String {
        val contact = ContactsHelper.findContact(context, name)
            ?: return "I couldn't find $name in your contacts."
        return callNumber(context, contact.name, contact.number)
    }

    fun callNumber(context: Context, name: String, number: String): String {
        return try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ConversationMemory.rememberContact(context, name, number)
            "Calling $name."
        } catch (_: Exception) {
            "I couldn't open the dialer."
        }
    }
}
