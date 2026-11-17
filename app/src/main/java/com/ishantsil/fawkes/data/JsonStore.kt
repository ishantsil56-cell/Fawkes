package com.ishantsil.fawkes.data

import android.content.Context
import org.json.JSONArray
import java.io.File

/**
 * Tiny JSON-file storage for notes, reminders and automations.
 * Plain files, no database dependency — enough for v1, and the files are
 * human-readable if anything ever needs debugging.
 */
object JsonStore {

    @Synchronized
    fun readArray(context: Context, fileName: String): JSONArray {
        val file = File(context.filesDir, fileName)
        if (!file.isFile) return JSONArray()
        return try {
            JSONArray(file.readText())
        } catch (_: Exception) {
            JSONArray()
        }
    }

    @Synchronized
    fun writeArray(context: Context, fileName: String, array: JSONArray) {
        val file = File(context.filesDir, fileName)
        try {
            file.writeText(array.toString())
        } catch (_: Exception) {
            // Writes fail silently here; callers surface errors themselves
            // when the data matters (reminders re-read after scheduling).
        }
    }

    @Synchronized
    fun deleteFile(context: Context, fileName: String): Boolean {
        return File(context.filesDir, fileName).delete()
    }
}
