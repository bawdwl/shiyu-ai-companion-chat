package com.shiyu.chat.data

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

internal fun insertActionBrackets(value: TextFieldValue): TextFieldValue {
    val text = value.text
    val start = value.selection.min.coerceIn(0, text.length)
    val end = value.selection.max.coerceIn(start, text.length)
    val selected = text.substring(start, end)
    val insertion = if (selected.isBlank()) "（）" else "（$selected）"
    if (text.length + insertion.length > 4096) return value
    val updated = text.replaceRange(start, end, insertion)
    val cursor = if (selected.isBlank()) {
        start + 1
    } else {
        start + insertion.length
    }
    return TextFieldValue(
        text = updated,
        selection = TextRange(cursor),
    )
}
