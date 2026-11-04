package com.shiyu.chat.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarPromptPolicyTest {
    private fun character(prompt: String) = CharacterEntity(
        id = "custom-avatar-prompt",
        name = "阿雅",
        tag = "花店店员",
        description = "温柔、安静",
        personality = "温柔、安静",
        speechStyle = "自然",
        relationship = "朋友",
        appearance = "二十岁女性，黑色长发，戴浅色发带，常穿棉布裙",
        appearancePrompt = prompt,
        greeting = "你来了。",
        era = "现代",
        knowledgeCutoff = "",
        forbiddenKnowledge = "",
        forbiddenTerms = "",
        custom = true,
    )

    @Test
    fun dream_prompt_uses_model_preset_and_character_appearance() {
        val prompt = DreamGenerationSettings().buildPrompt(
            character(
                "20 year old East Asian woman, oval face, long black hair, " +
                    "pale hair ribbon, cotton dress, quiet flower shop",
            ),
        )

        assertTrue(prompt.startsWith("masterpiece, best quality, 1girl, female, solo"))
        assertTrue(prompt.contains("long black hair"))
        assertFalse(prompt.contains("黑色长发"))
    }

    @Test
    fun chinese_appearance_prompt_is_not_sent_to_sd15() {
        assertFalse(usableEnglishAppearancePrompt("二十岁女性，黑色长发，棉布裙"))
    }

    @Test
    fun translated_prompt_is_compacted_to_one_line() {
        val prompt = normalizeEnglishAppearancePrompt(
            "\"young woman,\nlong black hair, cotton dress.\"",
        )

        assertTrue(prompt.startsWith("young woman"))
        assertTrue(prompt.endsWith("cotton dress."))
        assertFalse(prompt.contains("\n"))
    }

    @Test
    fun dream_prompt_falls_back_to_chinese_appearance() {
        val prompt = DreamGenerationSettings().buildPrompt(
            character("").copy(
                appearancePrompt = "",
                appearance = "二十岁女性，黑色长发，棉布裙",
            ),
        )

        assertTrue(prompt.contains("黑色长发"))
    }

    @Test
    fun dream_settings_clamp_unsafe_or_invalid_values() {
        val normalized = DreamGenerationSettings(
            steps = 99,
            cfg = 0.2f,
            scheduler = "not-a-scheduler",
        ).normalized()

        org.junit.Assert.assertEquals(50, normalized.steps)
        org.junit.Assert.assertEquals(1.0f, normalized.cfg, 0.001f)
        org.junit.Assert.assertEquals("dpm_sde_karras", normalized.scheduler)
    }
}
