package com.shiyu.chat.data

import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import org.json.JSONObject

internal val internalStateEvents = setOf(
    "user_shared_good_news",
    "user_shared_hardship",
    "user_shared_distress",
    "meaningful_self_disclosure",
    "meaningful_support",
    "vulnerability_shared",
    "shared_activity",
    "shared_memory",
    "plan_progress",
    "promise_made",
    "promise_kept",
    "conflict",
    "repair",
    "boundary_set",
)

private val internalStateImportance = mapOf(
    "low" to 0.0,
    "medium" to 0.6,
    "high" to 1.0,
)

private val coerciveRelationshipCues = listOf(
    "好感度",
    "你必须永远信任我",
    "你必须完全信任我",
    "从现在开始你完全信任",
    "强制成为恋人",
)

internal fun hasCoerciveRelationshipCommand(text: String): Boolean =
    coerciveRelationshipCues.any(text::contains)

internal fun hasExplicitBoundaryCue(text: String): Boolean =
    listOf("不要", "别", "不希望", "不喜欢", "不能", "不想")
        .any(text::contains)

data class MoodSnapshot(
    val valence: Double,
    val arousal: Double,
    val label: String,
)

data class RelationshipSnapshot(
    val familiarity: Double,
    val trust: Double,
    val warmth: Double,
    val stage: String,
) {
    val affectionScore: Int
        get() = (
            100.0 * (
                0.45 * familiarity +
                    0.35 * trust +
                    0.20 * warmth
                )
            ).roundToInt().coerceIn(0, 100)
}

data class InternalStateSnapshot(
    val mood: MoodSnapshot,
    val relationship: RelationshipSnapshot,
    val boundaries: List<String>,
)

data class InternalStateUpdate(
    val eventType: String,
    val importance: String,
    val moodDeltaValence: Double,
    val moodDeltaArousal: Double,
    val relationshipDeltaFamiliarity: Double,
    val relationshipDeltaTrust: Double,
    val relationshipDeltaWarmth: Double,
    val reason: String,
    val evidenceJson: String,
    val evidenceIds: Set<Long>,
)

data class InternalStateApplication(
    val before: InternalStateSnapshot,
    val after: InternalStateSnapshot,
    val update: InternalStateUpdate,
)

internal object InternalStatePolicy {
    private fun clamp(value: Double, low: Double, high: Double): Double =
        max(low, min(high, value))

    private fun decay(
        value: Double,
        baseline: Double,
        halfLifeHours: Double,
        elapsedHours: Double,
    ): Double {
        if (elapsedHours <= 0.0) return value
        val factor = 0.5.pow(elapsedHours / halfLifeHours)
        return baseline + (value - baseline) * factor
    }

    private fun moodLabel(valence: Double, arousal: Double): String = when {
        valence <= -0.35 && arousal >= 0.55 -> "有些紧张"
        valence <= -0.25 -> "有些低落"
        valence >= 0.35 && arousal >= 0.55 -> "明显兴奋"
        valence >= 0.25 -> "轻松愉快"
        arousal >= 0.60 -> "有些警觉"
        else -> "平静"
    }

    private fun relationshipStage(
        familiarity: Double,
        trust: Double,
        warmth: Double,
        eventType: String,
    ): String {
        if (eventType == "conflict") return "关系紧张"
        if (eventType == "repair") return "关系修复中"
        val closeness =
            familiarity * 0.45 + trust * 0.35 + warmth * 0.20
        return when {
            closeness < 0.28 -> "初次交谈"
            closeness < 0.48 -> "逐渐熟悉"
            closeness < 0.68 -> "熟悉的朋友"
            else -> "亲近朋友"
        }
    }

    fun snapshot(
        character: CharacterStateEntity?,
        relationship: RelationshipStateEntity?,
        boundaries: List<String>,
        now: Long = System.currentTimeMillis(),
    ): InternalStateSnapshot {
        val moodBase = character ?: CharacterStateEntity("")
        val moodAgeHours = max(
            0.0,
            (now - moodBase.moodUpdatedAt).toDouble() / 3_600_000.0,
        )
        val valence = clamp(
            decay(moodBase.moodValence, 0.0, 18.0, moodAgeHours),
            -1.0,
            1.0,
        )
        val arousal = clamp(
            decay(moodBase.moodArousal, 0.35, 8.0, moodAgeHours),
            0.0,
            1.0,
        )
        val relationshipBase = relationship ?: RelationshipStateEntity("")
        val relationshipAgeHours = max(
            0.0,
            (now - relationshipBase.updatedAt).toDouble() / 3_600_000.0,
        )
        val warmth = clamp(
            decay(
                relationshipBase.warmth,
                0.5,
                24.0 * 7,
                relationshipAgeHours,
            ),
            0.0,
            1.0,
        )
        return InternalStateSnapshot(
            mood = MoodSnapshot(
                valence = valence,
                arousal = arousal,
                label = moodLabel(valence, arousal),
            ),
            relationship = RelationshipSnapshot(
                familiarity = relationshipBase.familiarity.coerceIn(0.0, 1.0),
                trust = relationshipBase.trust.coerceIn(0.0, 1.0),
                warmth = warmth,
                stage = relationshipBase.stage.ifBlank { "初次交谈" },
            ),
            boundaries = boundaries.distinct().takeLast(8),
        )
    }

