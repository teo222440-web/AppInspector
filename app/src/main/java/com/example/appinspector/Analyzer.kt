package com.example.appinspector

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipFile

object Analyzer {
    private val cache = HashMap<String, String>()

    private val SDKS = mapOf(
        "com.google.firebase" to "Firebase", "com.google.android.gms.ads" to "Google Ads",
        "com.google.android.gms.measurement" to "Google Analytics", "com.facebook" to "Facebook SDK",
        "com.appsflyer" to "AppsFlyer", "com.adjust" to "Adjust", "com.applovin" to "AppLovin",
        "com.unity3d.ads" to "Unity Ads", "io.sentry" to "Sentry", "com.onesignal" to "OneSignal",
        "com.mixpanel" to "Mixpanel", "com.bytedance" to "ByteDance/Pangle"
    )
    private val PERMS = mapOf(
        "CAMERA" to "Camera", "RECORD_AUDIO" to "Micro", "ACCESS_FINE_LOCATION" to "Vị trí chính xác",
        "ACCESS_COARSE_LOCATION" to "Vị trí gần đúng", "ACCESS_BACKGROUND_LOCATION" to "Vị trí nền",
        "READ_CONTACTS" to "Danh bạ", "READ_SMS" to "Đọc SMS", "SEND_SMS" to "Gửi SMS",
        "READ_CALL_LOG" to "Nhật ký cuộc gọi", "READ_PHONE_STATE" to "Trạng thái máy",
        "SYSTEM_ALERT_WINDOW" to "Hiển thị đè app khác", "READ_EXTERNAL_STORAGE" to "Đọc bộ nhớ",
        "WRITE_EXTERNAL_STORAGE" to "Ghi bộ nhớ", "MANAGE_EXTERNAL_STORAGE" to "Toàn quyền bộ nhớ",
        "RECEIVE_BOOT_COMPLETED" to "Tự chạy khi bật máy", "FOREGROUND_SERVICE" to "Dịch vụ nền",
        "REQUEST_INSTALL_PACKAGES" to "Cài app khác", "WAKE_LOCK" to "Giữ máy thức",
        "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" to "Bỏ tối ưu pin", "BLUETOOTH_CONNECT" to "Bluetooth"
    )

    fun label(c: Context, pkg: String): String = try {
        c.packageManager.getApplicationLabel(c.packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) { pkg }

    @Suppress("DEPRECATION")
    fun analyze(c: Context, pkg: String): String = cache.getOrPut(pkg) {
        try {
            val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES or
                PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS
            val pi = c.packageManager.getPackageInfo(pkg, flags)
            val ai = pi.applicationInfo
            val df = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val names = ArrayList<String>()
            pi.activities?.forEach { names += it.name }
            pi.services?.forEach { names += it.name }
            pi.receivers?.forEach { names += it.name }
            pi.providers?.forEach { names += it.name }
            val req = pi.requestedPermissions.orEmpty().map { it.substringAfterLast('.') }
            val hits = req.mapNotNull { PERMS[it] }
            val sdks = SDKS.filterKeys { k -> names.any { it.startsWith(k) } }.values

            val sb = StringBuilder()
            sb.append("📦 $pkg\n")
            sb.append("v${pi.versionName} | target SDK ${ai.targetSdkVersion} | min ${ai.minSdkVersion}\n")
            sb.append("Cài: ${df.format(Date(pi.firstInstallTime))} | Cập nhật: ${df.format(Date(pi.lastUpdateTime))}\n")
            sb.append("APK: ${File(ai.sourceDir).length() / 1048576} MB")
            if (ai.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) sb.append(" | DEBUGGABLE")
            if (ai.flags and ApplicationInfo.FLAG_SYSTEM != 0) sb.append(" | App hệ thống")
            sb.append("\n\n🧱 Công nghệ: ${framework(ai)}\n")
            sb.append("🧩 ${pi.activities?.size ?: 0} màn hình | ${pi.services?.size ?: 0} service | " +
                "${pi.receivers?.size ?: 0} receiver | ${pi.providers?.size ?: 0} provider\n")
            sb.append("📡 SDK phát hiện: ${sdks.joinToString(", ").ifEmpty { "không thấy SDK quảng cáo/theo dõi phổ biến" }}\n")
            sb.append("🔐 Quyền (${req.size}): ${hits.joinToString(", ").ifEmpty { "không có quyền nhạy cảm" }}\n\n")
            sb.append("💡 Nhận định:\n")
            if ("INTERNET" !in req) sb.append("• Không xin quyền mạng → có thể chạy offline.\n")
            if ("RECEIVE_BOOT_COMPLETED" in req) sb.append("• Có thể tự khởi động cùng máy.\n")
            if ("FOREGROUND_SERVICE" in req || "WAKE_LOCK" in req) sb.append("• Có thể chạy nền/giữ máy thức.\n")
            if (sdks.isNotEmpty()) sb.append("• Có thể gửi dữ liệu thống kê/quảng cáo ra ngoài.\n")
            sb.toString()
        } catch (e: Exception) {
            "Không phân tích được app này: ${e.message}"
        }
    }

    private fun framework(ai: ApplicationInfo): String {
        val found = linkedSetOf<String>()
        var dex = 0; var so = 0
        try {
            ZipFile(ai.sourceDir).use { z ->
                for (e in z.entries()) {
                    val n = e.name
                    when {
                        n.endsWith("libflutter.so") -> found += "Flutter"
                        n.endsWith("libreactnativejni.so") || n.contains("index.android.bundle") -> found += "React Native"
                        n.endsWith("libunity.so") || n.endsWith("libil2cpp.so") -> found += "Unity"
                        n.contains("libmonodroid") -> found += "Xamarin/.NET"
                        n.startsWith("assets/www/") -> found += "Cordova/Ionic"
                        Regex("classes\\d*\\.dex").matches(n) -> dex++
                    }
                    if (n.startsWith("lib/") && n.endsWith(".so")) so++
                }
            }
        } catch (_: Exception) {}
        return found.joinToString(", ").ifEmpty { "Native (Java/Kotlin)" } + " | $dex dex | $so thư viện .so"
    }
}
