package com.shiyu.chat.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleContextPolicyTest {
    @Test
    fun proposalFactsStayInEditorAndDoNotEnterRuntimePrompt() {
        val sections = RoleContextPolicy.promptSections(
            CharacterRoleContext(
                fixedFacts = listOf(
                    RoleFixedFact("确认", "角色确实是医生", "confirmed"),
                    RoleFixedFact("提案", "角色可能出生在海边", "proposal"),
                ),
            ),
        ).joinToString("\n") { "${it.first}\n${it.second}" }

        assertTrue(sections.contains("角色确实是医生"))
        assertFalse(sections.contains("角色可能出生在海边"))
        assertFalse(sections.contains("待确认草稿"))
    }
}
