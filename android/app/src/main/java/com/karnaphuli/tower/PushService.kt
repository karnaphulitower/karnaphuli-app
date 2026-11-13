package com.karnaphuli.tower

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class PushService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        Kt.prefs(this).edit().putString("fcm", token).apply()
        MainActivity.pushToken(token)
    }

    override fun onMessageReceived(msg: RemoteMessage) {
        val d = msg.data
        when (d["t"]) {
            "sos" -> if (!Kt.foreground) AlertService.start(this, "sos", d)
            "call" -> if (!Kt.foreground) AlertService.start(this, "call", d)
            "callend" -> AlertService.stopIf(this, "call", d["id"])
            "sosend" -> AlertService.stopIf(this, "sos", d["id"])
            else -> if (!Kt.foreground && !d["title"].isNullOrEmpty()) note(d)
        }
    }

    /* v244: ordinary app notification (chat, notice, bill, receipt, account) when the app is closed; silent 23:00-08:00 Dhaka */
    private fun note(d: Map<String, String>) {
        try {
            Kt.channels(this)
            val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Dhaka"))
            val h = cal.get(java.util.Calendar.HOUR_OF_DAY)
            val quiet = h >= 23 || h < 8
            val open = android.content.Intent(this, MainActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val tag = d["tag"] ?: "kt"
            val pi = android.app.PendingIntent.getActivity(this, tag.hashCode(), open,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
            val body = d["body"] ?: ""
            val n = androidx.core.app.NotificationCompat.Builder(this, Kt.CH_GEN)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(d["title"])
                .setContentText(body)
                .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setSilent(quiet)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pi)
                .build()
            androidx.core.app.NotificationManagerCompat.from(this).notify(tag, 7000, n)
        } catch (e: SecurityException) {
        } catch (e: Exception) {
        }
    }
}
