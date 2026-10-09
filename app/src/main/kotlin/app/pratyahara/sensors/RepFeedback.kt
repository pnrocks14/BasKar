package app.pratyahara.sensors

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Bundle
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Tells you each squat counted without looking at the screen: a buzz, a beep and, if you want, the
 * count said out loud. Speech uses the phone's own offline voice; nothing is sent anywhere.
 */
class RepFeedback(context: Context, private val speak: Boolean) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }
    private val tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }.getOrNull()
    private var ttsReady = false
    private val tts: TextToSpeech? = if (speak) {
        TextToSpeech(context.applicationContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) runCatching {
                tts?.language = Locale.getDefault()
                // A little slower and lower than default sounds calmer.
                tts?.setSpeechRate(0.9f)
                tts?.setPitch(0.92f)
                tts?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                // Wake the speech engine now, silently, so the first count isn't late.
                tts?.speak(" ", TextToSpeech.QUEUE_FLUSH, Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 0f) }, "warmup")
            }
        }
    } else {
        null
    }

    fun say(text: String) {
        if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, text)
    }

    fun rep(count: Int, target: Int) {
        buzz(40)
        if (speak && ttsReady) {
            say(if (count >= target) "$count. Well done." else "$count")
        } else {
            tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
        }
    }

    fun done() {
        buzz(300)
        tone?.startTone(ToneGenerator.TONE_PROP_ACK, 400)
    }

    private fun buzz(ms: Long) {
        runCatching { vibrator?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)) }
    }

    fun release() {
        tts?.shutdown()
        tone?.release()
    }
}
