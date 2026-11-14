package com.shiyu.chat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class OpenCodeClientTest {
    @Test
    fun usesOpenCodeGoChatCompletionsContract() {
        assertEquals("deepseek-v4.1-flash", OpenCodeClient.MODEL)
        assertEquals("https://opencode.ai/zen/go/v1", OpenCodeClient.BASE_URL)
        assertEquals("shiyu-chat/0.21.0", OpenCodeClient.USER_AGENT)
    }

    @Test
    fun neverSendsUnsupportedJsonResponseFormat() {
        val fields = OpenCodeRequestPolicy.fields(
            maxTokens = 900,
            stream = false,
        )

        assertEquals("deepseek-v4.1-flash", fields["model"])
        assertEquals(900, fields["max_tokens"])
        assertEquals(false, fields["stream"])
        assertEquals(mapOf("type" to "disabled"), fields["thinking"])
        assertFalse(fields.containsKey("response_format"))
        assertFalse(fields.containsKey("stream_options"))
    }
}
