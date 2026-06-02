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
    private var lastMobileRx: Long = 0
    private var lastMobileTx: Long = 0
    private var lastTime: Long = 0
    private var lastSaveTime: Long = 0

    // Memory cache for daily stats to reduce disk I/O
    private var dailyMobileRx: Long = 0
    private var dailyMobileTx: Long = 0
    private var dailyWifiRx: Long = 0
    private var dailyWifiTx: Long = 0

    private var isScreenOn = true
    private var lastNotificationText = ""
    private var lastIconSpeedText = ""
    private var lastIsDownload = false

    private val screenReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            when (intent?.action) {
                android.content.Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    // Resume updates immediately
                    handler.removeCallbacks(updateRunnable)
                    handler.post(updateRunnable)
                }
                android.content.Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    saveDailyStatsToStorage() // Save buffered data before stopping
                    // Stop all processing while screen is off
                    handler.removeCallbacks(updateRunnable)
                }
            }
        }
    }

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (!isScreenOn) return // Extra safety check
            
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
        lastMobileRx = TrafficStats.getMobileRxBytes()
        lastMobileTx = TrafficStats.getMobileTxBytes()
        lastTime = System.currentTimeMillis()
        lastSaveTime = lastTime

        // Load daily stats into memory
        dailyMobileRx = settings.dailyMobileRx
        dailyMobileTx = settings.dailyMobileTx
        dailyWifiRx = settings.dailyWifiRx
        dailyWifiTx = settings.dailyWifiTx
        
        checkAndResetDailyStats()

        val filter = android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_SCREEN_ON)
            addAction(android.content.Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("0 KB/s", "0 B/s", "0 B/s", "0 B", "0 B", false)
        startForeground(NOTIFICATION_ID, notification)
        
        // Reset last bytes on start to avoid huge first delta
        lastRxBytes = TrafficStats.getTotalRxBytes()
        lastTxBytes = TrafficStats.getTotalTxBytes()
        lastMobileRx = TrafficStats.getMobileRxBytes()
        lastMobileTx = TrafficStats.getMobileTxBytes()
        
        handler.removeCallbacks(updateRunnable)
        handler.post(updateRunnable)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        saveDailyStatsToStorage()
        unregisterReceiver(screenReceiver)
        handler.removeCallbacks(updateRunnable)
        super.onDestroy()
    }

    private fun checkAndResetDailyStats() {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        if (settings.lastResetDate < today) {
            settings.lastResetDate = today
            dailyMobileRx = 0L
            dailyMobileTx = 0L
            dailyWifiRx = 0L
            dailyWifiTx = 0L
            saveDailyStatsToStorage()
        }
    }

    private fun saveDailyStatsToStorage() {
        settings.dailyMobileRx = dailyMobileRx
        settings.dailyMobileTx = dailyMobileTx
        settings.dailyWifiRx = dailyWifiRx
        settings.dailyWifiTx = dailyWifiTx
    }

    private fun updateStats() {
        checkAndResetDailyStats()

        val currentTotalRx = TrafficStats.getTotalRxBytes()
        val currentTotalTx = TrafficStats.getTotalTxBytes()
        val currentMobileRx = TrafficStats.getMobileRxBytes()
        val currentMobileTx = TrafficStats.getMobileTxBytes()
        val currentTime = System.currentTimeMillis()

        val deltaTime = (currentTime - lastTime) / 1000.0
        if (deltaTime <= 0) return

        val isReboot = SystemClock.elapsedRealtime() < 60000 // 1 minute

        fun processUsage(current: Long, last: Long): Pair<Long, Long> {
            if (current == TrafficStats.UNSUPPORTED.toLong() || current < 0) return 0L to last
            if (last <= 0L) return 0L to current
            
            return when {
                current >= last -> (current - last) to current
                isReboot -> current to current
                else -> 0L to last // Interface drop, keep last and don't count delta
            }
        }

        val (dMobileRx, nextMobileRx) = processUsage(currentMobileRx, lastMobileRx)
        val (dMobileTx, nextMobileTx) = processUsage(currentMobileTx, lastMobileTx)
        val (dTotalRx, nextTotalRx) = processUsage(currentTotalRx, lastRxBytes)
        val (dTotalTx, nextTotalTx) = processUsage(currentTotalTx, lastTxBytes)

        // Wifi is total minus mobile, but ensure non-negative
        val dWifiRx = if (currentMobileRx != TrafficStats.UNSUPPORTED.toLong()) maxOf(0L, dTotalRx - dMobileRx) else dTotalRx
        val dWifiTx = if (currentMobileTx != TrafficStats.UNSUPPORTED.toLong()) maxOf(0L, dTotalTx - dMobileTx) else dTotalTx

        // Update daily totals in memory (cached)
        dailyMobileRx += dMobileRx
        dailyMobileTx += dMobileTx
        dailyWifiRx += dWifiRx
        dailyWifiTx += dWifiTx

        // Periodic save to storage (every 30 seconds)
        if (currentTime - lastSaveTime >= 30000L) {
            saveDailyStatsToStorage()
            lastSaveTime = currentTime
        }

        // Speed calculation
        val downloadSpeed = dTotalRx / deltaTime
        val uploadSpeed = dTotalTx / deltaTime

        // Update last values
        lastRxBytes = nextTotalRx
        lastTxBytes = nextTotalTx
        lastMobileRx = nextMobileRx
        lastMobileTx = nextMobileTx
        lastTime = currentTime

        val mobileTotal = dailyMobileRx + dailyMobileTx
        val wifiTotal = dailyWifiRx + dailyWifiTx

        val isDownload = downloadSpeed >= uploadSpeed
        val displaySpeed = if (isDownload) downloadSpeed else uploadSpeed
        
        // Check threshold
        val thresholdInBytes = when (settings.thresholdUnit) {
            "KB/s" -> settings.thresholdValue * 1024
            "MB/s" -> settings.thresholdValue * 1024 * 1024
            else -> settings.thresholdValue
        }

        val showRealTime = settings.isRealTimeEnabled && displaySpeed >= thresholdInBytes
        
        val speedText = formatSpeed(displaySpeed)
        val downSpeedText = formatSpeed(downloadSpeed)
        val upSpeedText = formatSpeed(uploadSpeed)
        val mobileText = formatBytes(mobileTotal)
        val wifiText = formatBytes(wifiTotal)

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
