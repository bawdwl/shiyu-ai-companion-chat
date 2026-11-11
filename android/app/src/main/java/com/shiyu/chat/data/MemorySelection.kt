package com.shiyu.chat.data

import kotlin.math.max

internal data class UnifiedMemorySelection(
    val structuredMemories: List<StructuredMemoryEntity>,
    val excerpts: List<MemoryEntity>,
    val charactersUsed: Int,
    val historicalQuery: Boolean,
    val retrievalMode: String,
)

/**
 * Optional hook for an on-device embedding model. Android currently ships no
 * BGE/ONNX weights; callers therefore pass null and retrieval deterministically
 * falls back to FTS4 plus lexical/phrase ranking.
 */
internal fun interface MemorySemanticProvider {
    /** Return one value in [0,1] for every input text, in the same order. */
    fun score(query: String, texts: List<String>): List<Double>
}

/** Rank same-character excerpts by text overlap for legacy memory browsing. */
internal fun selectRelevantMemories(
    memories: List<MemoryEntity>,
    query: String,
    limit: Int,
): List<MemoryEntity> {
    if (limit <= 0) return emptyList()
    val queryTerms = retrievalTerms(query.take(2_000))
    return memories.withIndex().sortedWith(
        compareByDescending<IndexedValue<MemoryEntity>> { entry ->
            memoryTermSimilarity(queryTerms, retrievalTerms(entry.value.content))
        }.thenByDescending { it.value.id }
            .thenBy { it.index },
    ).take(limit).map { it.value }
}

internal fun suppressCoveredExcerpts(
    memories: List<MemoryEntity>,
    structuredMemories: List<StructuredMemoryEntity>,
): List<MemoryEntity> {
    val covered = coveredEvidenceIds(structuredMemories)
    if (covered.isEmpty()) return memories
    return memories.filter { memory ->
        memory.sourceMessageIds
            .split(',')
            .mapNotNull(String::trim)
            .mapNotNull(String::toLongOrNull)
            .none { it in covered }
    }
}

private val structuredHistoryCues =
    listOf("原来", "原本", "一开始", "最初", "之前", "以前", "旧", "历史")

internal fun isHistoricalMemoryQuery(query: String): Boolean =
    structuredHistoryCues.any { it in query }

internal fun compactMemoryExcerptForPrompt(
    content: String,
    query: String,
    maxChars: Int = 260,
): String {
    require(maxChars >= 80)
    val compact = content.replace(Regex("\\s+"), " ").trim()
    if (compact.length <= maxChars) return compact
    val anchors = Regex("[\\p{IsHan}]{2,}|[a-z0-9]+")
        .findAll(query.lowercase())
        .flatMap { match ->
            val part = match.value
            if (part.first().code in 0x3400..0x9fff) {
                part.windowed(2).asSequence()
            } else {
                sequenceOf(part)
            }
        }
        .filter { it.length >= 2 }
        .distinct()
        .take(16)
        .toList()
    val lower = compact.lowercase()
    val anchorIndex = anchors
        .map(lower::indexOf)
        .filter { it >= 0 }
        .minOrNull()
    if (anchorIndex == null) {
        val headSize = maxChars * 2 / 3
        val tailSize = maxChars - headSize - 1
        return compact.take(headSize).trimEnd() +
            "…" +
            compact.takeLast(tailSize).trimStart()
    }
    val start = (anchorIndex - maxChars / 3)
        .coerceIn(0, compact.length - maxChars)
    val end = (start + maxChars).coerceAtMost(compact.length)
    return buildString {
        if (start > 0) append('…')
        append(compact.substring(start, end).trim())
        if (end < compact.length) append('…')
    }
}

internal fun selectStructuredMemories(
    memories: List<StructuredMemoryEntity>,
    query: String,
    limit: Int,
    ftsScores: Map<Long, Double> = emptyMap(),
    semanticProvider: MemorySemanticProvider? = null,
    includeCoreProfile: Boolean = false,
): List<StructuredMemoryEntity> = rankStructuredMemories(
    memories = memories,
    query = query,
    limit = limit,
    includeSuperseded = isHistoricalMemoryQuery(query),
    ftsScores = ftsScores,
    semanticProvider = semanticProvider,
    includeCoreProfile = includeCoreProfile,
).map { it.memory }

