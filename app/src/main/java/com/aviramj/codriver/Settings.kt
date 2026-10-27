package com.aviramj.codriver

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * User configuration, shared by the phone activity and the Android Auto screen.
 *
 * Keys are stored in [EncryptedSharedPreferences] when the device keystore
 * cooperates, with a graceful fallback to plain prefs on the handful of OEM
 * builds where the keystore throws. When nothing is saved yet, keys default to
 * the values baked in at build time from secrets.properties (see BuildConfig).
 *
 * Speech-to-text (Whisper) and cloud TTS always use the OpenAI key + base URL.
 * Chat can use OpenAI or Anthropic depending on the selected model.
 */
object Settings {
    private const val FILE = "codriver_settings"

    const val KEY_BASE_URL = "base_url"
    const val KEY_API_KEY = "api_key"
    const val KEY_ANTHROPIC_API_KEY = "anthropic_api_key"
    const val KEY_CHAT_MODEL = "chat_model"
    const val KEY_TRANSCRIBE_MODEL = "transcribe_model"
    const val KEY_SYSTEM_PROMPT = "system_prompt"
    const val KEY_TTS_OPENAI = "tts_openai"
    const val KEY_TTS_MODEL = "tts_model"
    const val KEY_TTS_VOICE = "tts_voice"

    const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
    const val DEFAULT_CHAT_MODEL = "gpt-6-luna"
    const val DEFAULT_TRANSCRIBE_MODEL = "whisper-1"
    const val DEFAULT_TTS_MODEL = "gpt-4o-mini-tts"
    const val DEFAULT_TTS_VOICE = "alloy"
    val DEFAULT_SYSTEM_PROMPT = """
        You are a hands-free voice assistant for someone who is driving.
        Keep answers short, clear, and easy to follow by ear - usually a sentence
        or two, unless more detail is explicitly requested. Never use markdown,
        bullet lists, code blocks, emoji, or special formatting; reply in plain
        spoken prose. If a request is ambiguous, make a reasonable assumption and
        say so briefly rather than asking a clarifying question.
    """.trimIndent()

    private fun prefs(context: Context): SharedPreferences {
        val app = context.applicationContext
        return try {
            val masterKey = MasterKey.Builder(app)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                app,
                FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (t: Throwable) {
            app.getSharedPreferences(FILE + "_plain", Context.MODE_PRIVATE)
        }
    }

    fun baseUrl(c: Context): String =
        prefs(c).getString(KEY_BASE_URL, DEFAULT_BASE_URL)!!.trim().ifEmpty { DEFAULT_BASE_URL }

    /** OpenAI key — used for speech-to-text, OpenAI chat models, and OpenAI TTS. */
    fun apiKey(c: Context): String =
        prefs(c).getString(KEY_API_KEY, "")!!.trim().ifEmpty { BuildConfig.DEFAULT_OPENAI_API_KEY.trim() }

    /** Anthropic key — used only when a Claude model is selected for chat. */
    fun anthropicApiKey(c: Context): String =
        prefs(c).getString(KEY_ANTHROPIC_API_KEY, "")!!.trim().ifEmpty { BuildConfig.DEFAULT_ANTHROPIC_API_KEY.trim() }

    fun chatModel(c: Context): String =
        prefs(c).getString(KEY_CHAT_MODEL, DEFAULT_CHAT_MODEL)!!.trim().ifEmpty { DEFAULT_CHAT_MODEL }

    fun transcribeModel(c: Context): String =
        prefs(c).getString(KEY_TRANSCRIBE_MODEL, DEFAULT_TRANSCRIBE_MODEL)!!.trim()
            .ifEmpty { DEFAULT_TRANSCRIBE_MODEL }

    fun systemPrompt(c: Context): String =
        prefs(c).getString(KEY_SYSTEM_PROMPT, DEFAULT_SYSTEM_PROMPT)!!.ifEmpty { DEFAULT_SYSTEM_PROMPT }

    fun useOpenAiTts(c: Context): Boolean = prefs(c).getBoolean(KEY_TTS_OPENAI, false)

    fun ttsModel(c: Context): String =
        prefs(c).getString(KEY_TTS_MODEL, DEFAULT_TTS_MODEL)!!.trim().ifEmpty { DEFAULT_TTS_MODEL }

    fun ttsVoice(c: Context): String =
        prefs(c).getString(KEY_TTS_VOICE, DEFAULT_TTS_VOICE)!!.trim().ifEmpty { DEFAULT_TTS_VOICE }

    fun save(
        c: Context,
        baseUrl: String,
        apiKey: String,
        anthropicApiKey: String,
        chatModel: String,
        transcribeModel: String,
        systemPrompt: String,
        useOpenAiTts: Boolean,
        ttsModel: String,
        ttsVoice: String,
    ) {
        prefs(c).edit()
            .putString(KEY_BASE_URL, baseUrl.trim())
            .putString(KEY_API_KEY, apiKey.trim())
            .putString(KEY_ANTHROPIC_API_KEY, anthropicApiKey.trim())
            .putString(KEY_CHAT_MODEL, chatModel.trim())
            .putString(KEY_TRANSCRIBE_MODEL, transcribeModel.trim())
            .putString(KEY_SYSTEM_PROMPT, systemPrompt)
            .putBoolean(KEY_TTS_OPENAI, useOpenAiTts)
            .putString(KEY_TTS_MODEL, ttsModel.trim())
            .putString(KEY_TTS_VOICE, ttsVoice.trim())
            .apply()
    }

    /** True once an OpenAI key exists (required for voice input regardless of chat model). */
    fun isConfigured(c: Context): Boolean = apiKey(c).isNotEmpty()
}
