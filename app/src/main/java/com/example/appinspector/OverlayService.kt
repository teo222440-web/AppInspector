package com.example.appinspector

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.ConnectivityManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var usm: UsageStatsManager
    private lateinit var box: LinearLayout
    private lateinit var title: TextView
    private lateinit var body: TextView
    private lateinit var scroll: ScrollView
    private val handler = Handler(Looper.getMainLooper())

    private var lastQuery = 0L
    private var cur: String? = null
    private var since = 0L
    private val screens = ArrayList<String>()
    private var staticInfo = ""
    private var netText = "…"
    private var tick = 0

    override fun onBind(i: Intent?): IBinder? = null
    override fun onStartCommand(i: Intent?, f: Int, id: Int) = START_NOT_STICKY

    override fun onCreate() {
        super.onCreate()
        usm = getSystemService(UsageStatsManager::class.java)
        wm = getSystemService(WindowManager::class.java)
        val ch = "inspector"
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(ch, "App Inspector", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, ch)
            .setContentTitle("App Inspector đang chạy")
            .setSmallIcon(android.R.drawable.ic_menu_info_details).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
        buildOverlay()
        lastQuery = System.currentTimeMillis() - 3000
        handler.post(loop)
    }

    override fun onDestroy() {
        handler.removeCallbacks(loop)
        try { wm.removeView(box) } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun buildOverlay() {
        val dm = resources.displayMetrics
        box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(228, 20, 24, 32))
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 8, 16, 8)
            setBackgroundColor(Color.rgb(40, 60, 110))
        }
        title = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 13f; text = "App Inspector (kéo ở đây)"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val min = TextView(this).apply {
            text = "  —  "; setTextColor(Color.WHITE); textSize = 16f
            setOnClickListener {
                scroll.visibility = if (scroll.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }
        val close = TextView(this).apply {
            text = "  ✕  "; setTextColor(Color.WHITE); textSize = 16f
            setOnClickListener { stopSelf() }
        }
        bar.addView(title); bar.addView(min); bar.addView(close)
        body = TextView(this).apply { setTextColor(Color.WHITE); textSize = 11f; setPadding(16, 8, 16, 8) }
        scroll = ScrollView(this).apply {
            addView(body)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (dm.heightPixels * 0.4).toInt())
        }
        box.addView(bar); box.addView(scroll)

        val lp = WindowManager.LayoutParams(
            (dm.widthPixels * 0.85).toInt(), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 20; y = 200 }

        var dx = 0f; var dy = 0f; var ox = 0; var oy = 0
        bar.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { dx = e.rawX; dy = e.rawY; ox = lp.x; oy = lp.y; true }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = ox + (e.rawX - dx).toInt(); lp.y = oy + (e.rawY - dy).toInt()
                    wm.updateViewLayout(box, lp); true
                }
                else -> false
            }
        }
        wm.addView(box, lp)
    }

    private val loop = object : Runnable {
        override fun run() {
            try { poll() } catch (e: Exception) { body.text = "Lỗi: ${e.message}" }
            handler.postDelayed(this, 1000)
        }
    }

    private fun poll() {
        val now = System.currentTimeMillis()
        val ev = usm.queryEvents(lastQuery, now)
        val e = UsageEvents.Event()
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED && e.packageName != packageName) {
                if (e.packageName != cur) {
                    cur = e.packageName; since = e.timeStamp
                    screens.clear(); netText = "…"
                    staticInfo = Analyzer.analyze(this, e.packageName)
                }
                val s = (e.className ?: "").substringAfterLast('.')
                if (s.isNotEmpty() && screens.lastOrNull() != s) screens.add(s)
            }
        }
        lastQuery = now
        val c = cur
        if (c == null) {
            body.text = "Hãy mở một app khác – công cụ sẽ phân tích app đó."
            return
        }
        if (tick++ % 3 == 0) netText = net(c, since)
        val sec = (now - since) / 1000
        title.text = Analyzer.label(this, c)
        body.text = "⏱ Phiên hiện tại: ${sec / 60}p${sec % 60}s\n" +
            "🧭 Màn hình đã qua: ${screens.takeLast(6).joinToString(" → ")}\n" +
            "🌐 Mạng (ước lượng): $netText\n\n$staticInfo"
    }

    private fun kb(b: Long) = if (b > 1_048_576) "%.1fMB".format(b / 1048576.0) else "${b / 1024}KB"

    private fun net(pkg: String, from: Long): String = try {
        val uid = packageManager.getApplicationInfo(pkg, 0).uid
        val nsm = getSystemService(NetworkStatsManager::class.java)
        fun sum(type: Int): Pair<Long, Long> {
            var rx = 0L; var tx = 0L
            val st = nsm.queryDetailsForUid(type, null, from, System.currentTimeMillis(), uid)
            val b = NetworkStats.Bucket()
            while (st.hasNextBucket()) { st.getNextBucket(b); rx += b.rxBytes; tx += b.txBytes }
            st.close()
            return rx to tx
        }
        val w = sum(ConnectivityManager.TYPE_WIFI)
        val m = try { sum(ConnectivityManager.TYPE_MOBILE) } catch (_: Exception) { 0L to 0L }
        "Wi-Fi ↓${kb(w.first)} ↑${kb(w.second)} | Di động ↓${kb(m.first)} ↑${kb(m.second)}"
    } catch (_: Exception) { "không đọc được" }
}
