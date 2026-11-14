package com.shiyu.chat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterGenerationPolicyTest {
    @Test
    fun promptRequestsDetailedDistinctiveAppearance() {
        assertTrue(CharacterGenerationPolicy.SYSTEM_PROMPT.contains("worldbook 生成5条"))
        assertTrue(CharacterGenerationPolicy.SYSTEM_PROMPT.contains("description 用100至200个中文字符"))
        assertTrue(CharacterGenerationPolicy.SYSTEM_PROMPT.contains("public_intro 用120至220个中文字符"))
        assertFalse(CharacterGenerationPolicy.SYSTEM_PROMPT.contains("beautiful detailed face"))
        assertTrue(
            CharacterGenerationPolicy.SYSTEM_PROMPT.contains(
                "appearance 必须填写，用80至150个中文字符",
            ),
        )
        assertTrue(
            CharacterGenerationPolicy.SYSTEM_PROMPT.contains(
                "必须根据角色整体设定独立生成，不得逐句翻译",
            ),
        )
        assertTrue(CharacterGenerationPolicy.SYSTEM_PROMPT.contains("控制在28至42个英文词"))
        assertTrue(CharacterGenerationPolicy.MATERIAL_REPAIR_PROMPT.contains("worldbook 给 5 条"))
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
                "public_intro",
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
            publicIntro = "初".repeat(120),
            personality = "性".repeat(80),
            speechStyle = "语".repeat(60),
            relationship = "关".repeat(40),
            appearance = "貌".repeat(130),
            appearancePrompt =
                "1boy male solo twenty six year old angular face narrow black eyes " +
                    "straight brows high nose thin lips fair skin short black hair " +
                    "tall broad shoulders dark charcoal suit silver tie watch " +
                    "calm expression candlelit study",
            worldbookJson = "[{},{},{},{}]",
        )
        assertTrue(CharacterGenerationPolicy.missingMaterials(complete).isEmpty())
    }

    private fun character(
        description: String,
        publicIntro: String = "",
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
        publicIntro = publicIntro,
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
