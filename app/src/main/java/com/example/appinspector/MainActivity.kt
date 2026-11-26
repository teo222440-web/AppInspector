package com.example.appinspector

import android.Manifest
import android.app.Activity
import android.app.AppOpsManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }
        status = TextView(this).apply { textSize = 16f }
        root.addView(status)
        fun btn(t: String, f: () -> Unit) {
            root.addView(Button(this).apply { text = t; setOnClickListener { f() } })
        }
        btn("1. Cấp quyền hiển thị trên ứng dụng khác") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        btn("2. Cấp quyền truy cập dữ liệu sử dụng") {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        btn("3. Bật cửa sổ phân tích nổi") {
            if (Settings.canDrawOverlays(this) && hasUsage()) {
                startForegroundService(Intent(this, OverlayService::class.java))
                Toast.makeText(this, "Đã bật. Hãy mở app khác để phân tích.", Toast.LENGTH_LONG).show()
            } else Toast.makeText(this, "Hãy cấp đủ 2 quyền trước.", Toast.LENGTH_LONG).show()
        }
        btn("Tắt cửa sổ phân tích") { stopService(Intent(this, OverlayService::class.java)) }
        setContentView(root)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    }

    override fun onResume() {
        super.onResume()
        fun ok(v: Boolean) = if (v) "✔" else "✘"
        status.text = "Quyền hiển thị nổi: ${ok(Settings.canDrawOverlays(this))}\n" +
            "Quyền dữ liệu sử dụng: ${ok(hasUsage())}\n"
    }

    private fun hasUsage(): Boolean {
        val ops = getSystemService(AppOpsManager::class.java)
        return ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName
        ) == AppOpsManager.MODE_ALLOWED
    }
}
