package com.shiyu.chat.data

import java.security.MessageDigest

internal val structuredCorrectionCues = listOf(
    "不是", "改成", "改到", "改为", "更正", "说错", "现在已经", "后来",
    "不再", "取消了", "重新安排", "搬到", "换成", "现在", "目前", "如今",
)

private val factKeySynonyms = mapOf(
    "day" to "time",
    "date" to "time",
    "when" to "time",
    "timing" to "time",
    "location" to "place",
    "city" to "place",
    "address" to "place",
    "venue" to "place",
    "job" to "work",
    "occupation" to "work",
    "profession" to "work",
    "employer" to "work",
)

internal fun canonicalFactKey(factKey: String?, subject: String): String? {
    if (factKey.isNullOrBlank()) return null
    val parts = factKey.lowercase().split('.').toMutableList()
    if (parts.firstOrNull() in setOf("current", "profile", "item", "fact")) {
        parts[0] = subject
    }
    if (parts.isNotEmpty()) {
        parts[parts.lastIndex] = factKeySynonyms[parts.last()] ?: parts.last()
    }
    return parts.joinToString(".")
}

internal fun factKeySignature(factKey: String?): String? {
    if (factKey.isNullOrBlank()) return null
    val parts = factKey.lowercase().split('.').toMutableList()
    if (parts.firstOrNull() in setOf("user", "character", "shared", "current", "profile")) {
        parts.removeAt(0)
    }
    return parts.joinToString(".") { factKeySynonyms[it] ?: it }
}

internal fun normalizedMemoryText(value: String?): String =
    value.orEmpty().lowercase().filterNot(Char::isWhitespace)

internal fun memorySearchDocument(
    subject: String,
    factKey: String?,
    value: String?,
    content: String,
): String = memorySearchTokens(
    listOf(subject, factKey.orEmpty(), value.orEmpty(), content)
        .joinToString(" "),
)

internal fun memoryImportance(
    kind: String,
    factKey: String?,
    content: String,
): Double {
    val boundary = "boundary" in factKey.orEmpty().lowercase() ||
        listOf("不要", "别", "不希望", "不喜欢", "不能", "不想")
            .any(content::contains)
    if (boundary) return 1.0
    return when (kind) {
        "user_report" -> 0.84
        "plan" -> 0.74
        "shared_event" -> 0.70
        "character_detail" -> 0.58
        "role_claim" -> 0.46
        "belief" -> 0.42
        else -> 0.50
    }
}

internal fun memoryConfidence(evidenceJson: String): Double {
    val evidenceCount = evidenceSourceIds(evidenceJson).size
    return (0.45 + 0.15 * evidenceCount).coerceIn(0.45, 0.95)
}

internal fun sameMemoryPayload(
    value: String?,
    content: String,
    otherValue: String?,
    otherContent: String,
): Boolean =
    if (value != null && otherValue != null) {
        normalizedMemoryText(value) == normalizedMemoryText(otherValue)
    } else {
        normalizedMemoryText(content) == normalizedMemoryText(otherContent)
    }

internal fun automaticOperation(
    requested: String,
    hasTarget: Boolean,
    samePayload: Boolean,
    explicitCorrection: Boolean,
    newerEvidence: Boolean,
): String {
    if (requested == "ignore") return "ignore"
    if (samePayload && hasTarget) return "merge"
    if (!hasTarget) return "add"
    return when {
        requested == "merge" -> "contested"
        requested == "supersede" && explicitCorrection && newerEvidence -> "supersede"
        requested == "contested" -> "contested"
        explicitCorrection && newerEvidence -> "supersede"
        else -> "contested"
    }
}

internal fun evidenceSourceIds(evidenceJson: String): Set<Long> =
    Regex("\"(?:message_id|id)\"\\s*:\\s*(\\d+)")
        .findAll(evidenceJson)
        .mapNotNull { it.groupValues.getOrNull(1)?.toLongOrNull() }
        .toSet()

internal fun evidenceUserSourceIds(evidenceJson: String): Set<Long> =
    runCatching {
        val evidence = org.json.JSONArray(evidenceJson)
        buildSet {
            for (index in 0 until evidence.length()) {
                val item = evidence.optJSONObject(index) ?: continue
                if (item.optString("role") != "user") continue
                val id = item.optLong(
                    "message_id",
                    item.optLong("id", -1),
                )
                if (id > 0) add(id)
            }
        }
    }.getOrDefault(emptySet())

internal fun coveredEvidenceIds(
    memories: List<StructuredMemoryEntity>,
    states: Set<String> = setOf("active", "superseded", "deleted"),
): Set<Long> = memories.asSequence()
    .filter { it.state in states }
    .flatMap { evidenceSourceIds(it.evidenceJson).asSequence() }
    .toSet()

