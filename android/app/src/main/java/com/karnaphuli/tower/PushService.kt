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
            "sos" -> { if (!Kt.foreground) AlertService.start(this, "sos", d); ack(d["id"]) }
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
                .putExtra("go", d["go"] ?: "")
            val tag = d["tag"] ?: "kt"
            val pi = android.app.PendingIntent.getActivity(this, tag.hashCode(), open,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
            val body = d["body"] ?: ""
            val n = androidx.core.app.NotificationCompat.Builder(this, Kt.CH_GEN)
                .setSmallIcon(R.drawable.ic_stat)
                .setColor(0xFF1E3A8A.toInt())
                .setLargeIcon(android.graphics.BitmapFactory.decodeResource(resources, R.drawable.kt_large))
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

    /* v500: tell the sender this phone is really ringing (Firestore REST with the saved login) */
    private fun ack(sid: String?) {
        if (sid.isNullOrEmpty()) return
        val cfg = Kt.prefs(this).getString("ack", null) ?: return
        Thread {
            try {
                val c = org.json.JSONObject(cfg)
                val tk = java.net.URL("https://securetoken.googleapis.com/v1/token?key=" + c.getString("key")).openConnection() as java.net.HttpURLConnection
                tk.requestMethod = "POST"; tk.doOutput = true; tk.connectTimeout = 15000; tk.readTimeout = 15000
                tk.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                tk.outputStream.use { it.write(("grant_type=refresh_token&refresh_token=" + java.net.URLEncoder.encode(c.getString("rt"), "UTF-8")).toByteArray()) }
                val idt = org.json.JSONObject(tk.inputStream.bufferedReader().readText()).getString("id_token")
                val eid = "r" + c.optString("dev", "x")
                fun sv(v: String) = org.json.JSONObject().put("stringValue", v)
                val f = org.json.JSONObject().put("id", sv(eid)).put("sid", sv(sid)).put("k", sv(c.optString("k"))).put("who", sv(c.optString("who")))
                    .put("flat", sv(c.optString("flat"))).put("kind", sv("ring")).put("dev", sv(c.optString("dev"))).put("com", org.json.JSONObject().put("booleanValue", c.optBoolean("com")))
                    .put("t", org.json.JSONObject().put("integerValue", System.currentTimeMillis().toString()))
                val u = java.net.URL("https://firestore.googleapis.com/v1/projects/" + c.getString("proj") + "/databases/(default)/documents/" + c.getString("env") + "/sev_" + sid + "~" + eid)
                val w = u.openConnection() as java.net.HttpURLConnection
                w.requestMethod = "POST"; w.setRequestProperty("X-HTTP-Method-Override", "PATCH"); w.doOutput = true; w.connectTimeout = 15000; w.readTimeout = 15000
                w.setRequestProperty("Content-Type", "application/json"); w.setRequestProperty("Authorization", "Bearer " + idt)
                w.outputStream.use { it.write(org.json.JSONObject().put("fields", f).toString().toByteArray()) }
                w.responseCode
            } catch (_: Exception) {}
        }.start()
    }
}
