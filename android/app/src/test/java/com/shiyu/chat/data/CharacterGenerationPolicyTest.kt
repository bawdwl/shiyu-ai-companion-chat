package com.shiyu.chat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterGenerationPolicyTest {
    @Test
    fun promptMatchesAcceptedCharacterStudioFlow() {
        assertEquals(1323, CharacterGenerationPolicy.SYSTEM_PROMPT.length)
        assertEquals(510, CharacterGenerationPolicy.MATERIAL_REPAIR_PROMPT.length)
        assertTrue(CharacterGenerationPolicy.SYSTEM_PROMPT.contains("worldbook 生成3条"))
        assertTrue(CharacterGenerationPolicy.SYSTEM_PROMPT.contains("description 用100至200个中文字符"))
        assertTrue(CharacterGenerationPolicy.SYSTEM_PROMPT.contains("appearance 必须填写，用80至220个中文字符"))
        assertTrue(CharacterGenerationPolicy.MATERIAL_REPAIR_PROMPT.contains("worldbook 给 3 条"))
    }

    @Test
    fun completenessChecksMatchAcceptedFlow() {
        val sparse = character(
            description = "太短",
            personality = "太短",
            speechStyle = "太短",
            relationship = "太短",
            appearance = "太短",
            appearancePrompt = "1boy, male, solo",
            worldbookJson = "[]",
        )
        assertEquals(
            listOf(
                "description",
                "personality",
                "speech_style",
                "relationship",
                "appearance",
                "appearance_prompt",
                "worldbook",
            ),
            CharacterGenerationPolicy.missingMaterials(sparse),
        )

        val complete = character(
            description = "角".repeat(100),
            personality = "性".repeat(80),
            speechStyle = "语".repeat(60),
            relationship = "关".repeat(40),
            appearance = "貌".repeat(80),
            appearancePrompt = "1boy male solo black hair calm eyes dark suit portrait centered indoor",
            worldbookJson = "[{},{}]",
        )
        assertTrue(CharacterGenerationPolicy.missingMaterials(complete).isEmpty())
    }

    private fun character(
        description: String,
        personality: String,
        speechStyle: String,
        relationship: String,
        appearance: String,
        appearancePrompt: String,
        worldbookJson: String,
    ) = CharacterEntity(
        id = "custom-test",
        name = "测试角色",
        tag = "测试",
        description = description,
        personality = personality,
        speechStyle = speechStyle,
        relationship = relationship,
        appearance = appearance,
        appearancePrompt = appearancePrompt,
        greeting = "",
        era = "现代",
        knowledgeCutoff = "现代",
        forbiddenKnowledge = "",
        forbiddenTerms = "",
        worldbookJson = worldbookJson,
        custom = true,
    )
}
