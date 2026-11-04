package com.shiyu.chat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceChunksTest {
    @Test
    fun preservesPublishedKokoroSpeakerIdsAndBounds() {
        assertEquals(3, normalizeKokoroSpeakerId(3))
        assertEquals(4, normalizeKokoroSpeakerId(4))
        assertEquals(58, normalizeKokoroSpeakerId(58))
        assertEquals(59, normalizeKokoroSpeakerId(59))
        assertEquals(102, normalizeKokoroSpeakerId(102))
        assertEquals(0, normalizeKokoroSpeakerId(103))
    }

    @Test fun preservesLongContentAndPrefersSentenceBoundaries() {
        val text = "第一句。" + "长".repeat(530) + "结束！"
        val chunks = splitVoiceText(text)
        assertEquals(text, chunks.joinToString(""))
        assertEquals("第一句。", chunks.first())
        assertTrue(chunks.all { it.length <= 180 })
    }
    @Test fun doesNotSplitEmojiSurrogatePairs() {
        val text = "甲".repeat(179) + "😀" + "乙"
        assertEquals(text, splitVoiceText(text).joinToString(""))
        assertTrue(splitVoiceText(text).none { it.last().isHighSurrogate() })
    }
}
