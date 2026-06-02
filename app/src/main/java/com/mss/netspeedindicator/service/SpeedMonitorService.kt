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
import android.graphics.drawable.Icon
import android.net.TrafficStats
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import com.mss.netspeedindicator.R

class SpeedMonitorService : Service() {

    private val CHANNEL_ID = "speed_monitor_channel"
    private val NOTIFICATION_ID = 1
    private val handler = Handler(Looper.getMainLooper())
    
    private var lastRxBytes: Long = 0
    private var lastTxBytes: Long = 0
    private var lastTime: Long = 0

    private val updateRunnable = object : Runnable {
        override fun run() {
            updateSpeed()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        lastRxBytes = TrafficStats.getTotalRxBytes()
        lastTxBytes = TrafficStats.getTotalTxBytes()
        lastTime = System.currentTimeMillis()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("0 KB/s", true)
        startForeground(NOTIFICATION_ID, notification)
        handler.post(updateRunnable)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(updateRunnable)
        super.onDestroy()
    }

    private fun updateSpeed() {
        val currentRxBytes = TrafficStats.getTotalRxBytes()
        val currentTxBytes = TrafficStats.getTotalTxBytes()
        val currentTime = System.currentTimeMillis()

        val deltaRx = currentRxBytes - lastRxBytes
        val deltaTx = currentTxBytes - lastTxBytes
        val deltaTime = (currentTime - lastTime) / 1000.0

        if (deltaTime <= 0) return

        val downloadSpeed = deltaRx / deltaTime
        val uploadSpeed = deltaTx / deltaTime

        lastRxBytes = currentRxBytes
        lastTxBytes = currentTxBytes
        lastTime = currentTime

        // Choose which speed to show (usually the higher one or cycle)
        // For now, let's show download if it's significant, otherwise upload
        val isDownload = downloadSpeed >= uploadSpeed
        val displaySpeed = if (isDownload) downloadSpeed else uploadSpeed
        val speedText = formatSpeed(displaySpeed)

        val notification = createNotification(speedText, isDownload)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun formatSpeed(bytesPerSecond: Double): String {
        return when {
            bytesPerSecond >= 1024 * 1024 -> String.format("%.1f MB/s", bytesPerSecond / (1024 * 1024))
            bytesPerSecond >= 1024 -> String.format("%d KB/s", (bytesPerSecond / 1024).toInt())
            else -> String.format("%d B/s", bytesPerSecond.toInt())
        }
    }

    private fun createNotification(speedText: String, isDownload: Boolean): Notification {
        val bitmap = createSpeedBitmap(speedText, isDownload)
        
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Velocidade de Internet")
            .setContentText(if (isDownload) "Download: $speedText" else "Upload: $speedText")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            builder.setSmallIcon(IconCompat.createWithBitmap(bitmap))
        } else {
            builder.setSmallIcon(R.drawable.ic_net_speed)
        }

        return builder.build()
    }

    private fun createSpeedBitmap(speedText: String, isDownload: Boolean): Bitmap {
        // Status bar icons are typically 24x24dp. Let's use 96x96 for better resolution during drawing
        // and let the system scale it down.
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()

        paint.isAntiAlias = true
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        
        // Draw the value
        // We might need to split the value and unit if it's too long
        val valueOnly = speedText.split(" ")[0]
        val unit = speedText.split(" ")[1]

        paint.textSize = 50f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText(valueOnly, size / 2f, size / 2f + 10f, paint)

        paint.textSize = 25f
        canvas.drawText(unit, size / 2f, size - 10f, paint)

        // Draw arrow
        paint.style = Paint.Style.FILL
        if (isDownload) {
            // Down arrow
            val path = android.graphics.Path()
            path.moveTo(10f, 10f)
            path.lineTo(30f, 10f)
            path.lineTo(20f, 30f)
            path.close()
            canvas.drawPath(path, paint)
        } else {
            // Up arrow
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
