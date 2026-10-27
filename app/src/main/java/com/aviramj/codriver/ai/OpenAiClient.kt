package com.aviramj.codriver.ai

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Minimal OpenAI-compatible client: chat completions + audio transcription.
 * Works against api.openai.com and any compatible endpoint (Azure OpenAI,
 * OpenRouter, a local llama.cpp/Ollama server, ...) by changing [baseUrl].
 *
 * All calls are blocking and must run off the main thread.
 */
class OpenAiClient(
    baseUrl: String,
    private val apiKey: String,
) {
    private val base = baseUrl.trimEnd('/')

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun url(path: String) = "$base/$path"

    /** Sends the system prompt + conversation and returns the assistant's reply text. */
    @Throws(IOException::class)
    fun chat(systemPrompt: String, model: String, history: List<ChatMessage>): String {
        val messages = JSONArray()
        if (systemPrompt.isNotBlank()) {
            messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
        }
        history.forEach {
            messages.put(JSONObject().put("role", it.role).put("content", it.content))
        }

        val payload = JSONObject()
            .put("model", model)
            .put("messages", messages)

        val request = Request.Builder()
            .url(url("chat/completions"))
            .addHeader("Authorization", "Bearer $apiKey")
            .post(payload.toString().toRequestBody(JSON))
            .build()

        http.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("Chat request failed (HTTP ${resp.code}): ${body.take(300)}")
            val choices = JSONObject(body).optJSONArray("choices")
                ?: throw IOException("Unexpected chat response: ${body.take(200)}")
            if (choices.length() == 0) throw IOException("The model returned no choices.")
            return choices.getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
        }
    }

    /**
     * Transcribes recorded speech. [wav] must be a WAV container (the car
     * microphone produces 16-bit, 16 kHz, mono PCM). Returns the recognized text.
     */
    @Throws(IOException::class)
    fun transcribe(model: String, wav: ByteArray): String {
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", "speech.wav", wav.toRequestBody(WAV))
            .addFormDataPart("model", model)
            .addFormDataPart("response_format", "json")
            .build()

        val request = Request.Builder()
            .url(url("audio/transcriptions"))
            .addHeader("Authorization", "Bearer $apiKey")
            .post(multipart)
            .build()

        http.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("Transcription failed (HTTP ${resp.code}): ${body.take(300)}")
            return JSONObject(body).optString("text").trim()
        }
    }

    /**
     * Synthesizes [input] to speech and returns the raw audio bytes (MP3 by
     * default). [model] is e.g. "gpt-4o-mini-tts" or "tts-1"; [voice] is e.g.
     * "alloy", "nova", "shimmer".
     */
    @Throws(IOException::class)
    fun speech(model: String, voice: String, input: String, format: String = "mp3"): ByteArray {
        val payload = JSONObject()
            .put("model", model)
            .put("voice", voice)
            .put("input", input)
            .put("response_format", format)

        val request = Request.Builder()
            .url(url("audio/speech"))
            .addHeader("Authorization", "Bearer $apiKey")
            .post(payload.toString().toRequestBody(JSON))
            .build()

        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                val err = resp.body?.string().orEmpty()
                throw IOException("Speech request failed (HTTP ${resp.code}): ${err.take(300)}")
            }
            return resp.body?.bytes() ?: throw IOException("Empty audio response")
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        val WAV = "audio/wav".toMediaType()
    }
}
