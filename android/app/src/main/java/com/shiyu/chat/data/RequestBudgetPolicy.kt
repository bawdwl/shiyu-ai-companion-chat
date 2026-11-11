package com.shiyu.chat.data

import org.json.JSONArray

/**
 * Conservative preflight estimate for the complete request body. Cloud
 * providers still tokenize server-side, but the client must not validate only
 * the newest user text while silently ignoring character, history and memory.
 */
internal fun estimatePromptTokens(
    messages: JSONArray,
    maxOutputTokens: Int,
): Int {
    var textCharacters = 0
    var imageCount = 0
    for (index in 0 until messages.length()) {
        val content = messages.optJSONObject(index)?.opt("content") ?: continue
        when (content) {
            is String -> textCharacters += content.length
            is JSONArray -> {
                for (partIndex in 0 until content.length()) {
                    val part = content.optJSONObject(partIndex) ?: continue
                    when (part.optString("type")) {
                        "text" -> textCharacters += part.optString("text").length
                        "image_url" -> imageCount += 1
                    }
                }
            }
            else -> textCharacters += content.toString().length
        }
    }
    return estimatePromptTokenBudget(
        textCharacters = textCharacters,
        messageCount = messages.length(),
        imageCount = imageCount,
        maxOutputTokens = maxOutputTokens,
    )
}

internal fun estimatePromptTokenBudget(
    textCharacters: Int,
    messageCount: Int,
    imageCount: Int,
    maxOutputTokens: Int,
): Int = (
    (
        textCharacters.coerceAtLeast(0) / 2 +
            messageCount.coerceAtLeast(0) * 8 +
            imageCount.coerceAtLeast(0) * 1_024 +
            maxOutputTokens.coerceAtLeast(1)
        ) * 1.15
    ).toInt()
