package com.mss.netspeedindicator.service

import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.mss.netspeedindicator.R
import com.mss.netspeedindicator.data.AppSettings

class NetSpeedTileService : TileService() {

    private lateinit var settings: AppSettings

    override fun onCreate() {
        super.onCreate()
        settings = AppSettings(this)
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        settings.isMasterEnabled = !settings.isMasterEnabled
        updateTile()
        updateServiceStatus()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val isActive = settings.isMasterEnabled
        
        tile.state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.app_name)
        
        // You can set different icons if you want
        // tile.icon = Icon.createWithResource(this, if (isActive) R.drawable.ic_active else R.drawable.ic_inactive)
        
        tile.updateTile()
    }

    private fun updateServiceStatus() {
        val shouldRun = settings.isMasterEnabled && (settings.isRealTimeEnabled || settings.isDailyUsageEnabled)
        val intent = Intent(this, SpeedMonitorService::class.java)
        if (shouldRun) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } else {
            stopService(intent)
        }
    }
}
