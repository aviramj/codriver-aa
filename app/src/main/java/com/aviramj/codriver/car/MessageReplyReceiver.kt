package com.aviramj.codriver.car

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.aviramj.codriver.Settings
import com.aviramj.codriver.ai.AnthropicClient
import com.aviramj.codriver.ai.Models
import com.aviramj.codriver.ai.OpenAiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives spoken replies from Android Auto's messaging UI, forwards them to the
 * configured AI model, and posts the answer back as the next message (which the
 * car reads aloud). The network call runs off the main thread via [goAsync].
 */
class MessageReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        when (intent.action) {
            // Present only because Android Auto expects a mark-as-read action to exist.
            CarMessaging.ACTION_MARK_READ -> Unit

            CarMessaging.ACTION_REPLY -> {
                val spoken = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(CarMessaging.KEY_TEXT_REPLY)?.toString()?.trim()
                if (spoken.isNullOrEmpty()) return

                CarMessaging.addUserMessage(app, spoken)

                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    val answer = runCatching { askAi(app) }
                        .getOrElse { "Sorry, I hit a problem: ${it.message ?: "unknown error"}." }
                    try {
                        CarMessaging.postAssistantMessage(app, answer)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    /** Routes to the configured chat provider, reusing the same clients as the phone app. */
    private fun askAi(c: Context): String {
        val history = CarMessaging.aiHistory(c)
        if (history.isEmpty()) return "I didn't catch a question - tap reply and ask again."
        val model = Settings.chatModel(c)
        val system = Settings.systemPrompt(c)
        return if (Models.isAnthropic(model)) {
            val key = Settings.anthropicApiKey(c)
            if (key.isBlank()) "Add your Anthropic API key in the co:driver app to use Claude models."
            else AnthropicClient(key).chat(system, model, history)
        } else {
            val key = Settings.apiKey(c)
            if (key.isBlank()) "Add your OpenAI API key in the co:driver app first."
            else OpenAiClient(Settings.baseUrl(c), key).chat(system, model, history)
        }
    }
}
