package com.shiyu.chat.data

internal const val CLOUD_SUMMARY_RECENT_MESSAGE_LIMIT = 30
internal const val CLOUD_SUMMARY_MAX_MESSAGES_PER_UPDATE = 30
internal const val CLOUD_SUMMARY_MIN_MESSAGES_PER_UPDATE = 24
internal const val CLOUD_SUMMARY_MAX_CHARS = 900
internal const val CLOUD_SUMMARY_MAX_SOURCE_IDS = 120

internal fun selectCloudSummaryMessages(
    messages: List<MessageEntity>,
    coveredThroughMessageId: Long,
    recentMessageLimit: Int = CLOUD_SUMMARY_RECENT_MESSAGE_LIMIT,
    maxMessages: Int = CLOUD_SUMMARY_MAX_MESSAGES_PER_UPDATE,
    minMessages: Int = CLOUD_SUMMARY_MIN_MESSAGES_PER_UPDATE,
): List<MessageEntity> {
    if (messages.isEmpty()) return emptyList()
    val selected = messages
        .dropLast(recentMessageLimit.coerceAtLeast(0))
        .asSequence()
        .filter { it.id > coveredThroughMessageId }
        .filter { it.role in setOf("user", "assistant") }
        .filter { it.content.isNotBlank() }
        .take(maxMessages.coerceAtLeast(0))
        .toList()
    return selected.takeIf { it.size >= minMessages.coerceAtLeast(1) }.orEmpty()
}

internal fun buildCloudSummaryTranscript(
    messages: List<MessageEntity>,
    characterName: String,
): String =
    messages.joinToString("\n") { message ->
        val speaker = if (message.role == "user") "用户" else characterName
        val content = message.content
            .replace(Regex("[（(][^）)]*[）)]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(260)
        "$speaker：$content"
    }

internal fun sanitizeGeneratedConversationSummary(
    raw: String,
    characterName: String,
    maxLines: Int = 8,
    maxChars: Int = CLOUD_SUMMARY_MAX_CHARS,
): String {
    val parsed = extractJsonStringArray(raw, "summary")
    val candidates = if (parsed.isNotEmpty()) {
        parsed
    } else {
        raw
            .replace(Regex("(?s)```(?:text|markdown|json)?\\s*"), "")
            .replace("```", "")
            .replace(Regex("(?s)<think>.*?</think>"), "")
            .lineSequence()
            .toList()
    }
    return candidates
        .asSequence()
        .map { line -> line.trim().trimStart('-', '•', '*').trim() }
        .filter(String::isNotBlank)
        .filterNot { line ->
            line == "摘要" ||
                line.startsWith("以下是") ||
                line.startsWith("对话摘要") ||
                Regex("^(用户|${Regex.escape(characterName)})[：:]").containsMatchIn(line) ||
                line == "输入" ||
                line == "输出"
        }
        .map { line -> line.replace(Regex("\\s+"), " ").take(150) }
        .filter { it.length >= 4 }
        .distinctBy { it.replace(Regex("[\\s\\p{P}\\p{S}]+"), "") }
        .take(maxLines.coerceAtLeast(1))
        .joinToString("\n") { "- $it" }
        .take(maxChars.coerceAtLeast(120))
        .trim()
}

private fun extractJsonStringArray(raw: String, key: String): List<String> {
    val marker = raw.indexOf("\"$key\"")
    if (marker < 0) return emptyList()
    val start = raw.indexOf('[', marker)
    if (start < 0) return emptyList()
    val output = mutableListOf<String>()
    var index = start + 1
    while (index < raw.length) {
        when (raw[index]) {
            ']' -> return output
            '"' -> {
                val parsed = readJsonString(raw, index)
                    ?: return output
                if (parsed.first.isNotBlank()) output += parsed.first
                index = parsed.second + 1
            }
            else -> index++
        }
    }
    return output
}

private fun readJsonString(raw: String, start: Int): Pair<String, Int>? {
    if (start >= raw.length || raw[start] != '"') return null
    val value = StringBuilder()
    var index = start + 1
    while (index < raw.length) {
        val char = raw[index]
        when {
            char == '"' -> return value.toString() to index
            char == '\\' && index + 1 < raw.length -> {
                val escaped = raw[index + 1]
                when (escaped) {
                    'n' -> value.append('\n')
                    'r' -> value.append('\r')
                    't' -> value.append('\t')
                    '"', '\\', '/' -> value.append(escaped)
                    'u' -> {
                        val end = index + 6
                        if (end <= raw.length) {
                            runCatching {
                                raw.substring(index + 2, end).toInt(16).toChar()
                            }.getOrNull()?.let(value::append)
                            index = end - 2
                        }
                    }
                    else -> value.append(escaped)
                }
                index += 2
            }
            else -> {
                value.append(char)
                index++
            }
        }
    }
    return null
}
