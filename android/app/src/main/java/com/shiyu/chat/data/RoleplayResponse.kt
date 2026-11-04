package com.shiyu.chat.data

import org.json.JSONObject

/**
 * Compatibility reader for messages saved during the rejected four-part UI
 * experiment. New messages are stored as one natural chat string with an empty
 * displayJson field.
 */
data class RoleplayDisplay(
    val speech: String,
    val action: String,
) {
    companion object {
        fun fromJson(raw: String?): RoleplayDisplay? {
            if (raw.isNullOrBlank()) return null
            return runCatching {
                val root = JSONObject(raw)
                val speech = root.optString("speech").trim()
                if (speech.isBlank()) return null
                RoleplayDisplay(
                    speech = speech,
                    action = root.optString("action").trim(),
                )
            }.getOrNull()
        }
    }
}

fun naturalMessageText(content: String, displayJson: String?): String {
    val legacy = RoleplayDisplay.fromJson(displayJson) ?: return content
    val speech = legacy.speech.ifBlank { content.trim() }
    val action = legacy.action
        .trim('（', '）', '(', ')')
        .takeUnless { it.isBlank() || it == "没有明显的外部动作变化。" }
    return if (action.isNullOrBlank()) speech else "（$action）$speech"
}

/**
 * Remove short parenthetical stage directions before local TTS.
 */
fun speechOnlyText(text: String): String {
    val spoken = Regex("""[（(][^（()）\n]{1,120}[）)]""")
        .replace(text, " ")
        .replace(Regex("""\s+"""), " ")
        .replace(Regex("""\s*([，。！？；：、])\s*"""), "$1")
        .trim()
    return spoken.ifBlank { text }
}
