package com.aviramj.codriver.car

import android.Manifest
import android.content.pm.PackageManager
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.Header
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.aviramj.codriver.Settings
import com.aviramj.codriver.ai.AnthropicClient
import com.aviramj.codriver.ai.ConversationManager
import com.aviramj.codriver.ai.Models
import com.aviramj.codriver.ai.OpenAiClient
import com.aviramj.codriver.audio.CarMicRecorder
import com.aviramj.codriver.audio.Speaker
import java.util.concurrent.Executors

/**
 * The single Android Auto screen. It drives one user-initiated turn at a time:
 *
 *   IDLE -> (tap Speak) -> LISTENING -> THINKING -> SPEAKING -> IDLE
 *
 * Speech-to-text always goes to OpenAI Whisper; chat routes to OpenAI or
 * Anthropic based on the selected model. Staying on one screen and calling
 * [invalidate] keeps us within the host's template-refresh limits, and the
 * conversation history in [ConversationManager] lets follow-ups build on the
 * previous answer.
 */
class ConversationScreen(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver {

    private enum class State { IDLE, LISTENING, THINKING, SPEAKING }

    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var recorder: CarMicRecorder? = null

    private var state = State.IDLE
    private var statusLine = ""   // transient status/error text shown instead of the last answer

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        Speaker.warmUp(carContext)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        recorder?.requestStop()
        Speaker.stop()
        worker.shutdownNow()
    }

    override fun onGetTemplate(): Template {
        if (!Settings.isConfigured(carContext)) {
            return MessageTemplate.Builder(
                "Open CoDriver AI on your phone and add your OpenAI API key, then come back here.",
            )
                .setHeader(header())
                .build()
        }

        val builder = MessageTemplate.Builder(bodyText())
            .setHeader(header())

        when (state) {
            State.IDLE -> {
                builder.addAction(
                    Action.Builder()
                        .setTitle(if (ConversationManager.isEmpty()) "Speak" else "Ask follow-up")
                        .setBackgroundColor(CarColor.BLUE)
                        .setOnClickListener { onSpeakTapped() }
                        .build(),
                )
                if (!ConversationManager.isEmpty()) {
                    builder.addAction(
                        Action.Builder()
                            .setTitle("New chat")
                            .setOnClickListener { onNewChat() }
                            .build(),
                    )
                }
            }

            State.LISTENING -> builder.addAction(
                Action.Builder()
                    .setTitle("Stop")
                    .setOnClickListener { recorder?.requestStop() }
                    .build(),
            )

            State.THINKING -> {
                // No actions while the request is in flight.
            }

            State.SPEAKING -> {
                builder.addAction(
                    Action.Builder()
                        .setTitle("Stop")
                        .setOnClickListener { onStopSpeaking() }
                        .build(),
                )
                builder.addAction(
                    Action.Builder()
                        .setTitle("Ask follow-up")
                        .setOnClickListener { onStopSpeaking(); onSpeakTapped() }
                        .build(),
                )
            }
        }
        return builder.build()
    }

    private fun header(): Header =
        Header.Builder()
            .setTitle(TITLE)
            .setStartHeaderAction(Action.APP_ICON)
            .build()

    private fun bodyText(): String {
        if (statusLine.isNotEmpty()) return statusLine
        return ConversationManager.lastAssistant()?.let(::truncate)
            ?: "Tap Speak and ask me anything. I'll keep answers short so they're easy to follow while you drive."
    }

    private fun onSpeakTapped() {
        if (state != State.IDLE && state != State.SPEAKING) return
        if (ContextCompat.checkSelfPermission(carContext, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestMicPermission()
            return
        }
        beginTurn()
    }

    private fun beginTurn() {
        setState(State.LISTENING, "Listening… speak now.")
        worker.execute {
            val mic = CarMicRecorder(carContext).also { recorder = it }
            val wav = runCatching { mic.record() }.getOrNull()
            recorder = null
            if (wav == null) {
                finish(State.IDLE, "I couldn't reach the microphone. Make sure it's allowed and try again.")
                return@execute
            }

            setState(State.THINKING, "Thinking…")
            runCatching {
                // Speech-to-text always uses OpenAI Whisper.
                val openAi = OpenAiClient(Settings.baseUrl(carContext), Settings.apiKey(carContext))
                val heard = openAi.transcribe(Settings.transcribeModel(carContext), wav)
                if (heard.isBlank()) {
                    finish(State.IDLE, "I didn't catch that. Tap Speak to try again.")
                    return@execute
                }
                ConversationManager.addUser(heard)

                val model = Settings.chatModel(carContext)
                val system = Settings.systemPrompt(carContext)
                val reply = if (Models.isAnthropic(model)) {
                    val anthropicKey = Settings.anthropicApiKey(carContext)
                    if (anthropicKey.isBlank()) {
                        finish(State.IDLE, "Add your Anthropic API key in the phone app to use Claude models, or pick an OpenAI model.")
                        return@execute
                    }
                    AnthropicClient(anthropicKey).chat(system, model, ConversationManager.history())
                } else {
                    openAi.chat(system, model, ConversationManager.history())
                }
                ConversationManager.addAssistant(reply)
                speak(reply)
            }.onFailure { finish(State.IDLE, shortError(it)) }
        }
    }

    private fun speak(reply: String) {
        setState(State.SPEAKING, "")
        Speaker.speak(carContext, reply) {
            carContext.mainExecutor.execute {
                if (state == State.SPEAKING) {
                    state = State.IDLE
                    statusLine = ""
                    invalidate()
                }
            }
        }
    }

    private fun onStopSpeaking() {
        Speaker.stop()
        setState(State.IDLE, "")
    }

    private fun onNewChat() {
        ConversationManager.reset()
        setState(State.IDLE, "")
    }

    private fun requestMicPermission() {
        setState(State.IDLE, "Grant microphone access on your phone, then tap Speak again.")
        runCatching {
            carContext.requestPermissions(listOf(Manifest.permission.RECORD_AUDIO)) { granted, _ ->
                if (granted.contains(Manifest.permission.RECORD_AUDIO)) {
                    beginTurn()
                } else {
                    setState(State.IDLE, "Microphone access is required to talk to the AI.")
                }
            }
        }.onFailure {
            CarToast.makeText(
                carContext,
                "Enable the microphone permission in the CoDriver AI phone app.",
                CarToast.LENGTH_LONG,
            ).show()
        }
    }

    private fun setState(newState: State, status: String) {
        carContext.mainExecutor.execute {
            state = newState
            statusLine = status
            invalidate()
        }
    }

    private fun finish(newState: State, status: String) = setState(newState, status)

    private fun truncate(s: String, max: Int = 480): String =
        if (s.length <= max) s else s.substring(0, max).trimEnd() + "…"

    private fun shortError(t: Throwable): String =
        "Something went wrong: " + truncate(t.message ?: t.javaClass.simpleName, 200)

    private companion object {
        const val TITLE = "CoDriver AI"
    }
}
