package com.karnaphuli.tower

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/* v246: keeps a running call alive while the screen is off or the app is in the background */
class CallService : Service() {
    companion object {
        fun start(c: Context) { try { if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(Intent(c, CallService::class.java)) else c.startService(Intent(c, CallService::class.java)) } catch (_: Exception) {} }
        fun stop(c: Context) { try { c.stopService(Intent(c, CallService::class.java)) } catch (_: Exception) {} }
    }
    override fun onBind(i: Intent?): IBinder? = null
    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        Kt.channels(this)
        val open = PendingIntent.getActivity(this, 31, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, Kt.CH_GEN)
            .setSmallIcon(R.drawable.ic_stat).setContentTitle("📞 কল চলছে").setContentText("ফিরে যেতে চাপুন")
            .setOngoing(true).setSilent(true).setContentIntent(open).build()
        try {
            if (Build.VERSION.SDK_INT >= 30) startForeground(7101, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else startForeground(7101, n)
        } catch (_: Exception) { stopSelf() }
        return START_NOT_STICKY
    }
}
