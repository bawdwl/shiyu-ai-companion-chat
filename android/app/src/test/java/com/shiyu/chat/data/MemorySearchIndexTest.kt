package com.shiyu.chat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemorySearchIndexTest {
    @Test
    fun currentAndHistoricalQueriesUseDifferentStateScopes() {
        val current = MemorySearchIndex.querySql(includeHistory = false)
        val historical = MemorySearchIndex.querySql(includeHistory = true)
        assertTrue(current.contains("('active')"))
        assertFalse(current.contains("superseded"))
        assertTrue(historical.contains("('active','superseded')"))
    }

    @Test
    fun ftsTriggersCoverInsertUpdateAndDelete() {
        val sql = MemorySearchIndex.createStatements.joinToString("\n")
        assertTrue(sql.contains("structured_memory_fts_ai"))
        assertTrue(sql.contains("AFTER UPDATE OF searchTokens"))
        assertTrue(sql.contains("structured_memory_fts_ad"))
        assertTrue(sql.contains("DELETE FROM structured_memory_fts"))
    }

    @Test
    fun queryBuilderCapsLargeInputsAndKeepsEveryTermQuoted() {
        val query = buildMemoryFtsQuery("面".repeat(1_000) + " secret* OR user")
        val terms = query.split(" OR ")
        assertTrue(terms.size <= 64)
        assertTrue(terms.all { it.startsWith("\"") && it.endsWith("\"") })
        assertFalse("*" in query)
    }

    @Test
    fun searchDocumentContainsChineseAndEnglishTokens() {
        val document = memorySearchDocument(
            subject = "user",
            factKey = "user.interview.time",
            value = "周五",
            content = "面试改到周五",
        )
        assertTrue("面试" in document)
        assertTrue("周五" in document)
        assertTrue("interview" in document)
        assertEquals(document.split(' ').distinct().joinToString(" "), document)
    }
}
