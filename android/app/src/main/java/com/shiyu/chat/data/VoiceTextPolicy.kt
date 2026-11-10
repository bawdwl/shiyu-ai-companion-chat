package com.shiyu.chat.data

internal const val TTS_VOICE_COUNT = 5
internal val TTS_SELECTABLE_SPEAKER_IDS = listOf(0, 1, 2, 3)

internal fun splitVoiceText(text: String, limit: Int = 180): List<String> {
    require(limit > 0)
    val chunks = mutableListOf<String>()
    var start = 0
    while (start < text.length) {
        var end = minOf(start + limit, text.length)
        if (end < text.length) {
            val boundary = (end - 1 downTo start).firstOrNull { text[it] in "。！？!?；;\n" }
            if (boundary != null && boundary > start) end = boundary + 1
            if (end < text.length &&
                Character.isHighSurrogate(text[end - 1]) &&
                Character.isLowSurrogate(text[end])
            ) {
                end--
            }
        }
        if (end <= start) end = minOf(start + 2, text.length)
        val part = text.substring(start, end)
        if (part.isNotBlank()) chunks.add(part)
        start = end
    }
    return chunks
}

internal fun normalizeTtsSpeakerId(saved: Int): Int = when (saved) {
    in TTS_SELECTABLE_SPEAKER_IDS -> saved
    4 -> 1
    else -> 0
}
