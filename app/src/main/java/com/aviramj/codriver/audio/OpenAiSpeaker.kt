package com.aviramj.codriver.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaDataSource
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import com.aviramj.codriver.Settings
import com.aviramj.codriver.ai.OpenAiClient
import java.util.concurrent.Executors

/**
 * Cloud text-to-speech via OpenAI's /audio/speech endpoint. Fetches the audio on
 * a background thread, then plays it through a [MediaPlayer] routed as an
 * assistant stream (so it ducks navigation and plays over the car speakers).
 *
 * Reached through the [Speaker] facade, which falls back to [DeviceTts] when
 * [onFetchFailed] fires (bad key/model/voice, or no network).
 */
internal object OpenAiSpeaker {
    private val net = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private var player: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private var audioManager: AudioManager? = null

    /** Bumped on every speak/stop so a slow fetch can't play after it's superseded. */
    @Volatile private var generation = 0

    /**
     * @param onFinished runs when playback completes or a playback error ends it.
     * @param onFetchFailed runs when the audio couldn't be fetched, so the caller
     *        can fall back to another engine.
     */
    fun speak(context: Context, text: String, onFinished: () -> Unit, onFetchFailed: () -> Unit) {
        val app = context.applicationContext
        stop() // cancel anything currently playing (also bumps generation)
        val myGen = ++generation
        if (text.isBlank()) {
            onFinished()
            return
        }

        net.execute {
            val audio = runCatching {
                OpenAiClient(Settings.baseUrl(app), Settings.apiKey(app))
                    .speech(Settings.ttsModel(app), Settings.ttsVoice(app), text)
            }.getOrNull()

            if (audio == null || audio.isEmpty()) {
                onFetchFailed()
                return@execute
            }
            main.post {
                if (myGen != generation) onFinished() else play(app, audio, onFinished)
            }
        }
    }

    private fun play(app: Context, audio: ByteArray, onFinished: () -> Unit) {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val am = app.getSystemService(AudioManager::class.java)
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .build()
        audioManager = am
        focusRequest = request
        am?.requestAudioFocus(request)

        val mp = MediaPlayer()
        player = mp

        var finished = false
        val finish = {
            if (!finished) {
                finished = true
                runCatching { am?.abandonAudioFocusRequest(request) }
                release(mp)
                onFinished()
            }
        }

        mp.setAudioAttributes(attributes)
        mp.setOnPreparedListener { it.start() }
        mp.setOnCompletionListener { finish() }
        mp.setOnErrorListener { _, _, _ -> finish(); true }
        runCatching {
            mp.setDataSource(ByteArrayMediaDataSource(audio))
            mp.prepareAsync()
        }.onFailure { finish() }
    }

    /** Stops any current playback and releases audio focus. */
    fun stop() {
        generation++
        main.post {
            player?.let { release(it) }
            focusRequest?.let { req -> runCatching { audioManager?.abandonAudioFocusRequest(req) } }
            focusRequest = null
        }
    }

    private fun release(mp: MediaPlayer) {
        runCatching { if (mp.isPlaying) mp.stop() }
        runCatching { mp.reset() }
        runCatching { mp.release() }
        if (player === mp) player = null
    }

    /** Plays MP3 bytes straight from memory, no temp file. */
    private class ByteArrayMediaDataSource(private val data: ByteArray) : MediaDataSource() {
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position >= data.size) return -1
            val count = minOf(size.toLong(), data.size - position).toInt()
            System.arraycopy(data, position.toInt(), buffer, offset, count)
            return count
        }

        override fun getSize(): Long = data.size.toLong()
        override fun close() {}
    }
}
