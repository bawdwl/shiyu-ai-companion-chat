package com.shiyu.chat.data

import org.junit.Assert.assertEquals
import org.junit.Test

class QwenBpeTokenizerTest {
    @Test
    fun byteLevelEncoderKeepsPrintableBytesAndMapsControlBytes() {
        val encoder = QwenBpeTokenizer.byteLevelEncoder()

        assertEquals('!', encoder[33])
        assertEquals('¡', encoder[161])
        assertEquals(0x100.toChar(), encoder[0])
    }
}
