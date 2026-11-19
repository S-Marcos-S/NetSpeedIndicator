package com.mss.netspeedindicator.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.TrafficStats
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import com.mss.netspeedindicator.MainActivity
import com.mss.netspeedindicator.R
import com.mss.netspeedindicator.data.AppSettings
import com.mss.netspeedindicator.models.ActiveAppConsumption
import com.mss.netspeedindicator.utils.PermissionUtils
import com.mss.netspeedindicator.utils.RootUtils
import java.util.Calendar

class SpeedMonitorService : Service() {

    companion object {
        const val ACTION_TOGGLE_APP_USAGE = "com.mss.netspeedindicator.action.TOGGLE_APP_USAGE"
    }

    private val CHANNEL_ID = "speed_monitor_channel"
    private val NOTIFICATION_ID = 1
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var settings: AppSettings

    private var lastRxBytes: Long = 0
    private var lastTxBytes: Long = 0
    private var lastTime: Long = 0
    private var lastStatsUpdateTime: Long = 0
    private var lastAppQueryTime: Long = 0

    // Cached daily stats from NetworkStatsManager
    private var dailyMobileTotal: Long = 0
    private var dailyWifiTotal: Long = 0

    // Active and daily app usage tracking
    private val previousUidBytes = mutableMapOf<Int, Long>()
    private val appNameCache = mutableMapOf<Int, String>()
    private var activeConsumingApps = listOf<ActiveAppConsumption>()
    private var topDailyApps = listOf<ActiveAppConsumption>()
    private var lastActiveAppsTimestamp: Long = 0L

    // Hybrid root engine state
    private var isRootActive: Boolean = false
    private var hasAttemptedRootGrant: Boolean = false

