package com.shiyu.chat.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationWindowPolicyTest {
    @Test
    fun promptWindowStartsAtAUserTurnAndKeepsTheLatestTurns() {
        val messages = buildList {
            add(message(1, "assistant", "开场"))
            for (turn in 0 until 20) {
                add(message(turn * 2L + 2, "user", "用户 $turn"))
                add(message(turn * 2L + 3, "assistant", "角色 $turn"))
            }
        }

        val selected = ConversationWindowPolicy.recentCompleteTurns(
            messages,
            maxTurns = 3,
            maxMessages = 40,
        )

        assertEquals("user", selected.first().role)
        assertEquals("用户 17", selected.first().content)
        assertEquals(6, selected.size)
        assertEquals("角色 19", selected.last().content)
    }

    @Test
    fun openingMessageIsKeptWhenNoUserTurnExists() {
        val messages = listOf(message(1, "assistant", "开场"))

        assertEquals(
            messages,
            ConversationWindowPolicy.recentCompleteTurns(messages),
        )
    }

    private fun message(id: Long, role: String, content: String) = MessageEntity(
        id = id,
        characterId = "test",
        role = role,
        content = content,
    )
}
