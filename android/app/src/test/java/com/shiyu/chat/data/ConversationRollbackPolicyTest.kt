package com.shiyu.chat.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationRollbackPolicyTest {
    @Test
    fun legacyMemoryIsRemovedWhenAnySourceIsRolledBack() {
        assertTrue(legacyMemoryTouchesRollback("1, 4,8", 4))
        assertFalse(legacyMemoryTouchesRollback("1,2,3", 4))
    }

    @Test
    fun structuredMemoryIsRemovedWhenEvidenceCrossesCutoff() {
        val evidence = """[
            {"message_id":2,"quote":"a"},
            {"message_id":7,"quote":"b"}
        ]"""

        assertTrue(structuredMemoryTouchesRollback(evidence, 7))
        assertFalse(structuredMemoryTouchesRollback(evidence, 8))
    }

    @Test
    fun malformedEvidenceIsConservativelyRemoved() {
        assertTrue(structuredMemoryTouchesRollback("not-json", 1))
    }
}
