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
        }
    }
}
