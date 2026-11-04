package com.shiyu.chat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalRollingSummaryTest {
    @Test
    fun selectsOnlyUnsummarizedMessagesOutsideRecentWindow() {
        val messages = (1..30).map { message(it, if (it % 2 == 0) "assistant" else "user") }

        val selected = selectLocalSummaryMessages(messages, watermarkMessageId = 2)

        assertEquals(listOf(3L, 4L, 5L, 6L), selected.map { it.id })
    }

    @Test
    fun waitsUntilHistoryExceedsRecentWindow() {
        val messages = (1..24).map { message(it, "user") }

        assertTrue(selectLocalSummaryMessages(messages, watermarkMessageId = 0).isEmpty())
    }

    @Test
    fun transcriptRemovesActionsAndCapsLongMessages() {
        val transcript = buildLocalSummaryTranscript(
            listOf(
                message(1, "user", "我明天要去面试产品岗位。" + "细节".repeat(200)),
                message(2, "assistant", "（认真点头）我会记得问你结果。"),
            ),
            characterName = "宋知意",
        )

        assertTrue(transcript.startsWith("用户：我明天要去面试产品岗位。"))
        assertTrue(transcript.contains("宋知意：我会记得问你结果。"))
        assertFalse(transcript.contains("认真点头"))
        assertTrue(transcript.lines().all { it.length <= 244 })
    }

    @Test
    fun generatedSummaryIsShortAndRemovesModelDecorations() {
        val summary = sanitizeGeneratedLocalSummary(
            """
            以下是摘要：
            - 用户明天参加产品岗位面试。
            - 宋知意答应晚上询问面试结果。
            - 用户明天参加产品岗位面试。
            """.trimIndent(),
            characterName = "宋知意",
        )

        assertEquals(
            "- 用户明天参加产品岗位面试。\n- 宋知意答应晚上询问面试结果。",
            summary,
        )
        assertTrue(summary.length < 900)
    }

    @Test
    fun rejectsRawDialogueAndCapsLongSummaryLines() {
        val longFact = "用户希望参加一个需要准备作品集并且包含现场问答与团队讨论的产品岗位面试"
        val summary = sanitizeGeneratedLocalSummary(
            """
            以下是摘要：
            用户：我明天下午要去参加产品岗位面试。
            宋知意：那晚上我问你结果。
            $longFact
            """.trimIndent(),
            characterName = "宋知意",
        )

        assertFalse(summary.contains("用户："))
        assertFalse(summary.contains("宋知意："))
        assertTrue(summary.startsWith("- "))
        assertTrue(summary.lines().all { it.length <= 72 })
    }

    private fun message(id: Int, role: String) = MessageEntity(
        id = id.toLong(),
        characterId = "song",
        role = role,
        content = "第${id}条消息",
        createdAt = id.toLong(),
    )

    private fun message(id: Int, role: String, content: String) = MessageEntity(
        id = id.toLong(),
        characterId = "song",
        role = role,
        content = content,
        createdAt = id.toLong(),
    )
}
