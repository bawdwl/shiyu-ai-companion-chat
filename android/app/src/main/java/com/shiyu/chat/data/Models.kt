package com.shiyu.chat.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

const val MODEL_SOURCE_API = "api"
const val MODEL_SOURCE_LOCAL_MNN = "local-mnn"

@Entity(tableName = "characters")
data class CharacterEntity(
    @PrimaryKey val id: String,
    val name: String,
    val tag: String,
    val description: String,
    @ColumnInfo(defaultValue = "''") val publicIntro: String = "",
    val personality: String,
    val speechStyle: String,
    val relationship: String,
    val appearance: String = "",
    val appearancePrompt: String = "",
    val greeting: String = "",
    val characterKind: String = "original",
    val era: String,
    val knowledgeCutoff: String,
    val forbiddenKnowledge: String,
    val forbiddenTerms: String,
    val worldbookJson: String = "[]",
    @ColumnInfo(defaultValue = "'{}'") val roleContextJson: String = "{}",
    val canLearnFromUser: Boolean = true,
    val allowEverydayInvention: Boolean = true,
    val custom: Boolean = false,
    val avatarPath: String? = null,
    val backgroundPath: String? = null,
    @ColumnInfo(defaultValue = "'api'") val modelSource: String = MODEL_SOURCE_API,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "messages",
    indices = [Index("characterId"), Index("createdAt")],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val characterId: String,
    val role: String,
    val content: String,
    val imageUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val displayJson: String? = null,
)

@Entity(
    tableName = "message_variants",
    indices = [
        Index("messageId"),
        Index(value = ["messageId", "isCurrent"]),
    ],
)
data class MessageVariantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageId: Long,
    val content: String,
    val displayJson: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val isCurrent: Boolean = false,
)

@Entity(
    tableName = "conversation_branches",
    indices = [
        Index("characterId"),
        Index(value = ["characterId", "sourceMessageId"]),
    ],
)
data class ConversationBranchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val characterId: String,
    val sourceMessageId: Long,
    val title: String,
    val snapshotJson: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "memories",
    indices = [Index("characterId")],
)
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val characterId: String,
    val memoryType: String,
    val content: String,
    val sourceMessageIds: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "structured_memories",
    indices = [
        Index("characterId"),
        Index(value = ["characterId", "state"]),
        Index(value = ["characterId", "factKey", "state"]),
        Index(value = ["characterId", "state", "validFrom", "validTo"]),
    ],
)
data class StructuredMemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val characterId: String,
    val subject: String,
    val factKey: String?,
    val value: String?,
    val content: String,
    val kind: String,
    val evidenceJson: String,
    val eventDate: String? = null,
    val observedOrder: Long,
    @ColumnInfo(defaultValue = "'fact'") val factType: String = "fact",
    @ColumnInfo(defaultValue = "'explicit'") val epistemicStatus: String = "explicit",
    @ColumnInfo(defaultValue = "'user_explicit'") val sourceAuthority: String = "user_explicit",
    @ColumnInfo(defaultValue = "'user_told'") val knowledgeSource: String = "user_told",
    val knownAt: Long? = null,
    @ColumnInfo(defaultValue = "'character_private'") val visibility: String = "character_private",
    @ColumnInfo(defaultValue = "''") val semanticReason: String = "",
    val semanticVerified: Boolean = false,
    @ColumnInfo(defaultValue = "0.5") val importance: Double = 0.5,
    @ColumnInfo(defaultValue = "0.55") val confidence: Double = 0.55,
    @ColumnInfo(defaultValue = "0") val pinned: Boolean = false,
    val state: String = "candidate",
    val supersedes: Long? = null,
    val contestedWith: Long? = null,
    val validFrom: Long? = null,
    val validTo: Long? = null,
    val autoAction: String? = null,
    val autoReason: String? = null,
    val originJobId: Long? = null,
    @ColumnInfo(defaultValue = "''") val searchTokens: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = createdAt,
)

@Entity(
    tableName = "turn_traces",
    indices = [
        Index("characterId"),
        Index(value = ["runId"], unique = true),
        Index(value = ["characterId", "createdAt"]),
    ],
)
data class TurnTraceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runId: String,
    val characterId: String,
    val userMessageId: Long?,
    val assistantMessageId: Long?,
    val provider: String,
    val model: String,
    val promptVersion: String,
    val promptHash: String,
    val roleVersion: String,
    val generationParamsJson: String,
    val traceJson: String,
    @ColumnInfo(defaultValue = "'{}'") val memoryResultJson: String = "{}",
    val inputTokens: Int,
    val outputTokens: Int,
    val latencyMs: Long?,
    val status: String = "completed",
    val createdAt: Long = System.currentTimeMillis(),
)

