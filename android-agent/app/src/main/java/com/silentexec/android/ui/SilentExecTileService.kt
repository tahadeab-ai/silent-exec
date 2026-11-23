package com.silentexec.android.ui

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class SilentExecTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            label = "SilentExec"
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_TRIGGER_VOICE, true)
        }
        startActivityAndCollapse(intent)
    }

    companion object {
        const val EXTRA_TRIGGER_VOICE = "EXTRA_TRIGGER_VOICE"
    }
}