/**
 * Structured facts and raw excerpts compete through one deterministic budget.
 * The caller retrieves every row through the character-scoped DAO first, and
 * this function also enforces the scope when it is given mixed fixtures.
 */
internal fun selectUnifiedMemoryContext(
    characterId: String,
    structuredMemories: List<StructuredMemoryEntity>,
    excerpts: List<MemoryEntity>,
    query: String,
    maxFacts: Int = 8,
    maxExcerpts: Int = 4,
    maxCharacters: Int = 1_600,
    ftsScores: Map<Long, Double> = emptyMap(),
    semanticProvider: MemorySemanticProvider? = null,
): UnifiedMemorySelection {
    require(maxFacts >= 0 && maxExcerpts >= 0 && maxCharacters >= 0)
    val historical = isHistoricalMemoryQuery(query)
    val scopedFacts = structuredMemories.filter { it.characterId == characterId }
    val scopedExcerpts = suppressCoveredExcerpts(
        excerpts.filter { it.characterId == characterId },
        scopedFacts,
    )
    val rankedFacts = rankStructuredMemories(
        memories = scopedFacts,
        query = query,
        limit = max(1, maxFacts * 2),
        includeSuperseded = historical,
        ftsScores = ftsScores,
        semanticProvider = semanticProvider,
        includeCoreProfile = false,
    )
    val rankedExcerpts = rankExcerpts(
        excerpts = scopedExcerpts,
        query = query,
        limit = max(1, maxExcerpts * 2),
        semanticProvider = semanticProvider,
    )

    data class Candidate(
        val type: String,
        val fact: StructuredMemoryEntity? = null,
        val excerpt: MemoryEntity? = null,
        val text: String,
        val score: Double,
        val order: Long,
    )

    val candidates = buildList {
        rankedFacts.forEach {
            add(
                Candidate(
                    type = "fact",
                    fact = it.memory,
                    text = it.memory.content,
                    // Explicit structured facts win ties against raw excerpts.
                    score = it.score + 0.12,
                    order = it.memory.observedOrder,
                ),
            )
        }
        rankedExcerpts.forEach {
            val promptText = compactMemoryExcerptForPrompt(
                content = it.memory.content,
                query = query,
            )
            add(
                Candidate(
                    type = "excerpt",
                    excerpt = it.memory,
                    text = promptText,
                    score = it.score,
                    order = it.memory.id,
                ),
            )
        }
    }.sortedWith(
        compareByDescending<Candidate> { it.score }
            .thenByDescending { it.order },
    )

    val selectedFacts = mutableListOf<StructuredMemoryEntity>()
    val selectedExcerpts = mutableListOf<MemoryEntity>()
    val seen = mutableSetOf<String>()
    var used = 0
    for (candidate in candidates) {
        if (candidate.type == "fact" && selectedFacts.size >= maxFacts) continue
        if (candidate.type == "excerpt" && selectedExcerpts.size >= maxExcerpts) continue
        if (!seen.add(normalizedMemoryText(candidate.text))) continue
        val estimatedCost = candidate.text.length + 40
        if (used + estimatedCost > maxCharacters) continue
        used += estimatedCost
        if (candidate.fact != null) {
            selectedFacts += candidate.fact
        } else {
            selectedExcerpts += candidate.excerpt!!
        }
    }
    return UnifiedMemorySelection(
        structuredMemories = selectedFacts,
        excerpts = selectedExcerpts,
        charactersUsed = used,
        historicalQuery = historical,
        retrievalMode = if (semanticProvider == null) {
            "fts4_lexical"
        } else {
            "fts4_lexical_semantic"
        },
    )
}

private data class RankedFact(
    val memory: StructuredMemoryEntity,
    val score: Double,
)

