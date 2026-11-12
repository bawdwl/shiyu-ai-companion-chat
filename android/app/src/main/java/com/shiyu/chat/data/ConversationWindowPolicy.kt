package com.shiyu.chat.data

internal object ConversationWindowPolicy {
    const val INITIAL_MESSAGE_LIMIT = 80
    const val PAGE_SIZE = 60
    const val PROMPT_MESSAGE_LIMIT = 40
    const val PROMPT_TURN_LIMIT = 12

    fun recentCompleteTurns(
        messages: List<MessageEntity>,
        maxTurns: Int = PROMPT_TURN_LIMIT,
        maxMessages: Int = PROMPT_MESSAGE_LIMIT,
    ): List<MessageEntity> {
        if (messages.isEmpty()) return emptyList()
        val userTurnStarts = messages
            .withIndex()
            .filter { it.value.role == "user" }
            .takeLast(maxTurns.coerceAtLeast(1))
        val start = userTurnStarts.firstOrNull()?.index ?: 0
        return messages
            .drop(start)
            .takeLast(maxMessages.coerceAtLeast(1))
    }
}
