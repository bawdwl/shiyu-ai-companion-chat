package com.shiyu.chat.data

import org.junit.Assert.assertTrue
import org.junit.Test

class OpeningMessageTest {
    private fun character(greeting: String) = CharacterEntity(
        id = "custom-test",
        name = "小满",
        tag = "原创角色",
        description = "在街角书店整理旧书",
        personality = "温柔直率",
        speechStyle = "自然",
        relationship = "初次见面",
        greeting = greeting,
        era = "当代",
        knowledgeCutoff = "当前",
        forbiddenKnowledge = "",
        forbiddenTerms = "",
        custom = true,
    )

    @Test
    fun usesAuthorOrModelWrittenOpening() {
        val greeting = "（把一摞旧书放到桌上）你来得正好，我在书里发现了一张没有署名的明信片。"
        assertTrue(openingForCharacter(character(greeting)) == greeting)
    }

    @Test
    fun createsNaturalFallbackForOlderCharacters() {
        val opening = openingForCharacter(character(""))
        assertTrue(opening.startsWith("（小满停下手边的事"))
        assertTrue(opening.contains("街角书店"))
    }
}
