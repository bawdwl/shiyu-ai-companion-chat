package com.shiyu.chat.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Recovers complete candidate objects when a model response is truncated
 * before the closing array. The response is never trusted as a complete
 * document unless it is explicitly parseable.
 */
internal fun recoverStructuredMemoryCandidates(raw: String): JSONArray {
    val direct = parseCompleteJsonObject(raw)
        ?.optJSONArray("candidates")
        ?.takeIf { it.length() > 0 }
    if (direct != null) return direct

    val recovered = JSONArray()
    recoverJsonObjectTextsAfterKey(raw, "candidates").forEach { text ->
        runCatching { JSONObject(text) }.getOrNull()?.let(recovered::put)
    }
    return recovered
}

internal fun recoverJsonObjectTextsAfterKey(raw: String, key: String): List<String> {
    val marker = raw.indexOf("\"$key\"")
    if (marker < 0) return emptyList()
    val arrayStart = raw.indexOf('[', marker)
    if (arrayStart < 0) return emptyList()

    val recovered = mutableListOf<String>()
    var index = arrayStart + 1
    while (index < raw.length) {
        when (raw[index]) {
            ']' -> break
            '{' -> {
                val end = findBalancedObjectEnd(raw, index)
                if (end < 0) break
                recovered += raw.substring(index, end + 1)
                index = end + 1
            }
            else -> index++
        }
    }
    return recovered
}

internal fun parseCompleteJsonObject(raw: String): JSONObject? {
    val start = raw.indexOf('{')
    if (start < 0) return null
    val end = findBalancedObjectEnd(raw, start)
    if (end < 0) return null
    return runCatching { JSONObject(raw.substring(start, end + 1)) }.getOrNull()
}

private fun findBalancedObjectEnd(raw: String, start: Int): Int {
    var depth = 0
    var inString = false
    var escaped = false
    for (index in start until raw.length) {
        val char = raw[index]
        when {
            inString && escaped -> escaped = false
            inString && char == '\\' -> escaped = true
            inString && char == '"' -> inString = false
            !inString && char == '"' -> inString = true
            !inString && char == '{' -> depth++
            !inString && char == '}' -> {
                depth--
                if (depth == 0) return index
            }
        }
    }
    return -1
}
