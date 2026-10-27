package com.aviramj.codriver.ai

/** A single chat turn. [role] is one of "system", "user", or "assistant". */
data class ChatMessage(val role: String, val content: String)

/**
 * Holds the running conversation so follow-ups keep their context. Process-wide
 * singleton: the Android Auto screen lives in the same process as the service,
 * so a single in-memory history is enough. Old turns are trimmed to keep the
 * request (and token cost) bounded.
 */
object ConversationManager {
    /** Maximum number of user/assistant messages kept (the system prompt is added separately). */
    private const val MAX_MESSAGES = 20

    private val messages = mutableListOf<ChatMessage>()

    @Synchronized
    fun reset() = messages.clear()

    @Synchronized
    fun addUser(text: String) {
        messages.add(ChatMessage("user", text))
        trim()
    }

    @Synchronized
    fun addAssistant(text: String) {
        messages.add(ChatMessage("assistant", text))
        trim()
    }

    @Synchronized
    fun history(): List<ChatMessage> = messages.toList()

    @Synchronized
    fun lastAssistant(): String? = messages.lastOrNull { it.role == "assistant" }?.content

    @Synchronized
    fun isEmpty(): Boolean = messages.isEmpty()

    private fun trim() {
        while (messages.size > MAX_MESSAGES) messages.removeAt(0)
    }
}
