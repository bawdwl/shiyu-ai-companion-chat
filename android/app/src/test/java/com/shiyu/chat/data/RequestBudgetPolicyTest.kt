package com.shiyu.chat.data

import org.junit.Assert.assertTrue
import org.junit.Test

class RequestBudgetPolicyTest {
    @Test
    fun promptBudgetIncludesHistorySystemAndImageCost() {
        val estimate = estimatePromptTokenBudget(
            textCharacters = 1_600,
            messageCount = 10,
            imageCount = 1,
            maxOutputTokens = 900,
        )
        assertTrue(estimate > 2_000)
    }
}
