package com.shiyu.chat.data

internal const val LOCAL_SUMMARY_RECENT_MESSAGE_LIMIT = 24
internal const val LOCAL_SUMMARY_MAX_MESSAGES_PER_UPDATE = 8

internal fun selectLocalSummaryMessages(
    messages: List<MessageEntity>,
    watermarkMessageId: Long,
    recentMessageLimit: Int = LOCAL_SUMMARY_RECENT_MESSAGE_LIMIT,
    maxMessages: Int = LOCAL_SUMMARY_MAX_MESSAGES_PER_UPDATE,
): List<MessageEntity> {
    if (messages.size <= recentMessageLimit) return emptyList()
    return messages
        .dropLast(recentMessageLimit)
        .asSequence()
        .filter { it.id > watermarkMessageId }
        .filter { it.role in setOf("user", "assistant") }
        .filter { it.content.isNotBlank() }
        .take(maxMessages)
        .toList()
}

internal fun buildLocalSummaryTranscript(
    messages: List<MessageEntity>,
    characterName: String = "角色",
): String =
    messages.joinToString("\n") { message ->
        val speaker = if (message.role == "user") "用户" else characterName
        val content = message.content
            .replace(Regex("[（(][^）)]*[）)]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(240)
        "$speaker：$content"
    }

internal fun sanitizeGeneratedLocalSummary(
    raw: String,
    characterName: String = "角色",
): String {
    val withoutThinking = raw
        .replace(Regex("(?s)```(?:text|markdown)?\\s*"), "")
        .replace("```", "")
        .replace(Regex("(?s)<think>.*?</think>"), "")
        .trim()
    val lines = withoutThinking
        .lineSequence()
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
        .map(::shortenSummaryLine)
        .filter { it.length >= 4 }
        .distinctBy { line ->
            line.replace(Regex("[\\s\\p{P}\\p{S}]+"), "")
        }
        .take(6)
        .toList()
    return lines.joinToString("\n").take(600).trim()
}

private fun shortenSummaryLine(line: String): String {
    val normalized = line
        .replace(Regex("^[“”\"']+|[“”\"']+$"), "")
        .trim()
    val shortened = if (normalized.length <= 70) {
        normalized
    } else {
        val prefix = normalized.take(67)
        val boundary = prefix.lastIndexOfAny(charArrayOf('。', '；', '，', ',', ' '))
        if (boundary >= 20) prefix.take(boundary + 1).trimEnd() else prefix.trimEnd() + "…"
    }
    return "- $shortened"
}
