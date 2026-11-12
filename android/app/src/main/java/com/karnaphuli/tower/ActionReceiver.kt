package com.karnaphuli.tower

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        when (i.action) {
            "kt.STOP" -> AlertService.stop(c)
            "kt.DECLINE" -> AlertService.stop(c)
        }
    }
}
