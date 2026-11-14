package com.shiyu.chat.data

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class QwenEmbeddingIndexerTest {
    @Test
    fun vectorBlobRoundTripsWithLittleEndianEncoding() {
        val vector = floatArrayOf(0.25f, -0.5f, 0.75f, 1f)
        val decoded = QwenEmbeddingIndexer.decodeVector(
            QwenEmbeddingIndexer.encodeVector(vector),
        )

        assertArrayEquals(vector, decoded, 0f)
    }
}
