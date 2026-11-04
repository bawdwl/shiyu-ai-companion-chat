package com.shiyu.chat.data

private val CJK_TEXT = Regex("[\\u3400-\\u9fff]")

internal fun usableEnglishAppearancePrompt(value: String?): Boolean {
    val prompt = value.orEmpty().trim()
    return prompt.length in 8..800 &&
        !CJK_TEXT.containsMatchIn(prompt) &&
        prompt.any(Char::isLetter)
}

internal fun normalizeEnglishAppearancePrompt(raw: String?): String {
    val prompt = raw.orEmpty()
        .replace(Regex("[\\r\\n]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .trim('"', '\'', '`')
        .take(800)
    require(usableEnglishAppearancePrompt(prompt)) {
        "模型没有返回可用的英文外貌提示词"
    }
    return prompt
}
