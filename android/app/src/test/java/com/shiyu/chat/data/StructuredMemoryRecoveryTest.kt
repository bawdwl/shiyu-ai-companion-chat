package com.shiyu.chat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuredMemoryRecoveryTest {
    @Test
    fun completeResponseUsesFullCandidateArray() {
        val raw = """
            {"candidates":[
              {"fact_key":"user.interview.time","content":"面试改到周五"},
              {"fact_key":"user.preference.drink","content":"用户喜欢咖啡"}
            ],"state_update":{"significant":false}}
        """.trimIndent()

        val candidates = recoverJsonObjectTextsAfterKey(raw, "candidates")
        assertEquals(2, candidates.size)
        assertTrue(candidates.first().contains("\"user.interview.time\""))
    }

    @Test
    fun truncatedResponseKeepsCompleteObjects() {
        val raw = """
            {"candidates":[
              {"fact_key":"user.interview.time","content":"面试改到周五","evidence":[
                {"message_id":10,"role":"user","quote":"改到周五了"}
              ]},
              {"fact_key":"user.preference.drink","content":"用户喜欢咖啡"},
              {"fact_key":"user.interview.result","content":"面试结果还没出
        """.trimIndent()

        val candidates = recoverJsonObjectTextsAfterKey(raw, "candidates")
        assertEquals(2, candidates.size)
        assertTrue(candidates[1].contains("\"user.preference.drink\""))
    }

    @Test
    fun truncatedObjectWithoutCompleteCandidatesReturnsEmpty() {
        val raw = """{"candidates":[{"fact_key":"user.foo","content":"未完成"""
        assertTrue(recoverJsonObjectTextsAfterKey(raw, "candidates").isEmpty())
    }

    @Test
    fun bracesAndQuotesInsideStringsDoNotBreakRecovery() {
        val raw = """
            {"candidates":[
              {"content":"她写下“{剑}”这个字","fact_key":"shared.sword.symbol"},
              {"content":"下一项
        """.trimIndent()

        val candidates = recoverJsonObjectTextsAfterKey(raw, "candidates")
        assertEquals(1, candidates.size)
        assertTrue(candidates.single().contains("她写下“{剑}”这个字"))
    }
}
