package com.mss.netspeedindicator.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.app.usage.NetworkStatsManager
import android.net.ConnectivityManager
import android.net.TrafficStats
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import com.mss.netspeedindicator.R
import com.mss.netspeedindicator.data.AppSettings
import java.util.Calendar

class SpeedMonitorService : Service() {

    private val CHANNEL_ID = "speed_monitor_channel"
    private val NOTIFICATION_ID = 1
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var settings: AppSettings
    
    private var lastRxBytes: Long = 0
    private var lastTxBytes: Long = 0
    private var lastTime: Long = 0
    private var lastStatsUpdateTime: Long = 0

    // Cached daily stats from NetworkStatsManager
    private var dailyMobileTotal: Long = 0
    private var dailyWifiTotal: Long = 0

    private var isScreenOn = true
    private var lastNotificationText = ""
    private var lastIconSpeedText = ""
    private var lastIsDownload = false

    private val screenReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            when (intent?.action) {
                android.content.Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    // Force a daily stats update when screen turns on
                    lastStatsUpdateTime = 0 
                    handler.removeCallbacks(updateRunnable)
                    handler.post(updateRunnable)
                }
                android.content.Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    handler.removeCallbacks(updateRunnable)
                }
            }
        }
    }

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (!isScreenOn) return
            
            updateStats()
            handler.postDelayed(this, settings.updateInterval)
        }
    }

    override fun onCreate() {
        super.onCreate()
        settings = AppSettings(this)
        createNotificationChannel()
        
        lastRxBytes = TrafficStats.getTotalRxBytes()
        lastTxBytes = TrafficStats.getTotalTxBytes()
        lastTime = System.currentTimeMillis()
        
        val filter = android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_SCREEN_ON)
            addAction(android.content.Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("0 KB/s", "0 B/s", "0 B/s", "0 B", "0 B", false)
        startForeground(NOTIFICATION_ID, notification)
        
        lastRxBytes = TrafficStats.getTotalRxBytes()
        lastTxBytes = TrafficStats.getTotalTxBytes()
        lastStatsUpdateTime = 0 // Force update on start
        
        handler.removeCallbacks(updateRunnable)
        handler.post(updateRunnable)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        unregisterReceiver(screenReceiver)
        handler.removeCallbacks(updateRunnable)
        super.onDestroy()
    }

    private fun updateDailyTotalsFromSystem() {
        val networkStatsManager = getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        dailyMobileTotal = queryDeviceTotal(networkStatsManager, ConnectivityManager.TYPE_MOBILE, startTime, endTime)
        dailyWifiTotal = queryDeviceTotal(networkStatsManager, ConnectivityManager.TYPE_WIFI, startTime, endTime)
    }

    private fun queryDeviceTotal(manager: NetworkStatsManager, type: Int, start: Long, end: Long): Long {
        return try {
            val bucket = manager.querySummaryForDevice(type, null, start, end)
            bucket.rxBytes + bucket.txBytes
        } catch (e: Exception) {
            0L
        }
    }

    private fun updateStats() {
        val currentTime = System.currentTimeMillis()
        
        // Update daily totals from system every 30 seconds to save battery
        if (currentTime - lastStatsUpdateTime >= 30000L) {
            updateDailyTotalsFromSystem()
            lastStatsUpdateTime = currentTime
        }

        val currentTotalRx = TrafficStats.getTotalRxBytes()
        val currentTotalTx = TrafficStats.getTotalTxBytes()

        val deltaTime = (currentTime - lastTime) / 1000.0
        if (deltaTime <= 0) return

        val deltaRx = if (lastRxBytes > 0 && currentTotalRx >= lastRxBytes) currentTotalRx - lastRxBytes else 0L
        val deltaTx = if (lastTxBytes > 0 && currentTotalTx >= lastTxBytes) currentTotalTx - lastTxBytes else 0L

        // Speed calculation
        val downloadSpeed = deltaRx / deltaTime
        val uploadSpeed = deltaTx / deltaTime

        // Update last values for next speed calculation
        lastRxBytes = currentTotalRx
        lastTxBytes = currentTotalTx
        lastTime = currentTime

        val isDownload = downloadSpeed >= uploadSpeed
        val displaySpeed = if (isDownload) downloadSpeed else uploadSpeed
        
        // Check threshold
        val thresholdInBytes = when (settings.thresholdUnit) {
            "KB/s" -> settings.thresholdValue * 1024
            "MB/s" -> settings.thresholdValue * 1024 * 1024
            else -> settings.thresholdValue
        }

        val showRealTime = settings.isRealTimeEnabled && displaySpeed >= thresholdInBytes
        
        // Use 0 if below threshold to keep UI clean as requested
        val finalDownSpeed = if (showRealTime) downloadSpeed else 0.0
        val finalUpSpeed = if (showRealTime) uploadSpeed else 0.0
        val finalIconSpeed = if (showRealTime) displaySpeed else 0.0

        val speedText = formatSpeed(finalIconSpeed)
        val downSpeedText = formatSpeed(finalDownSpeed)
        val upSpeedText = formatSpeed(finalUpSpeed)
        val mobileText = formatBytes(dailyMobileTotal)
        val wifiText = formatBytes(dailyWifiTotal)

        val contentText = buildString {
            if (settings.isRealTimeEnabled) {
                append("↓$downSpeedText ↑$upSpeedText")
            }
            if (settings.isDailyUsageEnabled) {
                if (isNotEmpty()) append(" | ")
                append("M:$mobileText W:$wifiText")
            }
        }

        // Only notify if something meaningful changed to save battery
        if (contentText != lastNotificationText || (showRealTime && (speedText != lastIconSpeedText || isDownload != lastIsDownload))) {
            lastNotificationText = contentText
            lastIconSpeedText = speedText
            lastIsDownload = isDownload
            
            val notification = createNotification(speedText, downSpeedText, upSpeedText, mobileText, wifiText, showRealTime, contentText)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
            bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
            bytes >= 1024 -> String.format("%d KB", bytes / 1024)
            else -> "$bytes B"
        }
    }

    private fun formatSpeed(bytesPerSecond: Double): String {
        return when {
            bytesPerSecond >= 1024 * 1024 -> String.format("%.1f MB/s", bytesPerSecond / (1024 * 1024))
            bytesPerSecond >= 1024 -> String.format("%d KB/s", (bytesPerSecond / 1024).toInt())
            else -> String.format("%d B/s", bytesPerSecond.toInt())
        }
    }

    private fun createNotification(
        iconSpeedText: String, 
        downSpeedText: String, 
        upSpeedText: String, 
        mobileText: String, 
        wifiText: String,
        showRealTime: Boolean,
        contentText: String = ""
    ): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Indicador de Rede")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && showRealTime) {
            val bitmap = createSpeedBitmap(iconSpeedText)
            builder.setSmallIcon(IconCompat.createWithBitmap(bitmap))
        } else {
            builder.setSmallIcon(R.drawable.ic_net_speed)
        }

        // Expanded view showing details
        val bigTextStyle = NotificationCompat.BigTextStyle()
        val bigText = buildString {
            if (settings.isRealTimeEnabled) {
                append("Download: $downSpeedText  |  Upload: $upSpeedText")
            }
            if (settings.isDailyUsageEnabled) {
                if (isNotEmpty()) append("\n\n")
                append("Dados Móveis hoje: $mobileText\n")
                append("Wi-Fi hoje: $wifiText")
            }
        }
        builder.setStyle(bigTextStyle.bigText(bigText))

        return builder.build()
    }

    private fun createSpeedBitmap(speedText: String): Bitmap {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()

        paint.isAntiAlias = true
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        
        val valueOnly = speedText.split(" ")[0]
        val unit = speedText.split(" ")[1]

        paint.textSize = 50f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText(valueOnly, size / 2f, size / 2f + 10f, paint)

        paint.textSize = 25f
        canvas.drawText(unit, size / 2f, size - 10f, paint)

        return bitmap
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Speed Monitor"
            val descriptionText = "Shows real-time internet speed"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
