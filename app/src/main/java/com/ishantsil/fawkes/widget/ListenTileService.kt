package com.ishantsil.fawkes.widget

import android.content.Intent
import android.service.quicksettings.TileService
import com.ishantsil.fawkes.MainActivity
import com.ishantsil.fawkes.voice.VoskManager

/** Quick Settings tile: tap to open Fawkes and start listening. */
class ListenTileService : TileService() {

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(
                MainActivity.EXTRA_LISTEN,
                VoskManager.isModelInstalled(this@ListenTileService),
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivityAndCollapse(intent)
    }
}
