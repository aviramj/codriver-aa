package com.aviramj.codriver.audio

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Wraps raw PCM samples in a WAV container so they can be uploaded for transcription. */
object WavUtil {

    /** @param pcm little-endian 16-bit mono samples. */
    fun pcm16ToWav(pcm: ByteArray, sampleRate: Int, channels: Int = 1): ByteArray {
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val out = ByteArrayOutputStream(44 + pcm.size)

        fun str(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
        fun int32(v: Int) =
            out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array())
        fun int16(v: Int) =
            out.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v.toShort()).array())

        str("RIFF")
        int32(36 + pcm.size)     // file size - 8
        str("WAVE")
        str("fmt ")
        int32(16)                // fmt chunk size (PCM)
        int16(1)                 // audio format = PCM
        int16(channels)
        int32(sampleRate)
        int32(byteRate)
        int16(blockAlign)
        int16(bitsPerSample)
        str("data")
        int32(pcm.size)
        out.write(pcm)
        return out.toByteArray()
    }
}
