package com.karnaphuli.tower

/** Same app, shown over the lock screen (SOS alarm, answering a call, family SOS from the notification). */
class LockActivity : MainActivity() {
    override val lockMode = true
}
