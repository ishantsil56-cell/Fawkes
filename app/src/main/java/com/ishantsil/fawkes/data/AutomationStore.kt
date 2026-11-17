package com.ishantsil.fawkes.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Custom commands and routines: named lists of command phrases. */
object AutomationStore {

    const val KIND_CUSTOM = "custom"
    const val KIND_ROUTINE = "routine"

    private const val FILE_NAME = "automations.json"

    data class Automation(val kind: String, val trigger: String, val actions: List<String>)

    fun save(context: Context, kind: String, trigger: String, actions: List<String>) {
        val array = JsonStore.readArray(context, FILE_NAME)
        // Replace any existing automation with the same trigger.
        val kept = JSONArray()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            if (obj.optString("trigger", "").lowercase() != trigger.lowercase()) {
                kept.put(obj)
            }
        }
        kept.put(
            JSONObject().apply {
                put("kind", kind)
                put("trigger", trigger)
                put("actions", JSONArray(actions))
            },
        )
        JsonStore.writeArray(context, FILE_NAME, kept)
    }

    fun find(context: Context, trigger: String): Automation? {
        return listAll(context).firstOrNull { it.trigger.lowercase() == trigger.lowercase() }
    }

    fun list(context: Context, kind: String): List<Automation> {
        return listAll(context).filter { it.kind == kind }
    }

    fun listAll(context: Context): List<Automation> {
        val array = JsonStore.readArray(context, FILE_NAME)
        val result = mutableListOf<Automation>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val actionsJson = obj.optJSONArray("actions") ?: continue
            val actions = (0 until actionsJson.length()).mapNotNull {
                actionsJson.optString(it).takeIf { s -> s.isNotBlank() }
            }
            result.add(
                Automation(
                    kind = obj.optString("kind", KIND_CUSTOM),
                    trigger = obj.optString("trigger", ""),
                    actions = actions,
                ),
            )
        }
        return result.filter { it.trigger.isNotBlank() && it.actions.isNotEmpty() }
    }

    fun delete(context: Context, trigger: String): Boolean {
        val array = JsonStore.readArray(context, FILE_NAME)
        val kept = JSONArray()
        var removed = false
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            if (obj.optString("trigger", "").lowercase() == trigger.lowercase()) {
                removed = true
            } else {
                kept.put(obj)
            }
        }
        if (removed) JsonStore.writeArray(context, FILE_NAME, kept)
        return removed
    }
}
