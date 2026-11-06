package com.shiyu.chat.data

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class ComposerInsertionPolicyTest {
    @Test
    fun emptyActionBracketsPlaceCursorInside() {
        val result = insertActionBrackets(
            TextFieldValue("你好", selection = TextRange(2)),
        )

        assertEquals("你好（）", result.text)
        assertEquals(TextRange(3), result.selection)
    }

    @Test
    fun selectedTextIsWrappedAsAction() {
        val result = insertActionBrackets(
            TextFieldValue("她笑了笑", selection = TextRange(0, 4)),
        )

        assertEquals("（她笑了笑）", result.text)
        assertEquals(TextRange(6), result.selection)
    }
}
