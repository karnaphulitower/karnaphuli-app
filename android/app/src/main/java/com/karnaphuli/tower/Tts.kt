package com.karnaphuli.tower

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Phone's own text-to-speech (Android WebView has no speechSynthesis). Used for SOS voice. */
object Tts {
    private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    private val pending = ArrayList<() -> Unit>()

    fun init(c: Context, then: (() -> Unit)? = null) {
        synchronized(pending) {
            if (tts != null) {
                if (ready) { then?.invoke(); return }
                if (then != null) pending.add(then)
                return
            }
            if (then != null) pending.add(then)
        }
        tts = TextToSpeech(c.applicationContext) { st ->
            ready = st == TextToSpeech.SUCCESS
            try {
                tts?.setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            } catch (_: Exception) {}
            val p = synchronized(pending) { val x = ArrayList(pending); pending.clear(); x }
            p.forEach { try { it() } catch (_: Exception) {} }
        }
    }

    fun has(lang: String): Boolean {
        val t = tts ?: return false
        if (!ready) return false
        return try { t.isLanguageAvailable(Locale.forLanguageTag(lang)) >= TextToSpeech.LANG_AVAILABLE } catch (_: Exception) { false }
    }

    fun speak(c: Context, text: String, lang: String, done: () -> Unit) {
        init(c) {
            val t = tts
            if (t == null || !ready) { done(); return@init }
            val ok = try { t.setLanguage(Locale.forLanguageTag(lang)) >= TextToSpeech.LANG_AVAILABLE } catch (_: Exception) { false }
            if (!ok) try { t.setLanguage(Locale.US) } catch (_: Exception) {}
            t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { done() }
                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) { done() }
                override fun onError(id: String?, code: Int) { done() }
            })
            t.setSpeechRate(0.95f)
            t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kt" + System.currentTimeMillis())
        }
    }

    fun stop() { try { tts?.stop() } catch (_: Exception) {} }
}
