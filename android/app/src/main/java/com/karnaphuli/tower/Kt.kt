package com.karnaphuli.tower

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.os.Build

object Kt {
    const val CH_SOS = "sos_alarm"
    const val CH_CALL = "calls"
    const val CH_GEN = "general"
    const val CH_QUICK = "quick_sos"
    const val NID_QUICK = 9003
    const val NID_SOS = 9001
    const val NID_CALL = 9002
    @Volatile var foreground = false

    fun prefs(c: Context) = c.getSharedPreferences("kt", Context.MODE_PRIVATE)

    fun channels(c: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = c.getSystemService(NotificationManager::class.java)
        val sos = NotificationChannel(CH_SOS, "SOS জরুরি এলার্ম", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "জরুরি বিপদ-সংকেত"; setSound(null, null); enableVibration(false); setBypassDnd(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        val call = NotificationChannel(CH_CALL, "কল", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "আসা কল"; setSound(null, null); enableVibration(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        val gen = NotificationChannel(CH_GEN, "সাধারণ", NotificationManager.IMPORTANCE_DEFAULT)
        val quick = NotificationChannel(CH_QUICK, "লক স্ক্রিনে SOS বাটন", NotificationManager.IMPORTANCE_LOW).apply {
            description = "ফোন না খুলেই পরিবারকে SOS"; setSound(null, null); enableVibration(false); setShowBadge(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannels(listOf(sos, call, gen, quick))
    }

    /** v493: always-there notification: tap = family SOS over the lock screen (no unlock) */
    fun quick(c: Context) {
        try {
            val nm = androidx.core.app.NotificationManagerCompat.from(c)
            if (!prefs(c).getBoolean("quick", true)) { nm.cancel(NID_QUICK); return }
            channels(c)
            fun pi(go: String, code: Int) = android.app.PendingIntent.getActivity(c, code,
                android.content.Intent(c, LockActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("go", go),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
            val n = androidx.core.app.NotificationCompat.Builder(c, CH_QUICK)
                .setSmallIcon(R.drawable.ic_stat).setColor(0xFFD32F2F.toInt())
                .setOngoing(true).setAutoCancel(false).setShowWhen(false)
                .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
                .addAction(0, "👨‍👩‍👧 পরিবারকে SOS", pi("sosfam", 42))
                .addAction(0, "🚨 অন্য SOS", pi("sos", 43))
                .build()
            nm.notify(NID_QUICK, n)
        } catch (_: SecurityException) {} catch (_: Exception) {}
    }

    val alarmAttrs: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    val ringAttrs: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
}