private data class RankedExcerpt(
    val memory: MemoryEntity,
    val score: Double,
)

private fun rankStructuredMemories(
    memories: List<StructuredMemoryEntity>,
    query: String,
    limit: Int,
    includeSuperseded: Boolean,
    ftsScores: Map<Long, Double>,
    semanticProvider: MemorySemanticProvider?,
    includeCoreProfile: Boolean = false,
): List<RankedFact> {
    if (limit <= 0) return emptyList()
    val allowed = if (includeSuperseded) {
        setOf("active", "superseded")
    } else {
        setOf("active")
    }
    val eligible = memories.filter { it.state in allowed }
    if (eligible.isEmpty()) return emptyList()
    val queryTerms = retrievalTerms(query.take(2_000))
    val normalizedQuery = normalizedMemoryText(query)
    val semanticValues = safeSemanticValues(
        semanticProvider,
        query,
        eligible.map(::structuredSearchBody),
    )
    val maxOrder = eligible.maxOfOrNull { it.observedOrder } ?: 1L
    val allById = memories.associateBy { it.id }
    val scored = mutableMapOf<Long, RankedFact>()

    eligible.forEachIndexed { index, memory ->
        val body = structuredSearchBody(memory)
        val lexical = memoryTermSimilarity(queryTerms, retrievalTerms(body))
        val phrase = if (
            normalizedQuery.isNotEmpty() &&
            normalizedQuery in normalizedMemoryText(body)
        ) 1.0 else 0.0
        val fts = (ftsScores[memory.id] ?: 0.0).coerceIn(0.0, 1.0)
        val semantic = semanticValues.getOrElse(index) { 0.0 }.coerceIn(0.0, 1.0)
        if (fts <= 0.0 && lexical <= 0.0 && phrase <= 0.0 && semantic < 0.30) {
            return@forEachIndexed
        }
        val recency = memory.observedOrder.toDouble() / max(1L, maxOrder)
        val importance = memory.importance.coerceIn(0.0, 1.0)
        val confidence = memory.confidence.coerceIn(0.0, 1.0)
        val sourceQuality =
            0.55 * confidence + 0.45 * structuredSourceTrust(memory.kind)
        val score = if (semanticValues.isNotEmpty()) {
            0.44 * semantic +
                0.22 * fts +
                0.14 * lexical +
                0.06 * phrase +
                0.07 * importance +
                0.03 * sourceQuality +
                0.04 * recency
        } else {
            0.38 * fts +
                0.30 * lexical +
                0.12 * phrase +
                0.10 * importance +
                0.04 * sourceQuality +
                0.06 * recency
        }
        val stateBoost = when {
            memory.state == "active" -> 0.06
            includeSuperseded -> 0.04
            else -> 0.0
        }
        val finalScore = score + stateBoost
        if (finalScore > 0.0) {
            scored[memory.id] = RankedFact(memory, finalScore)
        }
    }

    if (includeCoreProfile) {
        eligible.asSequence()
            .filter {
                it.state == "active" &&
                    it.subject == "user" &&
                    it.kind == "user_report" &&
                    it.factType in setOf("fact", "preference")
            }
            .sortedWith(
                compareByDescending<StructuredMemoryEntity> { it.importance }
                    .thenByDescending { it.observedOrder },
            )
            .take(3)
            .forEach { memory ->
                if (memory.id !in scored) {
                    val recency = memory.observedOrder.toDouble() / max(1L, maxOrder)
                    scored[memory.id] = RankedFact(
                        memory,
                        0.16 + 0.06 * memory.importance.coerceIn(0.0, 1.0) +
                            0.03 * recency,
                    )
                }
            }
    }

    if (includeSuperseded) {
        val activeMatches = scored.values.filter { it.memory.state == "active" }
        for (active in activeMatches) {
            var predecessorId = active.memory.supersedes
            val seen = mutableSetOf<Long>()
            var previousScore = active.score
            while (predecessorId != null && seen.add(predecessorId)) {
                val predecessor = allById[predecessorId] ?: break
                if (predecessor.state == "superseded") {
                    previousScore = max(0.16, previousScore - 0.04)
                    scored.putIfAbsent(
                        predecessor.id,
                        RankedFact(predecessor, previousScore),
                    )
                }
                predecessorId = predecessor.supersedes
            }
        }
    }
    return scored.values.sortedWith(
        compareByDescending<RankedFact> { it.score }
            .thenByDescending { it.memory.observedOrder }
            .thenByDescending { it.memory.id },
    ).take(limit)
}

