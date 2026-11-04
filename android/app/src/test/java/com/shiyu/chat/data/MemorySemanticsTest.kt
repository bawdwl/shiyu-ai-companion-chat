package com.shiyu.chat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemorySemanticsTest {
    @Test
    fun hypotheticalPlanStaysUncertain() {
        val result = classifyMemorySemantics(
            kind = "user_report",
            subject = "user",
            content = "用户可能考虑搬到杭州",
            quotes = "我可能考虑搬到杭州，但还没决定",
            roles = setOf("user"),
            knownAt = 1L,
        )
        assertEquals("hypothesis", result.factType)
        assertEquals("uncertain", result.epistemicStatus)
        assertEquals("user_explicit", result.sourceAuthority)
    }

    @Test
    fun assistantImprovisationBecomesRoleClaim() {
        val result = classifyMemorySemantics(
            kind = "character_detail",
            subject = "character",
            content = "小满小时候住在海边",
            quotes = "我小时候住在海边",
            roles = setOf("assistant"),
            knownAt = 2L,
        )
        assertEquals("role_claim", result.kind)
        assertEquals("role_claim", result.factType)
        assertEquals("character_self_report", result.sourceAuthority)
        assertEquals("character_told", result.knowledgeSource)
    }

    @Test
    fun traceRoleVersionChangesWithStableConfigOnly() {
        val character = CharacterEntity(
            id = "xiaoman",
            name = "小满",
            tag = "日常搭子",
            description = "书店店员",
            personality = "温柔直率",
            speechStyle = "自然",
            relationship = "朋友",
            era = "现代",
            knowledgeCutoff = "",
            forbiddenKnowledge = "",
            forbiddenTerms = "",
        )
        val first = roleConfigVersion(character)
        assertEquals(first, roleConfigVersion(character.copy(avatarPath = "/tmp/a.png")))
        assertTrue(first != roleConfigVersion(character.copy(personality = "冷淡")))
    }
}
