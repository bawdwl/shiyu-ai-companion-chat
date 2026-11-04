package com.shiyu.chat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DreamPromptPolicyTest {
    private fun segmentCount(text: String): Int =
        text.split(',').count(String::isNotBlank)

    private fun character(
        description: String,
        appearance: String = "",
        appearancePrompt: String = "",
    ) = CharacterEntity(
        id = "dream-policy",
        name = "测试角色",
        tag = "",
        description = description,
        personality = "",
        speechStyle = "",
        relationship = "",
        appearance = appearance,
        appearancePrompt = appearancePrompt,
        greeting = "",
        era = "",
        knowledgeCutoff = "",
        forbiddenKnowledge = "",
        forbiddenTerms = "",
        custom = true,
    )

    @Test
    fun keeps_leading_character_details_when_prompt_is_too_long() {
        val compacted = compactCommaPromptToLimit(
            "masterpiece, best quality, brown hair, brown eyes, " +
                "knit cardigan, library, soft light, bokeh",
            maxTokens = 6,
            tokenCounter = ::segmentCount,
        )

        assertEquals(
            "masterpiece, best quality, brown hair, brown eyes, knit cardigan, library",
            compacted,
        )
    }

    @Test
    fun removes_duplicate_segments_before_trimming() {
        val compacted = compactCommaPromptToLimit(
            "masterpiece, brown hair, Brown Hair, brown eyes",
            maxTokens = 3,
            tokenCounter = ::segmentCount,
        )

        assertEquals("masterpiece, brown hair, brown eyes", compacted)
        assertFalse(compacted.lowercase().contains("brown hair, brown hair"))
    }

    @Test
    fun truncates_a_single_long_segment_by_words() {
        val compacted = compactCommaPromptToLimit(
            "young East Asian woman with long brown hair and brown eyes",
            maxTokens = 5,
            tokenCounter = { text -> text.split(Regex("\\s+")).count(String::isNotBlank) },
        )

        assertEquals("young East Asian woman with", compacted)
        assertTrue(compacted.split(Regex("\\s+")).size <= 5)
    }

    @Test
    fun infers_male_subject_and_removes_female_conflict() {
        val male = character(
            description = "江昭谌，26岁，身高193cm，沪圈顶级财阀继承人。他对各类示好一概漠视。",
            appearance = "他人高肩宽，短发，穿深色正装。",
        )
        val subject = inferVisualSubject(male)
        val prompt = ensureVisualSubjectTags(
            "young woman, long hair, business suit",
            subject,
        )

        assertEquals(VisualSubject.MALE, subject)
        assertTrue(prompt.startsWith("1boy, male, solo"))
        assertFalse(prompt.contains("young woman", ignoreCase = true))
    }

    @Test
    fun dream_template_preserves_male_subject_tags() {
        val male = character(
            description = "江昭谌是男性角色，26岁。",
            appearance = "短发，冷黑色眼睛，深色正装。",
        )

        val prompt = DreamGenerationSettings().buildPrompt(male)

        assertTrue(prompt.startsWith("masterpiece, best quality, 1boy, male, solo"))
    }
}
