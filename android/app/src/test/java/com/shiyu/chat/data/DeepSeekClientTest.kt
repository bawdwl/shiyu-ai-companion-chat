package com.shiyu.chat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DeepSeekClientTest {
    @Test
    fun usesOfficialDeepSeekChatContract() {
        val fields = DeepSeekRequestPolicy.fields(
            maxTokens = 900,
            stream = false,
            json = true,
        )

        assertEquals("deepseek-chat", DeepSeekClient.MODEL)
        assertEquals("https://api.deepseek.com", DeepSeekClient.BASE_URL)
        assertEquals("deepseek-chat", fields["model"])
        assertEquals(900, fields["max_tokens"])
        assertEquals(false, fields["stream"])
        assertEquals(mapOf("type" to "json_object"), fields["response_format"])
        assertFalse(fields.containsKey("thinking"))
    }

    @Test
    fun streamingRequestsIncludeUsageButNotThinkingOverride() {
        val fields = DeepSeekRequestPolicy.fields(
            maxTokens = 256,
            stream = true,
            json = false,
        )

        assertEquals(mapOf("include_usage" to true), fields["stream_options"])
        assertFalse(fields.containsKey("response_format"))
        assertFalse(fields.containsKey("thinking"))
    }
}
