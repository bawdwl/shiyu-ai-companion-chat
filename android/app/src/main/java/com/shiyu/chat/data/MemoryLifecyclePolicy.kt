package com.shiyu.chat.data

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

internal data class MemoryLifecycleSignals(
    val freshness: Double,
    val accessBoost: Double,
    val stalePlan: Boolean,
)

internal object MemoryLifecyclePolicy {
    private const val DAY_MS = 86_400_000.0

    fun signals(
        memory: StructuredMemoryEntity,
        now: Long = System.currentTimeMillis(),
    ): MemoryLifecycleSignals {
        val reference = max(memory.updatedAt, memory.lastAccessedAt ?: 0L)
            .takeIf { it > 0L }
            ?: memory.createdAt
        val ageDays = ((now - reference).coerceAtLeast(0L) / DAY_MS)
        val halfLifeDays = when (memory.factType) {
            "plan", "hypothesis" -> 30.0
            "opinion" -> 90.0
            "role_claim" -> 60.0
            "event" -> 365.0
            "preference", "fact" -> 180.0
            else -> 120.0
        }
        val freshness = exp(-ageDays / halfLifeDays).coerceIn(0.05, 1.0)
        val accessBoost = min(0.05, ln(1.0 + memory.accessCount.coerceAtLeast(0)) * 0.012)
        return MemoryLifecycleSignals(
            freshness = freshness,
            accessBoost = accessBoost,
            stalePlan = memory.factType in setOf("plan", "hypothesis") &&
                ageDays >= halfLifeDays * 2 &&
                !memory.pinned,
        )
    }

    /**
     * Exact duplicate groups only. Conflicting facts still go through the
     * evidence and correction pipeline instead of being silently discarded.
     */
    fun duplicateReplacements(
        memories: List<StructuredMemoryEntity>,
    ): List<Pair<StructuredMemoryEntity, StructuredMemoryEntity>> {
        val groups = memories
            .filter { it.state == "active" }
            .groupBy { memory ->
                listOf(
                    memory.subject,
                    memory.kind,
                    memory.factType,
                    normalizedMemoryText(memory.content),
                ).joinToString("|")
            }
        return buildList {
            groups.values.filter { it.size > 1 }.forEach { group ->
                val replacement = group.sortedWith(
                    compareByDescending<StructuredMemoryEntity> { it.pinned }
                        .thenByDescending { it.confidence }
                        .thenByDescending { it.observedOrder }
                        .thenByDescending { it.id },
                ).first()
                group.filter { it.id != replacement.id }.forEach { old ->
                    add(old to replacement)
                }
            }
        }
    }
}