private fun rankExcerpts(
    excerpts: List<MemoryEntity>,
    query: String,
    limit: Int,
    semanticProvider: MemorySemanticProvider?,
): List<RankedExcerpt> {
    if (limit <= 0 || excerpts.isEmpty()) return emptyList()
    val queryTerms = retrievalTerms(query.take(2_000))
    val normalizedQuery = normalizedMemoryText(query)
    val semanticValues = safeSemanticValues(
        semanticProvider,
        query,
        excerpts.map { it.content },
    )
    val maxOrder = excerpts.maxOfOrNull { it.id } ?: 1L
    return excerpts.mapIndexedNotNull { index, memory ->
        val lexical = memoryTermSimilarity(queryTerms, retrievalTerms(memory.content))
        val phrase = if (
            normalizedQuery.isNotEmpty() &&
            normalizedQuery in normalizedMemoryText(memory.content)
        ) 1.0 else 0.0
        val semantic = semanticValues.getOrElse(index) { 0.0 }.coerceIn(0.0, 1.0)
        if (lexical <= 0.0 && phrase <= 0.0 && semantic < 0.30) return@mapIndexedNotNull null
        val recency = memory.id.toDouble() / max(1L, maxOrder)
        val sourceQuality = if (memory.memoryType == "user_edited") 0.92 else 0.72
        val score = if (semanticValues.isNotEmpty()) {
            0.44 * semantic +
                0.14 * lexical +
                0.06 * phrase +
                0.07 * sourceQuality +
                0.12 * recency
        } else {
            0.62 * lexical +
                0.16 * phrase +
                0.07 * sourceQuality +
                0.15 * recency
        }
        RankedExcerpt(memory, score)
    }.sortedWith(
        compareByDescending<RankedExcerpt> { it.score }
            .thenByDescending { it.memory.id },
    ).take(limit)
}

private fun safeSemanticValues(
    provider: MemorySemanticProvider?,
    query: String,
    texts: List<String>,
): List<Double> {
    if (provider == null || texts.isEmpty()) return emptyList()
    return runCatching { provider.score(query, texts) }
        .getOrNull()
        ?.takeIf { it.size == texts.size }
        ?: emptyList()
}

private fun structuredSearchBody(memory: StructuredMemoryEntity): String =
    "${memory.subject} ${memory.factKey.orEmpty()} ${memory.value.orEmpty()} ${memory.content}"

private fun structuredSourceTrust(kind: String): Double = when (kind) {
    "user_report" -> 1.0
    "shared_event" -> 0.86
    "plan" -> 0.82
    "character_detail" -> 0.72
    "role_claim" -> 0.52
    "belief" -> 0.58
    else -> 0.60
}

private fun retrievalTerms(text: String): Set<String> {
    val terms = linkedSetOf<String>()
    Regex("[\\p{IsHan}]+|[a-z0-9]+").findAll(text.lowercase()).forEach { match ->
        val part = match.value
        if (part.first().code in 0x3400..0x9fff) {
            if (part.length == 1) {
                terms += part
            } else {
                for (size in 2..3) {
                    if (part.length < size) continue
                    part.windowed(size).forEach(terms::add)
                }
            }
        } else {
            terms += part
        }
    }
    return terms
}

private fun memoryTermSimilarity(query: Set<String>, body: Set<String>): Double {
    if (query.isEmpty() || body.isEmpty()) return 0.0
    return query.intersect(body).size.toDouble() /
        max(1, minOf(query.size, body.size))
}