    private var isScreenOn = true
    private var lastNotificationText = ""
    private var lastIconSpeedText = ""
    private var lastIsDownload = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    // Force stats update when screen turns on
                    lastStatsUpdateTime = 0
                    lastAppQueryTime = 0
                    handler.removeCallbacks(updateRunnable)
                    handler.post(updateRunnable)
                }
                Intent.ACTION_SCREEN_OFF -> {
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

        checkAndInitRoot()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TOGGLE_APP_USAGE) {
            val newState = !settings.isAppUsageNotificationEnabled
            settings.isAppUsageNotificationEnabled = newState

            if (newState) {
                checkAndInitRoot()
                lastAppQueryTime = 0
            } else {
                activeConsumingApps = emptyList()
            }

            updateStats(forceNotify = true)
            return START_STICKY
        }

        val initialText = getString(R.string.service_running)
        val notification = createNotification("0 KB/s", "0 B/s", "0 B/s", "0 B", "0 B", false, initialText)
        startForeground(NOTIFICATION_ID, notification)

        lastRxBytes = TrafficStats.getTotalRxBytes()
        lastTxBytes = TrafficStats.getTotalTxBytes()
        lastStatsUpdateTime = 0
        lastAppQueryTime = 0

        checkAndInitRoot()

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

    private fun checkAndInitRoot() {
        if (!settings.isRootEnabled) {
            isRootActive = false
            return
        }
        Thread {
            try {
                if (RootUtils.isRootAvailable()) {
                    isRootActive = true
                    if (!hasAttemptedRootGrant) {
                        hasAttemptedRootGrant = true
                        RootUtils.autoGrantPermissions(packageName)
                    }
                } else {
                    isRootActive = false
                }
            } catch (_: Exception) {
                isRootActive = false
            }
        }.start()
    }

    private fun updateDailyTotalsFromSystem() {
        val networkStatsManager = getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager ?: return
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

    private fun updateAppConsumptionFromSystem(currentTime: Long, currentDeviceSpeed: Double) {
        if (!settings.isAppUsageNotificationEnabled) {
            activeConsumingApps = emptyList()
            return
        }

        // Auto-grant permission via root if available
        if (!PermissionUtils.hasUsageStatsPermission(this)) {
            if (settings.isRootEnabled && !hasAttemptedRootGrant) {
                checkAndInitRoot()
            }
            if (!PermissionUtils.hasUsageStatsPermission(this)) {
                activeConsumingApps = emptyList()
                return
            }
        }

        // If total device speed is zero or below threshold, phone is physically idle
        if (currentDeviceSpeed <= 0.0) {
            activeConsumingApps = emptyList()
        }

        val networkStatsManager = getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager ?: return

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis
        val endTime = currentTime

        val currentUidBytes = mutableMapOf<Int, Long>()
        collectUidTotals(networkStatsManager, ConnectivityManager.TYPE_MOBILE, startTime, endTime, currentUidBytes)
        collectUidTotals(networkStatsManager, ConnectivityManager.TYPE_WIFI, startTime, endTime, currentUidBytes)

        val timeDeltaSec = if (lastAppQueryTime > 0) (currentTime - lastAppQueryTime) / 1000.0 else 0.0

        if (previousUidBytes.isNotEmpty() && timeDeltaSec > 0.4 && currentDeviceSpeed > 0.0) {
            val activeList = mutableListOf<ActiveAppConsumption>()
            for ((uid, currentTotal) in currentUidBytes) {
                if (uid < 0) continue
                val previousTotal = previousUidBytes[uid] ?: 0L
                if (currentTotal > previousTotal && previousTotal > 0L) {
                    val delta = currentTotal - previousTotal
                    val rawSpeed = delta / timeDeltaSec
                    if (rawSpeed > 0.0) {
                        val appName = getAppNameForUid(uid)
                        activeList.add(ActiveAppConsumption(uid, appName, rawSpeed, currentTotal))
                    }
                }
            }

            if (activeList.isNotEmpty()) {
                val sumRawSpeed = activeList.sumOf { it.speedBytesPerSec }
                val normalizedList = if (sumRawSpeed > currentDeviceSpeed * 1.05 && sumRawSpeed > 0.0) {
                    val factor = currentDeviceSpeed / sumRawSpeed
                    activeList.map { app ->
                        app.copy(speedBytesPerSec = app.speedBytesPerSec * factor)
                    }
                } else {
                    activeList
                }
                activeConsumingApps = normalizedList.sortedByDescending { it.speedBytesPerSec }.take(3)
                lastActiveAppsTimestamp = currentTime
            } else if (currentTime - lastActiveAppsTimestamp > 4000L) {
                activeConsumingApps = emptyList()
            }
        }

        val topDaily = currentUidBytes.entries
            .filter { it.key >= 0 && it.value > 1024L }
            .sortedByDescending { it.value }
            .take(3)
            .map { (uid, bytes) ->
                ActiveAppConsumption(uid, getAppNameForUid(uid), 0.0, bytes)
            }
        topDailyApps = topDaily

        previousUidBytes.clear()
        previousUidBytes.putAll(currentUidBytes)
    }

    private fun collectUidTotals(
        manager: NetworkStatsManager,
        networkType: Int,
        startTime: Long,
        endTime: Long,
        map: MutableMap<Int, Long>
    ) {
        try {
            val stats = manager.querySummary(networkType, null, startTime, endTime)
            val bucket = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                val uid = bucket.uid
                val current = map[uid] ?: 0L
                map[uid] = current + bucket.rxBytes + bucket.txBytes
            }
            stats.close()
        } catch (e: Exception) {
            // Handled safely
        }
    }

    private fun getAppNameForUid(uid: Int): String {
        return appNameCache.getOrPut(uid) {
            when (uid) {
                0 -> getString(R.string.app_system_root)
                1000 -> getString(R.string.app_system_android)
                Process.myUid() -> getString(R.string.app_name)
                else -> {
                    val packages = packageManager.getPackagesForUid(uid)
                    val pkgName = packages?.firstOrNull()
                    if (pkgName != null) {
                        try {
                            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                packageManager.getApplicationInfo(pkgName, PackageManager.ApplicationInfoFlags.of(0))
                            } else {
                                @Suppress("DEPRECATION")
                                packageManager.getApplicationInfo(pkgName, 0)
                            }
                            packageManager.getApplicationLabel(appInfo).toString()
                        } catch (e: Exception) {
                            pkgName
                        }
                    } else {
                        "UID $uid"
                    }
                }
            }
        }
    }

    private fun updateStats(forceNotify: Boolean = false) {
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

        // Update app consumption stats (every 2s when active traffic, every 15s when idle)
        val appQueryInterval = if (displaySpeed > 0) 2000L else 15000L
        if (settings.isAppUsageNotificationEnabled && (currentTime - lastAppQueryTime >= appQueryInterval)) {
            updateAppConsumptionFromSystem(currentTime, displaySpeed)
            lastAppQueryTime = currentTime
        }

        // Check threshold
        val thresholdInBytes = when (settings.thresholdUnit) {
            "KB/s" -> settings.thresholdValue * 1024
            "MB/s" -> settings.thresholdValue * 1024 * 1024
            else -> settings.thresholdValue
        }

        val showRealTime = settings.isRealTimeEnabled && displaySpeed >= thresholdInBytes

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
                if (settings.isAppUsageNotificationEnabled && activeConsumingApps.isNotEmpty()) {
                    append(" [${activeConsumingApps.first().appName}]")
                }
            }
            if (settings.isDailyUsageEnabled) {
                if (isNotEmpty()) append(" | ")
                append("M:$mobileText W:$wifiText")
            }
        }

        // Only notify if something meaningful changed to save battery, or if forced
        if (forceNotify || contentText != lastNotificationText || (showRealTime && (speedText != lastIconSpeedText || isDownload != lastIsDownload))) {
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
        val notifyIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, notifyIntent, flags)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(contentText)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (settings.isAppUsageNotificationEnabled && activeConsumingApps.isNotEmpty()) {
            val topApp = activeConsumingApps.first()
            builder.setSubText("${topApp.appName} (${formatSpeed(topApp.speedBytesPerSec)})")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && showRealTime) {
            val bitmap = createSpeedBitmap(iconSpeedText)
            builder.setSmallIcon(IconCompat.createWithBitmap(bitmap))
        } else {
            builder.setSmallIcon(R.drawable.ic_net_speed)
        }

        val bigTextStyle = NotificationCompat.BigTextStyle()
        val bigText = buildString {
            if (settings.isRealTimeEnabled) {
                append(getString(R.string.download_prefix, downSpeedText))
                append("  |  ")
                append(getString(R.string.upload_prefix, upSpeedText))
            }

            if (settings.isAppUsageNotificationEnabled) {
                if (PermissionUtils.hasUsageStatsPermission(this@SpeedMonitorService)) {
                    if (activeConsumingApps.isNotEmpty()) {
                        if (isNotEmpty()) append("\n\n")
                        append(getString(R.string.active_apps_header))
                        for (app in activeConsumingApps) {
                            append("\n• ${app.appName}: ${formatSpeed(app.speedBytesPerSec)}")
                        }
                    } else if (topDailyApps.isNotEmpty()) {
                        if (isNotEmpty()) append("\n\n")
                        append(getString(R.string.top_daily_apps_header))
                        for (app in topDailyApps) {
                            append("\n• ${app.appName}: ${formatBytes(app.totalBytes)}")
                        }
                    }
                } else {
                    if (isNotEmpty()) append("\n\n")
                    append(getString(R.string.apps_permission_needed))
                }
            }

            if (settings.isDailyUsageEnabled) {
                if (isNotEmpty()) append("\n\n")
                append(getString(R.string.daily_mobile_prefix, mobileText))
                append("\n")
                append(getString(R.string.daily_wifi_prefix, wifiText))
            }
        }
        builder.setStyle(bigTextStyle.bigText(bigText))

        // Action button to toggle app usage display directly from notification
        val toggleIntent = Intent(this, SpeedMonitorService::class.java).apply {
            action = ACTION_TOGGLE_APP_USAGE
        }
        val togglePendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.getService(this, 101, toggleIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        } else {
            PendingIntent.getService(this, 101, toggleIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        val actionTitle = if (settings.isAppUsageNotificationEnabled) {
            getString(R.string.action_hide_apps)
        } else {
            getString(R.string.action_show_apps)
        }
        builder.addAction(R.drawable.ic_net_speed, actionTitle, togglePendingIntent)

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

        val parts = speedText.split(" ")
        val valueOnly = parts.getOrNull(0) ?: "0"
        val unit = parts.getOrNull(1) ?: "B/s"

        paint.textSize = 50f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText(valueOnly, size / 2f, size / 2f + 10f, paint)

        paint.textSize = 25f
        canvas.drawText(unit, size / 2f, size - 10f, paint)

        return bitmap
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.notification_title)
            val descriptionText = getString(R.string.service_running)
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
