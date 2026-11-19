package com.mss.netspeedindicator.utils

import android.os.Build
import android.os.SystemClock
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

object RootUtils {

    private const val CACHE_TIMEOUT_MS = 60_000L

    @Volatile
    private var cachedRootAvailable: Boolean? = null

    @Volatile
    private var lastCheckTime: Long = 0L

    fun isRootAvailable(): Boolean {
        val now = SystemClock.elapsedRealtime()
        val cached = cachedRootAvailable
        if (cached != null && (cached || now - lastCheckTime < CACHE_TIMEOUT_MS)) {
            return cached
        }

        val available = checkRootMethod()
        cachedRootAvailable = available
        lastCheckTime = now
        return available
    }

    private fun checkRootMethod(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )
        val hasBinary = paths.any { File(it).exists() } || canExecuteSu()
        if (!hasBinary) return false

        return executeSuCommand("id")?.contains("uid=0") == true
    }

    private fun canExecuteSu(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }

    fun executeSuCommand(command: String, timeoutSec: Long = 3): String? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?

            val completed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                process.waitFor(timeoutSec, TimeUnit.SECONDS)
            } else {
                process.waitFor()
                true
            }

            if (!completed) {
                process.destroy()
                return null
            }

            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            reader.close()
            output.toString().trim()
        } catch (e: Exception) {
            null
        }
    }

    fun autoGrantPermissions(packageName: String): Boolean {
        return try {
            val cmd = "pm grant $packageName android.permission.PACKAGE_USAGE_STATS 2>/dev/null; " +
                    "cmd appops set $packageName GET_USAGE_STATS allow 2>/dev/null; " +
                    "appops set $packageName GET_USAGE_STATS allow 2>/dev/null"
            executeSuCommand(cmd) != null
        } catch (e: Exception) {
            false
        }
    }
}
