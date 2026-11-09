package com.shiyu.chat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CharacterGenerationProviderTest {
    @Test
    fun preservesDeepSeekOfficialProviderSelection() {
        assertEquals("deepseek", characterGenerationProvider("deepseek"))
    }

    @Test
    fun preservesOpenCodeProviderSelection() {
        assertEquals("opencode", characterGenerationProvider("opencode"))
    }

    @Test
    fun rejectsUnknownProvider() {
        assertThrows(IllegalStateException::class.java) {
            characterGenerationProvider("local")
        }
    }
}
