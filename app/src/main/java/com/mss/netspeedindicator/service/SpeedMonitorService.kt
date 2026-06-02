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

    private val updateRunnable = object : Runnable {
        override fun run() {
            updateStats()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        settings = AppSettings(this)
        createNotificationChannel()
        lastRxBytes = TrafficStats.getTotalRxBytes()
        lastTxBytes = TrafficStats.getTotalTxBytes()
        lastTime = System.currentTimeMillis()
        checkAndResetDailyStats()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("0 KB/s", true, "0 B/s", "0 B/s", "0 B", "0 B", false)
        startForeground(NOTIFICATION_ID, notification)
        handler.post(updateRunnable)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
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
            settings.baseMobileRx = TrafficStats.getMobileRxBytes()
            settings.baseMobileTx = TrafficStats.getMobileTxBytes()
            settings.baseWifiRx = TrafficStats.getTotalRxBytes() - settings.baseMobileRx
            settings.baseWifiTx = TrafficStats.getTotalTxBytes() - settings.baseMobileTx
        }
    }

    private fun updateStats() {
        checkAndResetDailyStats()

        val currentRxBytes = TrafficStats.getTotalRxBytes()
        val currentTxBytes = TrafficStats.getTotalTxBytes()
        val currentTime = System.currentTimeMillis()

        val deltaRx = currentRxBytes - lastRxBytes
        val deltaTx = currentTxBytes - lastTxBytes
        val deltaTime = (currentTime - lastTime) / 1000.0

        lastRxBytes = currentRxBytes
        lastTxBytes = currentTxBytes
        lastTime = currentTime

        if (deltaTime <= 0) return

        val downloadSpeed = deltaRx / deltaTime
        val uploadSpeed = deltaTx / deltaTime

        val currentMobileRx = TrafficStats.getMobileRxBytes()
        val currentMobileTx = TrafficStats.getMobileTxBytes()
        val currentTotalRx = TrafficStats.getTotalRxBytes()
        val currentTotalTx = TrafficStats.getTotalTxBytes()

        val mobileTotal = (currentMobileRx - settings.baseMobileRx) + (currentMobileTx - settings.baseMobileTx)
        val wifiTotal = (currentTotalRx - currentMobileRx - settings.baseWifiRx) + (currentTotalTx - currentMobileTx - settings.baseWifiTx)

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

        val notification = createNotification(speedText, isDownload, downSpeedText, upSpeedText, mobileText, wifiText, showRealTime)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
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
        isDownload: Boolean, 
        downSpeedText: String, 
        upSpeedText: String, 
        mobileText: String, 
        wifiText: String,
        showRealTime: Boolean
    ): Notification {
        val bitmap = createSpeedBitmap(iconSpeedText, isDownload)
        
        val contentText = buildString {
            if (showRealTime) {
                append("↓$downSpeedText ↑$upSpeedText")
            }
            if (settings.isDailyUsageEnabled) {
                if (isNotEmpty()) append(" | ")
                append("M:$mobileText W:$wifiText")
            }
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Indicador de Rede")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && showRealTime) {
            builder.setSmallIcon(IconCompat.createWithBitmap(bitmap))
        } else {
            builder.setSmallIcon(R.drawable.ic_net_speed)
        }

        // Expanded view showing details
        val bigTextStyle = NotificationCompat.BigTextStyle()
        val bigText = buildString {
            if (showRealTime) {
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

    private fun createSpeedBitmap(speedText: String, isDownload: Boolean): Bitmap {
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

        paint.style = Paint.Style.FILL
        if (isDownload) {
            val path = android.graphics.Path()
            path.moveTo(10f, 10f)
            path.lineTo(30f, 10f)
            path.lineTo(20f, 30f)
            path.close()
            canvas.drawPath(path, paint)
        } else {
            val path = android.graphics.Path()
            path.moveTo(size - 30f, 30f)
            path.lineTo(size - 10f, 30f)
            path.lineTo(size - 20f, 10f)
            path.close()
            canvas.drawPath(path, paint)
        }

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
