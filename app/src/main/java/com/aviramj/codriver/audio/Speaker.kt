package com.aviramj.codriver.audio

import android.content.Context
import com.aviramj.codriver.Settings

/**
 * Entry point for reading replies aloud. Dispatches to the on-device engine
 * ([DeviceTts]) or OpenAI's cloud voice ([OpenAiSpeaker]) based on the user's
 * setting. Cloud TTS falls back to the device engine if the audio can't be
 * fetched, so a reply is never silently lost to a config/network error.
 */
object Speaker {

    /** Warm up the device engine ahead of the first reply (no-op for cloud mode). */
    fun warmUp(context: Context) {
        if (!Settings.useOpenAiTts(context)) DeviceTts.init(context)
    }

    fun speak(context: Context, text: String, onDone: (() -> Unit)? = null) {
        if (Settings.useOpenAiTts(context)) {
            OpenAiSpeaker.speak(
                context,
                text,
                onFinished = { onDone?.invoke() },
                onFetchFailed = { DeviceTts.speakWhenReady(context, text, onDone) },
            )
        } else {
            DeviceTts.speakWhenReady(context, text, onDone)
        }
    }

    fun stop() {
        DeviceTts.stop()
        OpenAiSpeaker.stop()
    }
}