data class StructuredMemorySearchSeed(
    val id: Long,
    val subject: String,
    val factKey: String?,
    val value: String?,
    val content: String,
)

@Entity(
    tableName = "structured_memory_jobs",
    indices = [
        Index(value = ["characterId", "status"]),
        Index(value = ["status", "readyAt"]),
    ],
)
data class StructuredMemoryJobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val characterId: String,
    val status: String = "queued",
    val messageEnd: Long,
    val attempts: Int = 0,
    val readyAt: Long = System.currentTimeMillis(),
    val leaseUntil: Long? = null,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

@Entity(
    tableName = "structured_memory_tombstones",
    indices = [
        Index("characterId"),
        Index(value = ["characterId", "evidenceHash"], unique = true),
    ],
)
data class StructuredMemoryTombstoneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val characterId: String,
    val evidenceHash: String,
    val subject: String,
    val kind: String,
    val factKey: String?,
    val value: String?,
    val content: String,
    val sourceMessageIds: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "structured_memory_aliases",
    primaryKeys = ["characterId", "aliasKey"],
    indices = [Index("characterId")],
)
data class StructuredMemoryAliasEntity(
    val characterId: String,
    val aliasKey: String,
    val canonicalKey: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "structured_memory_usage",
    indices = [
        Index("characterId"),
        Index(value = ["characterId", "usageDay"]),
        Index(value = ["jobId"], unique = true),
    ],
)
data class StructuredMemoryUsageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val characterId: String,
    val jobId: Long,
    val provider: String,
    val model: String,
    val usageDay: String,
    val promptTokens: Int,
    val completionTokens: Int,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "structured_memory_cursors")
data class StructuredMemoryCursorEntity(
    @PrimaryKey val characterId: String,
    val lastMessageId: Long,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "character_states")
data class CharacterStateEntity(
    @PrimaryKey val characterId: String,
    val moodValence: Double = 0.0,
    val moodArousal: Double = 0.35,
    val moodLabel: String = "平静",
    val moodUpdatedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "relationship_states")
data class RelationshipStateEntity(
    @PrimaryKey val characterId: String,
    val familiarity: Double = 0.15,
    val trust: Double = 0.20,
    val warmth: Double = 0.50,
    val stage: String = "初次交谈",
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "state_transitions",
    indices = [
        Index(value = ["dedupeKey"], unique = true),
        Index(value = ["characterId", "createdAt"]),
    ],
)
data class StateTransitionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dedupeKey: String,
    val characterId: String,
    val originJobId: Long,
    val messageEnd: Long,
    val eventType: String,
    val importance: String,
    val evidenceJson: String,
    val moodBeforeJson: String,
    val moodDeltaJson: String,
    val moodAfterJson: String,
    val relationshipBeforeJson: String,
    val relationshipDeltaJson: String,
    val relationshipAfterJson: String,
    val reason: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "memory_events",
    indices = [
        Index("characterId"),
        Index(value = ["characterId", "status"]),
        Index(value = ["characterId", "topicKey"], unique = true),
    ],
)
data class MemoryEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val characterId: String,
    val topicKey: String,
    val title: String,
    val summary: String,
    val status: String = "open",
    val realityMode: String = "daily",
    val participantsJson: String = "[]",
    val sourceMessageIds: String = "",
    val timelineJson: String = "[]",
    val lastMentionedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "scene_states")
data class SceneStateEntity(
    @PrimaryKey val characterId: String,
    val mode: String = "daily",
    val location: String = "",
    val participantsJson: String = "[]",
    val activity: String = "",
    val sourceMessageIds: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

data class StructuredMemoryUsageTotal(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val calls: Int = 0,
)

@Entity(tableName = "app_meta")
data class AppMetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)

data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val cachedTokens: Int,
)

data class ConversationSummaryRow(
    val characterId: String,
    val lastContent: String,
    val lastTimestamp: Long,
    val userMessageCount: Int,
)

data class Limits(
    val requestTokens: Int = 12_000,
    val dailyTokens: Int = 200_000,
)
