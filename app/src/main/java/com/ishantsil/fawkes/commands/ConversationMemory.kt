package com.ishantsil.fawkes.commands

import android.content.Context

/**
 * Short conversation memory: remembers the last person and app mentioned,
 * so "call him", "text her" and "open it again" resolve without repeating
 * the name. Persisted, so it survives an app restart.
 */
object ConversationMemory {

    private const val PREFS = "fawkes_memory"
    private const val KEY_CONTACT_NAME = "last_contact_name"
    private const val KEY_CONTACT_NUMBER = "last_contact_number"
    private const val KEY_APP_NAME = "last_app_name"
    private const val KEY_APP_PACKAGE = "last_app_package"

    var lastContactName: String? = null
        private set
    var lastContactNumber: String? = null
        private set
    var lastAppName: String? = null
        private set
    var lastAppPackage: String? = null
        private set

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        lastContactName = prefs.getString(KEY_CONTACT_NAME, null)
        lastContactNumber = prefs.getString(KEY_CONTACT_NUMBER, null)
        lastAppName = prefs.getString(KEY_APP_NAME, null)
        lastAppPackage = prefs.getString(KEY_APP_PACKAGE, null)
    }

    fun rememberContact(context: Context, name: String, number: String) {
        lastContactName = name
        lastContactNumber = number
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_CONTACT_NAME, name)
            .putString(KEY_CONTACT_NUMBER, number)
            .apply()
    }

    fun rememberApp(context: Context, name: String, packageName: String) {
        lastAppName = name
        lastAppPackage = packageName
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_APP_NAME, name)
            .putString(KEY_APP_PACKAGE, packageName)
            .apply()
    }

    /** True when the phrase refers to the last contact ("call him", "text her"). */
    fun isContactPronoun(phrase: String): Boolean {
        val words = phrase.split(" ")
        return words.any { it in setOf("him", "her", "them", "that person") }
    }

    fun hasContact(): Boolean = !lastContactNumber.isNullOrBlank()
}
