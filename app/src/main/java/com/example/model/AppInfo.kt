package com.example.model

import android.graphics.Bitmap
import android.graphics.drawable.Drawable

/**
 * Represents an installed app or an APK file selected on the device.
 */
data class AppInfo(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdkVersion: Int = 21,
    val targetSdkVersion: Int = 34,
    val sourceDir: String, // Absolute path to the .apk file on device
    val apkSize: Long = 0L,
    val isSystemApp: Boolean = false,
    val iconBitmap: Bitmap? = null,
    val iconDrawable: Drawable? = null,
    val installLocation: Int = 0, // 0: Auto, 1: Internal, 2: External
    val permissions: List<String> = emptyList(),
    val activities: List<String> = emptyList(),
    val services: List<String> = emptyList(),
    val receivers: List<String> = emptyList(),
    val isSelectedFile: Boolean = false
) {
    val formattedSize: String
        get() {
            val mb = apkSize / (1024.0 * 1024.0)
            return if (mb >= 1.0) {
                String.format("%.2f MB", mb)
            } else {
                val kb = apkSize / 1024.0
                String.format("%.1f KB", kb)
            }
        }
}
