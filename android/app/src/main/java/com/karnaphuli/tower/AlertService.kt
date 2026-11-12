package com.karnaphuli.tower

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import kotlin.math.PI
import kotlin.math.sin

/** Rings like a phone call (looping ringtone) or screams like a siren (SOS), even when the app is closed. */
class AlertService : Service() {
    companion object {
        @Volatile var kind: String? = null
        @Volatile var curId: String? = null
        fun start(c: Context, k: String, d: Map<String, String>) {
            val i = Intent(c, AlertService::class.java).putExtra("k", k)
            d.forEach { (a, b) -> i.putExtra("d_$a", b) }
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
        }
        fun stop(c: Context) { c.stopService(Intent(c, AlertService::class.java)) }
        fun stopIf(c: Context, k: String, id: String?) { if (kind == k && (id == null || id == curId)) stop(c) }
    }

    private var player: MediaPlayer? = null
    private var track: AudioTrack? = null
    @Volatile private var sirenOn = false
    private var oldVol = -1
    private var wake: PowerManager.WakeLock? = null
    private val h = Handler(Looper.getMainLooper())

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        if (i == null) { stopSelf(); return START_NOT_STICKY }
        Kt.channels(this)
        stopSound()
        val k = i.getStringExtra("k") ?: "sos"
        val id = i.getStringExtra("d_id") ?: ""
        val title = i.getStringExtra("d_title") ?: "কর্ণফুলী টাওয়ার"
        val body = i.getStringExtra("d_body") ?: ""
        val video = i.getStringExtra("d_v") == "video"
        kind = k; curId = id

