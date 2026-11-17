package com.ishantsil.fawkes.actions

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/** Opens the Wi-Fi / Bluetooth settings panels. No permission needed. */
object SystemPanelAction {

    fun openWifi(context: Context): String {
        return try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Intent(Settings.Panel.ACTION_WIFI)
            } else {
                Intent(Settings.ACTION_WIFI_SETTINGS)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "Opening Wi-Fi settings."
        } catch (_: Exception) {
            "I couldn't open Wi-Fi settings."
        }
    }

    fun openBluetooth(context: Context): String {
        return try {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "Opening Bluetooth settings."
        } catch (_: Exception) {
            "I couldn't open Bluetooth settings."
        }
    }
}