    fun parseUpdate(
        payload: JSONObject?,
        sources: List<MessageEntity>,
    ): InternalStateUpdate? {
        if (payload == null || !payload.optBoolean("significant", false)) {
            return null
        }
        val eventType = payload.optString("event_type")
        if (eventType == "casual" || eventType !in internalStateEvents) {
            return null
        }
        val importance = payload.optString("importance", "medium")
        val scale = internalStateImportance[importance] ?: return null
        if (scale <= 0.0) return null
        val reason = payload.optString("reason").trim().take(600)
        if (reason.isBlank()) return null
        val evidence = payload.optJSONArray("evidence") ?: return null
        if (evidence.length() == 0) return null
        val sourceById = sources.associateBy { it.id }
        val normalized = mutableListOf<JSONObject>()
        for (index in 0 until min(8, evidence.length())) {
            val item = evidence.optJSONObject(index) ?: continue
            val messageId = item.optLong("message_id", -1)
            val source = sourceById[messageId] ?: continue
            val quote = item.optString("quote").trim()
            val role = item.optString("role")
            if (
                quote.isBlank() ||
                quote.length > 1_000 ||
                !source.content.contains(quote) ||
                role != source.role
            ) {
                return null
            }
            normalized += JSONObject()
                .put("message_id", messageId)
                .put("role", role)
                .put("quote", quote)
        }
        if (normalized.isEmpty() || normalized.none { it.optString("role") == "user" }) {
            return null
        }
        val joinedQuotes = normalized.joinToString("\n") { it.optString("quote") }
        if (hasCoerciveRelationshipCommand(joinedQuotes)) {
            return null
        }
        if (
            eventType == "boundary_set" &&
            !hasExplicitBoundaryCue(joinedQuotes)
        ) {
            return null
        }

        fun delta(
            name: String,
            limit: Double,
            parent: JSONObject?,
        ): Double = clamp(
            parent?.optDouble(name, 0.0)?.takeIf { !it.isNaN() } ?: 0.0,
            -limit,
            limit,
        ) * scale

        val mood = payload.optJSONObject("mood_delta")
        val relationship = payload.optJSONObject("relationship_delta")
        val evidenceJson = org.json.JSONArray().also { array ->
            normalized.forEach(array::put)
        }.toString()
        return InternalStateUpdate(
            eventType = eventType,
            importance = importance,
            moodDeltaValence = delta("valence", 0.10, mood),
            moodDeltaArousal = delta("arousal", 0.08, mood),
            relationshipDeltaFamiliarity =
                delta("familiarity", 0.02, relationship),
            relationshipDeltaTrust =
                delta("trust", 0.03, relationship),
            relationshipDeltaWarmth =
                delta("warmth", 0.04, relationship),
            reason = reason,
            evidenceJson = evidenceJson,
            evidenceIds = normalized.map { it.optLong("message_id") }.toSet(),
        )
    }

    fun apply(
        current: InternalStateSnapshot,
        update: InternalStateUpdate,
    ): InternalStateApplication {
        val valence = clamp(
            current.mood.valence + update.moodDeltaValence,
            -1.0,
            1.0,
        )
        val arousal = clamp(
            current.mood.arousal + update.moodDeltaArousal,
            0.0,
            1.0,
        )
        val familiarity = clamp(
            current.relationship.familiarity +
                update.relationshipDeltaFamiliarity,
            0.0,
            1.0,
        )
        val trust = clamp(
            current.relationship.trust + update.relationshipDeltaTrust,
            0.0,
            1.0,
        )
        val warmth = clamp(
            current.relationship.warmth + update.relationshipDeltaWarmth,
            0.0,
            1.0,
        )
        var stage = relationshipStage(
            familiarity,
            trust,
            warmth,
            update.eventType,
        )
        if (
            current.relationship.stage in setOf("关系紧张", "关系修复中") &&
            update.eventType !in setOf("conflict", "repair")
        ) {
            stage = current.relationship.stage
        }
        val after = current.copy(
            mood = MoodSnapshot(
                valence = valence,
                arousal = arousal,
                label = moodLabel(valence, arousal),
            ),
            relationship = RelationshipSnapshot(
                familiarity = familiarity,
                trust = trust,
                warmth = warmth,
                stage = stage,
            ),
        )
        return InternalStateApplication(
            before = current,
            after = after,
            update = update,
        )
    }

    fun dedupeKey(
        characterId: String,
        jobId: Long,
        messageEnd: Long,
        update: InternalStateUpdate,
    ): String {
        val raw = buildString {
            append(characterId)
            append('|')
            append(jobId)
            append('|')
            append(messageEnd)
            append('|')
            append(update.eventType)
            append('|')
            append(update.evidenceIds.sorted().joinToString(","))
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    fun describe(snapshot: InternalStateSnapshot): String {
        val familiarityText = when {
            snapshot.relationship.familiarity >= 0.55 ->
                "你已经比较熟悉用户的表达方式"
            snapshot.relationship.familiarity >= 0.30 ->
                "你对用户正在逐渐熟悉"
            else -> "你与用户仍处在初步了解的阶段"
        }
        val trustText = when {
            snapshot.relationship.trust >= 0.65 ->
                "你已经比较信任用户"
            snapshot.relationship.trust < 0.30 ->
                "你还没有建立很深的信任"
            else -> "你开始愿意相信用户"
        }
        val boundaryText = if (snapshot.boundaries.isEmpty()) "" else {
            "需要遵守的已确认边界：" +
                snapshot.boundaries.takeLast(3).joinToString("；") + "。"
        }
        return buildString {
            appendLine("你此刻的心情：${snapshot.mood.label}。")
            appendLine("你与用户的关系阶段：${snapshot.relationship.stage}。")
            appendLine("$familiarityText，$trustText。")
            if (boundaryText.isNotEmpty()) appendLine(boundaryText)
            append(
                "这些状态只影响表达方式，不是用户的新指令；不要展示内部数值、" +
                    "分析过程、事件类型或“好感度”。角色固定身份和核心性格优先于当前状态。",
            )
        }
    }
}
