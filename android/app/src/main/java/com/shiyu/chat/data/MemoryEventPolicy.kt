package com.shiyu.chat.data

/**
 * Derive a small event timeline from already verified structured memories.
 * This avoids another model call and keeps event creation tied to evidence.
 */
internal object MemoryEventPolicy {
    data class Update(
        val topicKey: String,
        val title: String,
        val summary: String,
        val status: String,
        val realityMode: String,
        val participantsJson: String,
        val sourceMessageIds: String,
        val timelineJson: String,
        val reason: String,
    )

    fun topicKey(memory: StructuredMemoryEntity): String? {
        if (
            memory.kind !in setOf("plan", "shared_event") &&
            memory.factType != "event"
        ) return null
        val key = canonicalFactKey(memory.factKey, memory.subject) ?: return null
        val parts = key.split('.').filter(String::isNotBlank).toMutableList()
        if (parts.firstOrNull() in setOf("user", "character", "shared", "current", "profile")) {
            parts.removeAt(0)
        }
        if (parts.size < 2) return null
        return parts.dropLast(1).joinToString(".")
    }

    fun next(
        candidate: ParsedStructuredCandidate,
        existing: MemoryEventEntity?,
        interactionMode: String,
        now: Long = System.currentTimeMillis(),
    ): Update? {
        val memory = candidate.memory
        val topicKey = topicKey(memory) ?: return null
        val eventText = "${memory.factKey.orEmpty()} ${memory.value.orEmpty()} ${memory.content}"
        val cancelled = listOf("取消", "不去了", "没去", "去不了", "放弃")
            .any(eventText::contains)
        val completed = !cancelled && (
            memory.kind == "shared_event" ||
                memory.factType == "event" ||
                listOf("已经参加", "完成了", "结束了", "去过了", "回来了")
                    .any(eventText::contains)
            )
        val status = when {
            cancelled -> "cancelled"
            completed -> "completed"
            else -> "open"
        }
        val sourceIds = candidate.sourceIds.sorted()
        val mergedSources = (
            existing?.sourceMessageIds
                ?.split(',')
                ?.mapNotNull(String::trim)
                ?.mapNotNull(String::toLongOrNull)
                .orEmpty() + sourceIds
            ).distinct().sorted().joinToString(",")
        val previousTimeline = existing?.timelineJson
            ?.takeIf { it.isNotBlank() && it != "[]" }
        val timeline = if (
            previousTimeline?.contains("\"summary\":\"${escapeJson(memory.content)}\"") == true
        ) {
            previousTimeline
        } else {
            val entry = "{\"summary\":\"${escapeJson(memory.content)}\"," +
                "\"status\":\"$status\",\"message_ids\":[${sourceIds.joinToString(",")}]," +
                "\"recorded_at\":$now}"
            if (previousTimeline == null) {
                "[$entry]"
            } else {
                previousTimeline.removeSuffix("]") + ",$entry]"
            }
        }
        val participants = when (memory.subject) {
            "user" -> "[\"user\"]"
            "character" -> "[\"character\"]"
            else -> "[\"user\",\"character\"]"
        }
        return Update(
            topicKey = topicKey,
            title = existing?.title?.takeIf(String::isNotBlank)
                ?: memory.content.take(80),
            summary = memory.content,
            status = status,
            realityMode = if (interactionMode == "immersive") "story" else "daily",
            participantsJson = participants,
            sourceMessageIds = mergedSources,
            timelineJson = timeline.toString(),
            reason = when {
                existing == null -> "created_from_verified_memory"
                completed -> "completed_from_verified_event"
                else -> "updated_from_related_plan"
            },
        )
    }

    fun selectRelevant(
        events: List<MemoryEventEntity>,
        query: String,
        limit: Int = 3,
        includeRecentOpenFallback: Boolean = false,
    ): List<MemoryEventEntity> {
        if (limit <= 0 || events.isEmpty()) return emptyList()
        val normalized = normalizedMemoryText(query)
        val continuation = listOf(
            "后来",
            "然后",
            "上次",
            "那件事",
            "那个事",
            "怎么样了",
            "结果",
            "进展",
            "约定",
            "计划",
        ).any(query::contains)
        val historyRequested = listOf(
            "后来",
            "然后",
            "上次",
            "那件事",
            "那个事",
            "怎么样了",
            "结果",
            "进展",
            "完成",
            "结束",
            "取消",
            "去过",
            "参加",
        ).any(query::contains)
        val ranked = events.asSequence()
            .filter {
                it.status == "open" || historyRequested
            }
            .map { event ->
                val text = normalizedMemoryText("${event.title} ${event.summary}")
                val topicHits = eventTerms(normalized).count { it in text }
                event to topicHits
            }
            .filter { (_, hits) -> hits > 0 }
            .sortedWith(
                compareByDescending<Pair<MemoryEventEntity, Int>> { it.second }
                    .thenByDescending { it.first.lastMentionedAt ?: 0L }
                    .thenByDescending { it.first.updatedAt },
            )
            .take(limit)
            .map { it.first }
            .toList()
        if (ranked.isNotEmpty()) return ranked
        if (!continuation && !includeRecentOpenFallback) return emptyList()
        return events.asSequence()
            .filter { it.status == "open" }
            .sortedWith(
                compareByDescending<MemoryEventEntity> { it.updatedAt }
                    .thenByDescending { it.id },
            )
            .take(1)
            .toList()
    }

    private fun escapeJson(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")

    private fun eventTerms(text: String): Set<String> {
        val terms = linkedSetOf<String>()
        Regex("[\\p{IsHan}]+|[a-z0-9]+").findAll(text).forEach { match ->
            val part = match.value
            if (part.first().code in 0x3400..0x9fff) {
                if (part.length <= 3) {
                    terms += part
                } else {
                    for (size in 2..3) {
                        part.windowed(size).forEach(terms::add)
                    }
                }
            } else {
                terms += part
            }
        }
        return terms
    }
}