internal fun evidenceHash(evidenceJson: String): String {
    val normalized = evidenceJson
        .replace(Regex("\\s+"), "")
        .replace("\"id\":", "\"message_id\":")
    return MessageDigest.getInstance("SHA-256")
        .digest(normalized.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

internal fun tombstoneMatches(
    characterId: String,
    evidenceHash: String,
    subject: String,
    kind: String,
    factKey: String?,
    value: String?,
    content: String,
    sourceIds: Set<Long>,
    userSourceIds: Set<Long>,
    prior: StructuredMemoryTombstoneEntity,
): Boolean {
    if (prior.characterId != characterId) return false
    if (prior.evidenceHash == evidenceHash) return true
    if (
        prior.subject != subject ||
        prior.kind != kind ||
        prior.factKey != factKey
    ) return false
    val valueMatches = if (value != null && prior.value != null) {
        normalizedMemoryText(value) == normalizedMemoryText(prior.value)
    } else {
        normalizedMemoryText(content) == normalizedMemoryText(prior.content) ||
            contentSimilarity(content, prior.content) >= 0.6
    }
    if (!valueMatches) return false
    val priorIds = prior.sourceMessageIds
        .split(',')
        .mapNotNull(String::toLongOrNull)
        .toSet()
    val priorMax = priorIds.maxOrNull() ?: 0L
    val newerUserStatement = userSourceIds.any { it > priorMax }
    return !newerUserStatement
}

internal fun contentSimilarity(left: String?, right: String?): Double {
    fun terms(text: String?): Set<String> {
        if (text.isNullOrBlank()) return emptySet()
        return Regex("[\\p{IsHan}]+|[a-zA-Z0-9]+")
            .findAll(text.lowercase())
            .flatMap { match ->
                val part = match.value
                if (part.length <= 2 || !part.first().isLetter() ||
                    part.first().code < 0x2e80
                ) {
                    sequenceOf(part)
                } else {
                    part.windowed(2).asSequence()
                }
            }
            .filter { it.length >= 2 }
            .toSet()
    }
    val leftTerms = terms(left)
    val rightTerms = terms(right)
    if (leftTerms.isEmpty() || rightTerms.isEmpty()) return 0.0
    return (
        leftTerms.intersect(rightTerms).size.toDouble() /
            minOf(leftTerms.size, rightTerms.size)
        )
}

internal fun selectExtractionFacts(
    memories: List<StructuredMemoryEntity>,
    query: String,
    limit: Int = 40,
): List<StructuredMemoryEntity> {
    if (limit <= 0) return emptyList()
    val queryTerms = run {
        Regex("[\\p{IsHan}]+|[a-zA-Z0-9]+")
            .findAll(query.lowercase())
            .flatMap { match ->
                val part = match.value
                if (part.length <= 2 || !part.first().isLetter() ||
                    part.first().code < 0x2e80
                ) {
                    sequenceOf(part)
                } else {
                    part.windowed(2).asSequence()
                }
            }
            .filter { it.length >= 2 }
            .toSet()
    }
    return memories.asSequence()
        .filter { it.state == "active" || it.state == "contested" }
        .map { memory ->
            val body = "${memory.factKey.orEmpty()} ${memory.value.orEmpty()} ${memory.content}"
            val overlap = queryTerms.count { it in body.lowercase() }
            Triple(overlap, memory.observedOrder, memory)
        }
        .sortedWith(
            compareByDescending<Triple<Int, Long, StructuredMemoryEntity>> {
                it.first
            }.thenByDescending { it.second }.thenByDescending { it.third.id },
        )
        .take(limit)
        .map { it.third }
        .toList()
}

internal data class StructuredMemoryWindow(
    val sources: List<MessageEntity>,
    val newBatch: List<MessageEntity>,
    val processedThrough: Long,
    val hasMore: Boolean,
)

internal fun selectMemoryWindow(
    history: List<MessageEntity>,
    cursor: Long,
    messageEnd: Long,
    limit: Int = 24,
): StructuredMemoryWindow? {
    if (limit <= 0) return null
    val eligible = history.filter { it.id <= messageEnd }
    val pending = eligible.filter { it.id > cursor }
    if (pending.isEmpty()) return null
    val limited = pending.take(limit)
    val lastAssistant = limited.indexOfLast { it.role == "assistant" }
        .takeIf { it >= 0 } ?: limited.lastIndex
    val newBatch = limited.take(lastAssistant + 1)
    if (newBatch.isEmpty()) return null
    val context = eligible.filter { it.id < newBatch.first().id }.takeLast(4)
    return StructuredMemoryWindow(
        sources = context + newBatch,
        newBatch = newBatch,
        processedThrough = newBatch.last().id,
        hasMore = newBatch.last().id < messageEnd,
    )
}
