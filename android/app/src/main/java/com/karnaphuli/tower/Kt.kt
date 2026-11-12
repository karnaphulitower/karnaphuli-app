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
        nm.createNotificationChannels(listOf(sos, call, gen))
    }

    val alarmAttrs: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    val ringAttrs: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
}
