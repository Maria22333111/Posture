package com.example.posture

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import java.util.Locale

class Notifier(context: Context) : TextToSpeech.OnInitListener {

    private val vibrator = context.getSystemService(Vibrator::class.java)
    private val tts = TextToSpeech(context, this)
    private var ready = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("ru", "RU")
            ready = true
        }
    }

    fun alert(text: String) {
        vibrator?.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
        if (ready) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "posture")
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}