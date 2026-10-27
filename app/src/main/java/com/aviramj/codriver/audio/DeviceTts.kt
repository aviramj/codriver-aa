package com.aviramj.codriver.audio

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * On-device text-to-speech. Kept warm as a process-wide singleton so the first
 * reply isn't delayed by engine startup. Audio is tagged as an assistant stream
 * so it plays over the car speakers and ducks navigation instead of fighting
 * media playback. Reached through the [Speaker] facade, not directly.
 */
internal object DeviceTts {
    private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    private val pendingOnInit = mutableListOf<() -> Unit>()

    @Synchronized
    fun init(context: Context, onInit: (() -> Unit)? = null) {
        if (ready) {
            onInit?.invoke()
            return
        }
        onInit?.let { pendingOnInit.add(it) }
        if (tts != null) return

        val app = context.applicationContext
        tts = TextToSpeech(app) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.language = Locale.getDefault()
                tts?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
            }
            val callbacks: List<() -> Unit>
            synchronized(this) {
                callbacks = pendingOnInit.toList()
                pendingOnInit.clear()
            }
            callbacks.forEach { it.invoke() }
        }
    }

    /** Initializes the engine if needed, then speaks once it's ready. */
    fun speakWhenReady(context: Context, text: String, onDone: (() -> Unit)? = null) {
        init(context) { speak(text, onDone) }
    }

    private fun speak(text: String, onDone: (() -> Unit)?) {
        val engine = tts
        if (engine == null || !ready || text.isBlank()) {
            onDone?.invoke()
            return
        }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { onDone?.invoke() }
            @Deprecated("Base class requires this overload")
            override fun onError(utteranceId: String?) { onDone?.invoke() }
            override fun onError(utteranceId: String?, errorCode: Int) { onDone?.invoke() }
        })
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "codriver-" + System.currentTimeMillis())
    }

    fun stop() {
        runCatching { tts?.stop() }
    }
}
