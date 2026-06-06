package com.mss.netspeedindicator.repository

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import com.mss.netspeedindicator.models.AppUsageInfo
import com.mss.netspeedindicator.models.DataPoint
import com.mss.netspeedindicator.models.TimePeriodStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

import java.text.SimpleDateFormat
import java.util.Locale

import android.content.pm.ApplicationInfo

class NetworkStatsRepository(private val context: Context) {

    private val networkStatsManager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
    private val packageManager = context.packageManager

    suspend fun getStatsForPeriod(startTime: Long, endTime: Long): TimePeriodStats = withContext(Dispatchers.IO) {
        val mobileTotal = getTotalUsage(ConnectivityManager.TYPE_MOBILE, startTime, endTime)
        val wifiTotal = getTotalUsage(ConnectivityManager.TYPE_WIFI, startTime, endTime)
        
        val usageMap = mutableMapOf<Int, Pair<Long, Long>>() // uid -> (mobile, wifi)

        // Query summaries for all apps
        collectUidUsage(ConnectivityManager.TYPE_MOBILE, startTime, endTime, usageMap, isMobile = true)
        collectUidUsage(ConnectivityManager.TYPE_WIFI, startTime, endTime, usageMap, isMobile = false)

        val topApps = usageMap.mapNotNull { (uid, usage) ->
            if (usage.first + usage.second <= 0) return@mapNotNull null
            
            val packageNames = packageManager.getPackagesForUid(uid) ?: return@mapNotNull null
            val packageName = packageNames.firstOrNull() ?: return@mapNotNull null
            
            try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                AppUsageInfo(
                    packageName = packageName,
                    appName = packageManager.getApplicationLabel(appInfo).toString(),
                    icon = packageManager.getApplicationIcon(appInfo),
                    mobileData = usage.first,
                    wifiData = usage.second
                )
            } catch (e: Exception) {
                // If it's a known system UID, we can label it
                when (uid) {
                    0 -> AppUsageInfo("root", "Sistema (Root)", null, usage.first, usage.second)
                    1000 -> AppUsageInfo("system", "Sistema Android", null, usage.first, usage.second)
                    else -> AppUsageInfo(
                        packageName = packageName,
                        appName = packageName,
                        icon = null,
                        mobileData = usage.first,
                        wifiData = usage.second
                    )
                }
            }
        }.sortedByDescending { it.totalData }.take(25)

        val dataPoints = generateDataPoints(startTime, endTime)

        TimePeriodStats(
            dataPoints = dataPoints,
            totalMobile = mobileTotal,
            totalWifi = wifiTotal,
            topApps = topApps
        )
    }

    private fun collectUidUsage(
        networkType: Int, 
        startTime: Long, 
        endTime: Long, 
        map: MutableMap<Int, Pair<Long, Long>>,
        isMobile: Boolean
    ) {
        try {
            val stats = networkStatsManager.querySummary(networkType, null, startTime, endTime)
            val bucket = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                val uid = bucket.uid
                val current = map[uid] ?: (0L to 0L)
                val rxTx = bucket.rxBytes + bucket.txBytes
                
                map[uid] = if (isMobile) {
                    (current.first + rxTx) to current.second
                } else {
                    current.first to (current.second + rxTx)
                }
            }
            stats.close()
        } catch (e: Exception) {
            // Handle
        }
    }

    private fun getAppUsage(networkType: Int, uid: Int, startTime: Long, endTime: Long): Long {
        return try {
            val stats = networkStatsManager.queryDetailsForUid(networkType, null, startTime, endTime, uid)
            var total = 0L
            val bucket = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                total += bucket.rxBytes + bucket.txBytes
            }
            stats.close()
            total
        } catch (e: Exception) {
            0L
        }
    }

    private fun getTotalUsage(networkType: Int, startTime: Long, endTime: Long): Long {
        return try {
            val bucket = networkStatsManager.querySummaryForDevice(networkType, null, startTime, endTime)
            bucket.rxBytes + bucket.txBytes
        } catch (e: Exception) {
            0L
        }
    }

    private fun generateDataPoints(startTime: Long, endTime: Long): List<DataPoint> {
        val points = mutableListOf<DataPoint>()
        val duration = endTime - startTime
        
        // If duration is roughly 24 hours or less, use hourly breakdown
        if (duration <= 25 * 60 * 60 * 1000L) {
            val hourFormat = SimpleDateFormat("HH'h'", Locale.getDefault())
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = startTime
            
            // Generate for each hour
            for (i in 0 until 24) {
                val start = calendar.timeInMillis
                val label = hourFormat.format(calendar.time)
                calendar.add(Calendar.HOUR_OF_DAY, 1)
                val end = calendar.timeInMillis
                
                if (start > endTime) break
                
                val mobile = getTotalUsage(ConnectivityManager.TYPE_MOBILE, start, minOf(end, endTime))
                val wifi = getTotalUsage(ConnectivityManager.TYPE_WIFI, start, minOf(end, endTime))
                points.add(DataPoint(start, label, mobile, wifi))
            }
        } else {
            // For longer periods, use daily breakdown
            val dayFormat = SimpleDateFormat("dd/MM", Locale.getDefault())
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = startTime
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            while (calendar.timeInMillis < endTime) {
                val start = calendar.timeInMillis
                val label = dayFormat.format(calendar.time)
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                val end = calendar.timeInMillis
                
                val mobile = getTotalUsage(ConnectivityManager.TYPE_MOBILE, maxOf(start, startTime), minOf(end, endTime))
                val wifi = getTotalUsage(ConnectivityManager.TYPE_WIFI, maxOf(start, startTime), minOf(end, endTime))
                points.add(DataPoint(start, label, mobile, wifi))
            }
        }
        return points
    }
}
