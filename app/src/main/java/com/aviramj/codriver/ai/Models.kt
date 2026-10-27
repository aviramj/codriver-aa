package com.aviramj.codriver.ai

/** Which API a chat model is served by. */
enum class Provider { OPENAI, ANTHROPIC }

/** A selectable chat model: display [label], the API [id], and its [provider]. */
data class ChatModelOption(val label: String, val id: String, val provider: Provider)

/**
 * The models offered in the phone app's dropdown. OpenAI's lineup churns fast, so
 * these are editable in the UI (a "Custom…" entry lets you type any id) and the
 * provider is derived from the id at call time — a Claude id routes to Anthropic,
 * anything else to the OpenAI-compatible endpoint.
 */
object Models {
    val PRESETS: List<ChatModelOption> = listOf(
        ChatModelOption("GPT-6 Astra — OpenAI", "gpt-6-astra", Provider.OPENAI),
        ChatModelOption("GPT-6.1 Terra — OpenAI", "gpt-6.1-terra", Provider.OPENAI),
        ChatModelOption("GPT-6 Luna — OpenAI", "gpt-6-luna", Provider.OPENAI),
        ChatModelOption("Claude Opus 5.5 — Anthropic", "claude-opus-5-5", Provider.ANTHROPIC),
        ChatModelOption("Claude Sonnet 5.5 — Anthropic", "claude-sonnet-5-5", Provider.ANTHROPIC),
        ChatModelOption("Claude Haiku 5.5 — Anthropic", "claude-haiku-5-5", Provider.ANTHROPIC),
    )

    fun providerFor(model: String): Provider =
        if (model.trim().startsWith("claude", ignoreCase = true)) Provider.ANTHROPIC else Provider.OPENAI

    fun isAnthropic(model: String): Boolean = providerFor(model) == Provider.ANTHROPIC

    fun indexOfId(model: String): Int = PRESETS.indexOfFirst { it.id.equals(model.trim(), ignoreCase = true) }
}
