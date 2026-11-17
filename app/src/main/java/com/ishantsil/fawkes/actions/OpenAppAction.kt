package com.ishantsil.fawkes.actions

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.ishantsil.fawkes.commands.ConversationMemory

/**
 * Opens installed apps by spoken name. Visibility of launcher apps comes
 * from the <queries> block in the manifest (no QUERY_ALL_PACKAGES needed).
 */
object OpenAppAction {

    fun open(context: Context, appName: String): String {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = try {
            pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        } catch (_: Exception) {
            emptyList()
        }
        val lower = appName.lowercase()
        val match = apps.firstOrNull { it.loadLabel(pm).toString().lowercase() == lower }
            ?: apps.firstOrNull { it.loadLabel(pm).toString().lowercase().startsWith(lower) }
            ?: apps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(lower) }
            ?: return "I couldn't find an app called $appName."
        val label = match.loadLabel(pm).toString()
        return openPackage(context, match.activityInfo.packageName, label)
    }

    fun openPackage(context: Context, packageName: String, label: String): String {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return "I couldn't open $label."
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            ConversationMemory.rememberApp(context, label, packageName)
            "Opening $label."
        } catch (_: Exception) {
            "I couldn't open $label."
        }
    }
}
