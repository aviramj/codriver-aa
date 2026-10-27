package com.aviramj.codriver.ai

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Minimal client for Anthropic's Messages API, used when a Claude model is
 * selected for chat. (Speech-to-text and text-to-speech still go to OpenAI —
 * Anthropic offers neither.)
 *
 * Blocking; call off the main thread.
 */
class AnthropicClient(private val apiKey: String) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    @Throws(IOException::class)
    fun chat(systemPrompt: String, model: String, history: List<ChatMessage>): String {
        val messages = JSONArray()
        history.forEach {
            messages.put(JSONObject().put("role", it.role).put("content", it.content))
        }

        val payload = JSONObject()
            .put("model", model)
            .put("max_tokens", 4096)
            .put("messages", messages)
        if (systemPrompt.isNotBlank()) payload.put("system", systemPrompt)

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .post(payload.toString().toRequestBody(JSON))
            .build()

        http.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("Claude request failed (HTTP ${resp.code}): ${body.take(300)}")

            val json = JSONObject(body)
            val content = json.optJSONArray("content")
                ?: throw IOException("Unexpected Claude response: ${body.take(200)}")

            // The reply is the concatenation of the text blocks (thinking blocks,
            // if any, are separate and skipped).
            val sb = StringBuilder()
            for (i in 0 until content.length()) {
                val block = content.getJSONObject(i)
                if (block.optString("type") == "text") sb.append(block.optString("text"))
            }
            val text = sb.toString().trim()
            if (text.isNotEmpty()) return text

            return when (json.optString("stop_reason")) {
                "refusal" -> "Sorry, I can't help with that one."
                "max_tokens" -> "That answer got too long for me to finish — try asking for a shorter version."
                else -> throw IOException("Claude returned no text (stop_reason=${json.optString("stop_reason")}).")
            }
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
