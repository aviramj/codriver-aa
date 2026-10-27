package com.aviramj.codriver.audio

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.car.app.CarContext
import androidx.car.app.media.CarAudioRecord
import java.io.ByteArrayOutputStream
import kotlin.math.sqrt

/**
 * Records a single spoken utterance from the car microphone and returns it as
 * WAV bytes.
 *
 * Android Auto only exposes the microphone through [CarAudioRecord] (not the
 * normal [android.speech.SpeechRecognizer]), so we capture raw PCM here and send
 * it to a cloud transcription endpoint. Recording stops automatically after a
 * short trailing silence, after a hard time cap, or when [requestStop] is called.
 */
class CarMicRecorder(private val carContext: CarContext) {

    @Volatile
    private var stopRequested = false

    fun requestStop() {
        stopRequested = true
    }

    /**
     * Blocking - call off the main thread.
     * @return WAV bytes, or null if audio focus was denied or nothing was captured.
     */
    fun record(maxMillis: Long = 15_000, trailingSilenceMillis: Long = 1_300): ByteArray? {
        stopRequested = false
        val record = CarAudioRecord.create(carContext)

        val attributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .build()

        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS) stopRequested = true
            }
            .build()

        val audioManager = carContext.getSystemService(AudioManager::class.java)
        if (audioManager == null ||
            audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        ) {
            return null
        }

        val pcm = ByteArrayOutputStream()
        try {
            record.startRecording()
            val buffer = ByteArray(CarAudioRecord.AUDIO_CONTENT_BUFFER_SIZE)
            val start = System.currentTimeMillis()
            var lastVoiceAt = start
            var heardVoice = false

            while (!stopRequested) {
                val read = record.read(buffer, 0, buffer.size)
                if (read < 0) break          // microphone dismissed / end of stream
                if (read == 0) continue
                pcm.write(buffer, 0, read)

                val now = System.currentTimeMillis()
                if (rms(buffer, read) > SILENCE_RMS) {
                    heardVoice = true
                    lastVoiceAt = now
                }
                if (heardVoice && now - lastVoiceAt > trailingSilenceMillis) break
                if (now - start > maxMillis) break
            }
        } finally {
            runCatching { record.stopRecording() }
            audioManager.abandonAudioFocusRequest(focusRequest)
        }

        val bytes = pcm.toByteArray()
        if (bytes.isEmpty()) return null
        return WavUtil.pcm16ToWav(bytes, SAMPLE_RATE)
    }

    /** Root-mean-square amplitude of the 16-bit little-endian samples in [buffer]. */
    private fun rms(buffer: ByteArray, length: Int): Double {
        var sum = 0.0
        var count = 0
        var i = 0
        while (i + 1 < length) {
            val sample = (buffer[i].toInt() and 0xff) or (buffer[i + 1].toInt() shl 8)
            sum += (sample * sample).toDouble()
            count++
            i += 2
        }
        return if (count == 0) 0.0 else sqrt(sum / count)
    }

    private companion object {
        // The car audio content format is fixed at 16-bit, 16 kHz, mono PCM.
        const val SAMPLE_RATE = 16_000
        const val SILENCE_RMS = 500.0
    }
}
