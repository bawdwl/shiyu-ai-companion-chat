package com.shiyu.chat.data

internal enum class VisualSubject {
    MALE,
    FEMALE,
    NEUTRAL,
}

internal fun inferVisualSubject(character: CharacterEntity): VisualSubject {
    val profile = sequenceOf(
        character.name,
        character.tag,
        character.description,
        character.personality,
        character.speechStyle,
        character.relationship,
        character.appearance,
        character.appearancePrompt,
    ).joinToString(" ")
    val male = Regex(
        """(?i)\b1boy\b|\bmale\b|\byoung man\b|\bman\b|\bboy\b|男性|男生|男孩|男人|少年|先生|学长|哥哥|父亲|爸爸|他""",
    ).findAll(profile).count()
    val female = Regex(
        """(?i)\b1girl\b|\bfemale\b|\byoung woman\b|\bwoman\b|\bgirl\b|女性|女生|女孩|女人|少女|女士|学姐|姐姐|母亲|妈妈|她""",
    ).findAll(profile).count()
    return when {
        male > female -> VisualSubject.MALE
        female > male -> VisualSubject.FEMALE
        Regex("""(?i)\b1boy\b""").containsMatchIn(profile) -> VisualSubject.MALE
        Regex("""(?i)\b1girl\b""").containsMatchIn(profile) -> VisualSubject.FEMALE
        else -> VisualSubject.NEUTRAL
    }
}

internal fun appearancePromptConflicts(
    prompt: String,
    subject: VisualSubject,
): Boolean {
    val femaleMarkers = Regex("""(?i)\b1girl\b|\byoung woman\b|\bwoman\b|\bfemale\b""")
    val maleMarkers = Regex("""(?i)\b1boy\b|\byoung man\b|\bman\b|\bmale\b""")
    return when (subject) {
        VisualSubject.MALE -> femaleMarkers.containsMatchIn(prompt)
        VisualSubject.FEMALE -> maleMarkers.containsMatchIn(prompt)
        VisualSubject.NEUTRAL -> false
    }
}

internal fun ensureVisualSubjectTags(
    prompt: String,
    subject: VisualSubject,
): String {
    val opposite = when (subject) {
        VisualSubject.MALE -> Regex(
            """(?i)\b1girl\b|\byoung woman\b|\bwoman\b|\bfemale\b|\bgirl\b""",
        )
        VisualSubject.FEMALE -> Regex(
            """(?i)\b1boy\b|\byoung man\b|\bman\b|\bmale\b|\bboy\b""",
        )
        VisualSubject.NEUTRAL -> null
    }
    val cleaned = prompt
        .let { value -> opposite?.replace(value, "") ?: value }
        .replace(Regex("\\s+"), " ")
        .replace(Regex("(^|,)\\s*,"), ",")
        .replace(Regex(",\\s*,"), ",")
        .trim(' ', ',')
    val lower = cleaned.lowercase()
    val hasExpected = when (subject) {
        VisualSubject.MALE -> lower.contains("1boy")
        VisualSubject.FEMALE -> lower.contains("1girl")
        VisualSubject.NEUTRAL -> false
    }
    if (hasExpected) return cleaned
    val prefix = when (subject) {
        VisualSubject.MALE -> "1boy, male, solo"
        VisualSubject.FEMALE -> "1girl, female, solo"
        VisualSubject.NEUTRAL -> "solo"
    }
    return listOf(prefix, cleaned)
        .filter(String::isNotBlank)
        .joinToString(", ")
}

internal fun compactCommaPromptToLimit(
    prompt: String,
    maxTokens: Int,
    tokenCounter: (String) -> Int,
): String {
    require(maxTokens > 0) { "Token 上限必须大于 0" }
    val segments = prompt
        .replace(Regex("[\\r\\n]+"), " ")
        .split(',')
        .map { it.trim() }
        .filter(String::isNotEmpty)
        .distinctBy(String::lowercase)
    require(segments.isNotEmpty()) { "正面提示词不能为空" }

    for (retain in segments.size downTo 1) {
        val candidate = segments.take(retain).joinToString(", ")
        if (tokenCounter(candidate) <= maxTokens) return candidate
    }

    val first = segments.first()
    val words = first.split(Regex("\\s+"))
    var low = 1
    var high = words.size
    var best = ""
    while (low <= high) {
        val middle = (low + high) / 2
        val candidate = words.take(middle).joinToString(" ")
        if (tokenCounter(candidate) <= maxTokens) {
            best = candidate
            low = middle + 1
        } else {
            high = middle - 1
        }
    }
    return best.ifBlank {
        throw IllegalArgumentException(
            "提示词中的第一个片段已经超过 $maxTokens Token，请进一步缩短",
        )
    }
}
