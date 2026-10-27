package com.aviramj.codriver

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.aviramj.codriver.ai.AnthropicClient
import com.aviramj.codriver.ai.ConversationManager
import com.aviramj.codriver.ai.Models
import com.aviramj.codriver.ai.OpenAiClient
import com.aviramj.codriver.audio.Speaker
import com.aviramj.codriver.databinding.ActivityDrivingBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Full-screen, hands-light assistant for using on a phone mount while driving.
 * One big button: tap → speak → hear the answer → tap again for a follow-up.
 *
 * On the phone (unlike the Android Auto surface) we can use the platform
 * [SpeechRecognizer] directly for voice input. Chat + text-to-speech reuse the
 * same providers as the rest of the app.
 */
class DrivingModeActivity : AppCompatActivity() {

    private enum class State { IDLE, LISTENING, THINKING, SPEAKING }

    private lateinit var binding: ActivityDrivingBinding
    private var recognizer: SpeechRecognizer? = null
    private var state = State.IDLE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDrivingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        Speaker.warmUp(this)

        binding.talkButton.setOnClickListener { onTalkTapped() }
        binding.newChat.setOnClickListener {
            ConversationManager.reset()
            Speaker.stop()
            setIdle("Tap the button and ask me anything.")
        }
        binding.settings.setOnClickListener { startActivity(Intent(this, MainActivity::class.java)) }

        render()
    }

    override fun onResume() {
        super.onResume()
        if (!Settings.isConfigured(this)) {
            binding.status.text = "Add your API key in Settings, then come back."
        }
    }

    private fun onTalkTapped() {
        if (!Settings.isConfigured(this)) {
            binding.status.text = "Add your API key in Settings, then come back."
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
            return
        }
        when (state) {
            State.IDLE -> startListening()
            State.SPEAKING -> { Speaker.stop(); startListening() }
            State.LISTENING -> recognizer?.stopListening()
            State.THINKING -> {} // ignore taps while the request is in flight
        }
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            setIdle("Speech recognition isn't available on this device.")
            return
        }
        state = State.LISTENING
        binding.status.text = "Listening…"
        render()

        recognizer?.destroy()
        val rec = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = rec
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { binding.status.text = it }
            }

            override fun onError(error: Int) {
                setIdle("Didn't catch that — tap and try again.")
            }

            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (text.isNullOrBlank()) setIdle("Didn't catch that — tap and try again.")
                else handleTranscript(text)
            }
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        }
        rec.startListening(intent)
    }

    private fun handleTranscript(userText: String) {
        state = State.THINKING
        binding.status.text = userText
        render()
        ConversationManager.addUser(userText)

        lifecycleScope.launch {
            val reply = withContext(Dispatchers.IO) {
                runCatching {
                    val model = Settings.chatModel(this@DrivingModeActivity)
                    val system = Settings.systemPrompt(this@DrivingModeActivity)
                    if (Models.isAnthropic(model)) {
                        val key = Settings.anthropicApiKey(this@DrivingModeActivity)
                        if (key.isBlank()) return@runCatching "ERROR: add your Anthropic API key in Settings to use Claude models."
                        AnthropicClient(key).chat(system, model, ConversationManager.history())
                    } else {
                        OpenAiClient(Settings.baseUrl(this@DrivingModeActivity), Settings.apiKey(this@DrivingModeActivity))
                            .chat(system, model, ConversationManager.history())
                    }
                }.getOrElse { "ERROR: ${it.message ?: it}" }
            }

            if (reply.startsWith("ERROR")) {
                setIdle(reply.removePrefix("ERROR: "))
                return@launch
            }
            ConversationManager.addAssistant(reply)
            state = State.SPEAKING
            binding.status.text = reply
            render()
            Speaker.speak(this@DrivingModeActivity, reply) {
                runOnUiThread { if (state == State.SPEAKING) setIdle(reply) }
            }
        }
    }

    private fun setIdle(message: String) {
        runOnUiThread {
            state = State.IDLE
            binding.status.text = message
            render()
        }
    }

    private fun render() {
        binding.talkLabel.text = when (state) {
            State.IDLE -> if (ConversationManager.isEmpty()) "Tap to talk" else "Tap to ask a follow-up"
            State.LISTENING -> "Listening… tap to stop"
            State.THINKING -> "Thinking…"
            State.SPEAKING -> "Tap to interrupt and ask"
        }
        binding.talkButton.isEnabled = state != State.THINKING
        binding.talkButton.alpha = if (state == State.THINKING) 0.5f else 1f
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_MIC && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startListening()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        recognizer?.destroy()
        Speaker.stop()
    }

    private companion object {
        const val REQ_MIC = 7
    }
}
