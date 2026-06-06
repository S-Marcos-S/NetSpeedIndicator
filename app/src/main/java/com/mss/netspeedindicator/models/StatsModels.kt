package com.mss.netspeedindicator.models

import android.graphics.drawable.Drawable

data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val mobileData: Long,
    val wifiData: Long,
    val totalData: Long = mobileData + wifiData
)

data class AppUsageSegment(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val bytes: Long,
    val color: Int
)

data class DataPoint(
    val timestamp: Long,
    val label: String,
    val mobileData: Long,
    val wifiData: Long,
    val appSegments: List<AppUsageSegment> = emptyList()
)

data class TimePeriodStats(
    val dataPoints: List<DataPoint>,
    val totalMobile: Long,
    val totalWifi: Long,
    val topApps: List<AppUsageInfo>
)