        val n = if (k == "call") callNotif(id, title, body, video) else sosNotif(id, title, body)
        val nid = if (k == "call") Kt.NID_CALL else Kt.NID_SOS
        if (Build.VERSION.SDK_INT >= 29) startForeground(nid, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(nid, n)

        try {
            wake = (getSystemService(POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "kt:alert").apply { acquire(4 * 60 * 1000L) }
        } catch (_: Exception) {}

        if (k == "call") ring() else siren()
        vibrate(k == "call")
        // also try to show the full-screen activity directly (works when allowed)
        try {
            val a = Intent(this, if (k == "call") CallActivity::class.java else AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("id", id).putExtra("title", title).putExtra("body", body).putExtra("video", video)
            startActivity(a)
        } catch (_: Exception) {}

        h.removeCallbacksAndMessages(null)
        h.postDelayed({ stopSelf() }, if (k == "call") 60_000L else 180_000L)
        return START_NOT_STICKY
    }

    private fun pi(cls: Class<*>, id: String, title: String, body: String, video: Boolean, code: Int): PendingIntent {
        val a = Intent(this, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("id", id).putExtra("title", title).putExtra("body", body).putExtra("video", video)
        return PendingIntent.getActivity(this, code, a, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun callNotif(id: String, title: String, body: String, video: Boolean): Notification {
        val full = pi(CallActivity::class.java, id, title, body, video, 11)
        val ans = PendingIntent.getActivity(this, 12,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("acc", id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val dec = PendingIntent.getBroadcast(this, 13, Intent(this, ActionReceiver::class.java).setAction("kt.DECLINE").putExtra("id", id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val caller = Person.Builder().setName(title.replace(Regex("^[📞📹]\\s*"), "")).setImportant(true).build()
        return NotificationCompat.Builder(this, Kt.CH_CALL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title).setContentText(body)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true).setAutoCancel(false)
            .setFullScreenIntent(full, true).setContentIntent(full)
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(caller, dec, ans).setIsVideo(video))
            .build()
    }

    private fun sosNotif(id: String, title: String, body: String): Notification {
        val full = pi(AlarmActivity::class.java, id, title, body, false, 21)
        val stop = PendingIntent.getBroadcast(this, 22, Intent(this, ActionReceiver::class.java).setAction("kt.STOP"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, Kt.CH_SOS)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title).setContentText(body)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(0xFFD32F2F.toInt()).setColorized(true)
            .setOngoing(true).setAutoCancel(false)
            .setFullScreenIntent(full, true).setContentIntent(full)
            .addAction(0, "🔕 বন্ধ করুন", stop)
            .build()
    }

    /** Phone's own ringtone, looping, on the ringtone stream — like WhatsApp. */
    private fun ring() {
        try {
            val am = getSystemService(AUDIO_SERVICE) as AudioManager
            if (am.ringerMode != AudioManager.RINGER_MODE_NORMAL) return // respect silent/vibrate like WhatsApp
            val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            player = MediaPlayer().apply {
                setAudioAttributes(Kt.ringAttrs)
                setDataSource(this@AlertService, uri)
                isLooping = true
                prepare(); start()
            }
        } catch (e: Exception) {
            // fallback: built-in tone
            try { startTone(false) } catch (_: Exception) {}
        }
    }

    /** Siren on the ALARM stream at full volume (plays even in silent mode). */
    private fun siren() {
        try {
            val am = getSystemService(AUDIO_SERVICE) as AudioManager
            oldVol = am.getStreamVolume(AudioManager.STREAM_ALARM)
            am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        } catch (_: Exception) {}
        startTone(true)
    }

    private fun startTone(sos: Boolean) {
        val sr = 22050
        val buf = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(4096)
        val t = AudioTrack.Builder()
            .setAudioAttributes(if (sos) Kt.alarmAttrs else Kt.ringAttrs)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(sr).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(buf).setTransferMode(AudioTrack.MODE_STREAM).build()
        track = t; sirenOn = true; t.play()
        Thread {
            val chunk = ShortArray(1024); var ph = 0.0; var n = 0L
            while (sirenOn) {
                for (j in chunk.indices) {
                    val ts = n.toDouble() / sr
                    val f: Double; val amp: Double
                    if (sos) { // wail 600 → 1300 Hz, 1.2 s up / down
                        val c = (ts % 2.4) / 1.2; val x = if (c < 1) c else 2 - c
                        f = 600 + 700 * x; amp = 1.0
                    } else { // dual-tone ring: 2 s on, 2 s off
                        f = if ((ts * 20).toInt() % 2 == 0) 480.0 else 440.0
                        amp = if (ts % 4 < 2) 0.8 else 0.0
                    }
                    ph += 2 * PI * f / sr; if (ph > 2 * PI) ph -= 2 * PI
                    chunk[j] = (sin(ph) * 32000 * amp).toInt().toShort(); n++
                }
                try { t.write(chunk, 0, chunk.size) } catch (_: Exception) { break }
            }
        }.start()
    }

    private fun vibrate(call: Boolean) {
        try {
            val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
            val p = if (call) longArrayOf(0, 800, 600, 800, 1800) else longArrayOf(0, 1000, 300, 1000, 300)
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(p, 0)) else @Suppress("DEPRECATION") v.vibrate(p, 0)
        } catch (_: Exception) {}
    }

    private fun stopSound() {
        sirenOn = false
        try { player?.stop(); player?.release() } catch (_: Exception) {}
        player = null
        try { track?.stop(); track?.release() } catch (_: Exception) {}
        track = null
        try { (getSystemService(VIBRATOR_SERVICE) as Vibrator).cancel() } catch (_: Exception) {}
        if (oldVol >= 0) try { (getSystemService(AUDIO_SERVICE) as AudioManager).setStreamVolume(AudioManager.STREAM_ALARM, oldVol, 0) } catch (_: Exception) {}
        oldVol = -1
    }

    override fun onDestroy() {
        h.removeCallbacksAndMessages(null)
        stopSound()
        try { wake?.release() } catch (_: Exception) {}
        kind = null; curId = null
        sendBroadcast(Intent("kt.ALERT_DONE").setPackage(packageName))
        super.onDestroy()
    }
}
