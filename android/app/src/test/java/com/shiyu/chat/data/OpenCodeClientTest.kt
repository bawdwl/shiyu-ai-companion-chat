package com.shiyu.chat.data

import org.junit.Assert.assertEquals
import org.junit.Test

class OpenCodeClientTest {
    @Test
    fun usesOpenCodeGoChatCompletionsContract() {
        assertEquals("deepseek-v4.1-flash", OpenCodeClient.MODEL)
        assertEquals("https://opencode.ai/zen/go/v1", OpenCodeClient.BASE_URL)
        assertEquals("shiyu-chat/0.12.0", OpenCodeClient.USER_AGENT)
    }
}
