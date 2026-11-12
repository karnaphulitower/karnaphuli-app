package com.karnaphuli.tower

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Shared base: shows over lock screen, turns screen on, closes when the alert stops. */
abstract class AlertActivity : Activity() {
    private val done = object : BroadcastReceiver() { override fun onReceive(c: Context, i: Intent) { finish() } }
    protected fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        val f = IntentFilter("kt.ALERT_DONE")
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(done, f, Context.RECEIVER_NOT_EXPORTED) else registerReceiver(done, f)
        build()
    }
    override fun onNewIntent(i: Intent) { super.onNewIntent(i); setIntent(i); build() }
    override fun onDestroy() { try { unregisterReceiver(done) } catch (_: Exception) {}; super.onDestroy() }
    abstract fun build()

    protected fun txt(s: String, sz: Float, col: Int = Color.WHITE, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = sz; setTextColor(col); gravity = Gravity.CENTER
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }
    protected fun roundBtn(label: String, bg: Int, onClick: () -> Unit) = Button(this).apply {
        text = label; textSize = 18f; setTextColor(Color.WHITE); isAllCaps = false
        background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = dp(40).toFloat(); setColor(bg) }
        setPadding(dp(24), dp(14), dp(24), dp(14))
        setOnClickListener { onClick() }
    }
    protected fun openApp(acc: String?) {
        val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (acc != null) i.putExtra("acc", acc)
        startActivity(i)
    }
}

class CallActivity : AlertActivity() {
    override fun build() {
        val title = intent.getStringExtra("title") ?: "কল"
        val video = intent.getBooleanExtra("video", false)
        val id = intent.getStringExtra("id")
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#0b141a")); setPadding(dp(24), dp(80), dp(24), dp(60))
        }
        root.addView(txt("কর্ণফুলী টাওয়ার", 14f, Color.parseColor("#8fa3ad")))
        root.addView(txt(title.replace(Regex("^[📞📹]\\s*"), ""), 30f, bold = true).apply { setPadding(0, dp(40), 0, dp(10)) })
        root.addView(txt(if (video) "📹 ভিডিও কল আসছে…" else "📞 কল আসছে…", 17f, Color.parseColor("#c9d6dc")))
        root.addView(LinearLayout(this), LinearLayout.LayoutParams(1, 0, 1f))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(10), 0, dp(10), 0) }
        row.addView(roundBtn("✖ কাটুন", Color.parseColor("#e53935")) { AlertService.stop(this); finish() }, lp)
        row.addView(roundBtn("📞 ধরুন", Color.parseColor("#25a244")) { AlertService.stop(this); openApp(id); finish() }, lp)
        root.addView(row)
        setContentView(root)
    }
}

class AlarmActivity : AlertActivity() {
    override fun build() {
        val title = intent.getStringExtra("title") ?: "🚨 জরুরি"
        val body = intent.getStringExtra("body") ?: ""
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#b71c1c")); setPadding(dp(24), dp(80), dp(24), dp(60))
        }
        root.addView(txt("🚨", 72f))
        root.addView(txt(title, 28f, bold = true).apply { setPadding(0, dp(20), 0, dp(12)) })
        root.addView(txt(body, 18f, Color.parseColor("#ffe0e0")))
        root.addView(LinearLayout(this), LinearLayout.LayoutParams(1, 0, 1f))
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(8), 0, dp(8)) }
        root.addView(roundBtn("📱 App খুলে দেখুন", Color.parseColor("#2f6b4f")) { AlertService.stop(this); openApp(null); finish() }, lp)
        root.addView(roundBtn("🔕 শব্দ বন্ধ করুন", Color.parseColor("#424242")) { AlertService.stop(this); finish() }, lp)
        setContentView(root)
    }
}
