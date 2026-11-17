package com.ishantsil.fawkes.data

import android.content.Context
import org.json.JSONObject

/** Voice notes, newest first. Stored as JSON in internal storage. */
object NotesStore {

    private const val FILE_NAME = "notes.json"

    fun add(context: Context, text: String) {
        val notes = JsonStore.readArray(context, FILE_NAME)
        val entry = JSONObject().apply {
            put("text", text)
            put("created", System.currentTimeMillis())
        }
        notes.put(entry)
        JsonStore.writeArray(context, FILE_NAME, notes)
    }

    /** Newest first. */
    fun list(context: Context): List<String> {
        val notes = JsonStore.readArray(context, FILE_NAME)
        val result = mutableListOf<String>()
        for (i in notes.length() - 1 downTo 0) {
            result.add(notes.optJSONObject(i)?.optString("text", "") ?: "")
        }
        return result.filter { it.isNotBlank() }
    }

    fun clear(context: Context) {
        JsonStore.deleteFile(context, FILE_NAME)
    }
}
