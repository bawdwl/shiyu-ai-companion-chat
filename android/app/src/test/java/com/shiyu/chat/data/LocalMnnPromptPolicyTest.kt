package com.shiyu.chat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMnnPromptPolicyTest {
    @Test
    fun appendsNoThinkToLatestUserTurn() {
        assertEquals("今天有点累。\n/no_think", ensureNoThink("今天有点累。"))
    }

    @Test
    fun doesNotDuplicateNoThinkSwitch() {
        assertEquals("今天有点累。\n/no_think", ensureNoThink("今天有点累。\n/no_think"))
    }

    @Test
    fun removesExactRepeatedSentenceWithinOneResponse() {
        val result = cleanRepeatedResponse(
            "我刚从画室出来。我刚刚在速写本上画了你，不过还没敢放出来。" +
                "（突然停住，脸红红的）我刚刚在速写本上画了你，不过还没敢放出来。",
        )

        assertEquals(
            "我刚从画室出来。我刚刚在速写本上画了你，不过还没敢放出来。（突然停住，脸红红的）",
            result.text,
        )
        assertEquals(1, result.removedSegments)
    }

    @Test
    fun removesSentenceRepeatedFromAnEarlierTurn() {
        val result = cleanRepeatedResponse(
            "我一直记得你晚上一个人会怕黑。那我陪你走一段。",
            listOf("我一直记得你晚上一个人会怕黑。早点休息。"),
        )

        assertEquals("那我陪你走一段。", result.text)
        assertEquals(1, result.removedSegments)
    }

    @Test
    fun retrySamplerRaisesRepetitionPenalty() {
        val normal = localMnnSamplerConfig(isRetry = false)
        val retry = localMnnSamplerConfig(isRetry = true)

        assertTrue(normal.contains("\"sampler_type\": \"mixed\""))
        assertTrue(normal.contains("\"penalty\": 1.15"))
        assertTrue(retry.contains("\"penalty\": 1.22"))
        assertFalse(normal.contains("\"penalty\": 1.22"))
    }
}
