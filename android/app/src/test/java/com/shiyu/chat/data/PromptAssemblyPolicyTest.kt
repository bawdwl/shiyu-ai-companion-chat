package com.shiyu.chat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptAssemblyPolicyTest {
    @Test
    fun requiredSectionsSurviveSmallBudgets() {
        val result = PromptAssemblyPolicy.assemble(
            drafts = listOf(
                PromptSectionDraft(PromptSectionKind.ROLE_IDENTITY, "身份"),
                PromptSectionDraft(PromptSectionKind.ROLE_BEHAVIOR, "行为"),
                PromptSectionDraft(PromptSectionKind.ROLE_CARD, "角色".repeat(600)),
                PromptSectionDraft(PromptSectionKind.SAFETY, "安全"),
                PromptSectionDraft(PromptSectionKind.SUMMARY, "可选摘要"),
            ),
            maxCharacters = 1_000,
        )

        assertTrue(result.systemText.contains("身份"))
        assertTrue(result.systemText.contains("行为"))
        assertTrue(result.systemText.contains("角色"))
        assertTrue(result.systemText.contains("安全"))
        assertFalse(result.systemText.contains("可选摘要"))
    }

    @Test
    fun higherPriorityOptionalSectionsEnterBeforeLowerPriorityOnes() {
        val result = PromptAssemblyPolicy.assemble(
            drafts = listOf(
                PromptSectionDraft(PromptSectionKind.EXAMPLES, "示例".repeat(700)),
                PromptSectionDraft(PromptSectionKind.WORLD_LORE, "世界".repeat(100)),
                PromptSectionDraft(PromptSectionKind.EXAMPLES, "示例"),
            ),
            maxCharacters = 1_100,
        )

        assertTrue(result.systemText.contains("世界设定 Lore"))
        assertFalse(result.systemText.contains("语气示例"))
    }

    @Test
    fun sectionLimitsAndReportedCharactersAreDeterministic() {
        val result = PromptAssemblyPolicy.assemble(
            drafts = listOf(
                PromptSectionDraft(
                    PromptSectionKind.MEMORY,
                    "记".repeat(PromptSectionKind.MEMORY.maxCharacters + 100),
                ),
            ),
            maxCharacters = 10_000,
        )

        assertEquals(PromptSectionKind.MEMORY.maxCharacters, result.sections.single().characters)
        assertEquals(result.systemText.length, result.usedCharacters)
    }
}
