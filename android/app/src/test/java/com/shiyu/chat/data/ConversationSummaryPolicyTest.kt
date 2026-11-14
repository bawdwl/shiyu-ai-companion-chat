package com.shiyu.chat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationSummaryPolicyTest {
    private fun message(id: Long, role: String = "user", text: String = "消息$id") =
        MessageEntity(
            id = id,
            characterId = "a",
            role = role,
            content = text,
        )

    @Test
    fun selectsOnlyOlderUncoveredMessagesAndLeavesRecentWindowAlone() {
        val messages = (1L..50L).map { message(it) }
        val selected = selectCloudSummaryMessages(
            messages = messages,
            coveredThroughMessageId = 8,
            recentMessageLimit = 20,
            maxMessages = 12,
            minMessages = 4,
        )

        assertEquals(9L, selected.first().id)
        assertEquals(20L, selected.last().id)
        assertEquals(12, selected.size)
    }

    @Test
    fun waitsForEnoughUncoveredMessagesBeforeCallingTheModel() {
        val messages = (1L..35L).map { message(it) }
        val selected = selectCloudSummaryMessages(
            messages = messages,
            coveredThroughMessageId = 10,
            recentMessageLimit = 30,
            maxMessages = 20,
            minMessages = 12,
        )

        assertTrue(selected.isEmpty())
    }

    @Test
    fun extractsStructuredSummaryAndDropsSpeakerTranscriptLines() {
        val parsed = sanitizeGeneratedConversationSummary(
            raw = """{"summary":["用户准备周五去厦门面试","角色答应面试前陪用户练习","用户：这条只是转录"]}""",
            characterName = "青禾",
        )

        assertTrue(parsed.contains("用户准备周五去厦门面试"))
        assertTrue(parsed.contains("角色答应面试前陪用户练习"))
        assertTrue("这条只是转录" !in parsed)
    }

    @Test
    fun transcriptLabelsSpeakersAndRemovesStageDirections() {
        val transcript = buildCloudSummaryTranscript(
            messages = listOf(
                message(1, "user", "（压低声音）我明天去面试"),
                message(2, "assistant", "（点头）我等你回来"),
            ),
            characterName = "青禾",
        )

        assertTrue(transcript.contains("用户：我明天去面试"))
        assertTrue(transcript.contains("青禾：我等你回来"))
        assertTrue("压低声音" !in transcript)
    }
}
