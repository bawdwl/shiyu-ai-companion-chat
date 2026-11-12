package com.shiyu.chat.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface CharacterDao {
    @Query("SELECT * FROM characters ORDER BY createdAt")
    fun observeAll(): Flow<List<CharacterEntity>>

    @Query("SELECT * FROM characters ORDER BY createdAt")
    suspend fun getAll(): List<CharacterEntity>

    @Query("SELECT * FROM characters WHERE id=:id")
    suspend fun get(id: String): CharacterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(character: CharacterEntity)

    @Query("UPDATE characters SET avatarPath=:avatarPath WHERE id=:id")
    suspend fun updateAvatar(id: String, avatarPath: String?)

    @Query("UPDATE characters SET avatarPath=:avatarPath,backgroundPath=:backgroundPath WHERE id=:id")
    suspend fun updateArtwork(id: String, avatarPath: String?, backgroundPath: String?)

    @Query("UPDATE characters SET publicIntro=:publicIntro WHERE id=:id AND TRIM(publicIntro)=''")
    suspend fun fillMissingPublicIntro(id: String, publicIntro: String): Int

    @Query("DELETE FROM characters WHERE id=:id AND custom=1")
    suspend fun deleteCustom(id: String)

    @Query("DELETE FROM characters WHERE id=:id")
    suspend fun deleteAny(id: String): Int

    @Query("DELETE FROM characters WHERE id=:id")
    suspend fun deleteRetiredBuiltin(id: String): Int
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE characterId=:characterId ORDER BY id")
    fun observeForCharacter(characterId: String): Flow<List<MessageEntity>>

    @Query(
        """SELECT * FROM (
               SELECT * FROM messages
               WHERE characterId=:characterId
               ORDER BY id DESC LIMIT :limit
           ) ORDER BY id"""
    )
    fun observeRecentForCharacter(
        characterId: String,
        limit: Int,
    ): Flow<List<MessageEntity>>

    @Query(
        """SELECT * FROM (
               SELECT * FROM messages
               WHERE characterId=:characterId AND id<:beforeId
               ORDER BY id DESC LIMIT :limit
           ) ORDER BY id"""
    )
    suspend fun olderBefore(
        characterId: String,
        beforeId: Long,
        limit: Int,
    ): List<MessageEntity>

    @Query(
        """SELECT EXISTS(
               SELECT 1 FROM messages
               WHERE characterId=:characterId AND id<:beforeId
           )"""
    )
    suspend fun hasBefore(characterId: String, beforeId: Long): Boolean

    @Query("SELECT * FROM messages WHERE characterId=:characterId ORDER BY id")
    suspend fun getAllForCharacter(characterId: String): List<MessageEntity>

    @Query(
        """SELECT * FROM messages
           WHERE characterId=:characterId
             AND content LIKE '%' || :query || '%'
           ORDER BY id DESC LIMIT :limit"""
    )
    suspend fun search(
        characterId: String,
        query: String,
        limit: Int = 80,
    ): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id=:id AND characterId=:characterId")
    suspend fun get(characterId: String, id: Long): MessageEntity?

    @Query(
        """SELECT * FROM messages
           WHERE characterId=:characterId AND role='user' AND id<:assistantId
           ORDER BY id DESC LIMIT 1"""
    )
    suspend fun previousUserBefore(
        characterId: String,
        assistantId: Long,
    ): MessageEntity?

    @Query(
        """SELECT * FROM messages
           WHERE characterId=:characterId AND id<:beforeId
           ORDER BY id DESC LIMIT :limit"""
    )
    suspend fun recentBefore(
        characterId: String,
        beforeId: Long,
        limit: Int,
    ): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE characterId=:characterId ORDER BY id DESC LIMIT :limit")
    suspend fun recent(characterId: String, limit: Int): List<MessageEntity>

    @Query(
        """SELECT * FROM messages
           WHERE characterId=:characterId AND id>:afterId AND id<=:throughId
           ORDER BY id LIMIT :limit"""
    )
    suspend fun afterIdThrough(
        characterId: String,
        afterId: Long,
        throughId: Long,
        limit: Int,
    ): List<MessageEntity>

    @Query(
        """SELECT * FROM messages
           WHERE characterId=:characterId AND id<=:throughId
           ORDER BY id DESC LIMIT :limit"""
    )
    suspend fun throughDescending(
        characterId: String,
        throughId: Long,
        limit: Int,
    ): List<MessageEntity>

    @Query("SELECT COALESCE(MAX(id),0) FROM messages WHERE characterId=:characterId AND id<:messageId")
    suspend fun maxIdBefore(characterId: String, messageId: Long): Long

    @Insert
    suspend fun insert(message: MessageEntity): Long

    @Query("DELETE FROM messages WHERE characterId=:characterId")
    suspend fun clear(characterId: String)

    @Query("UPDATE messages SET content=:content,displayJson=:displayJson WHERE id=:id")
    suspend fun updateContent(
        id: Long,
        content: String,
        displayJson: String?,
    ): Int

    @Query("DELETE FROM messages WHERE characterId=:characterId AND id>=:messageId")
    suspend fun deleteFrom(characterId: String, messageId: Long): Int

    @Query("SELECT COUNT(*) FROM messages WHERE characterId=:characterId")
    suspend fun count(characterId: String): Int

    @Query(
        """SELECT m.characterId,
                  m.content AS lastContent,
                  m.createdAt AS lastTimestamp,
                  (SELECT COUNT(*) FROM messages u
                   WHERE u.characterId=m.characterId AND u.role='user') AS userMessageCount
           FROM messages m
           INNER JOIN (
               SELECT characterId,MAX(id) AS maxId
               FROM messages GROUP BY characterId
           ) latest ON latest.maxId=m.id
           ORDER BY m.createdAt DESC""",
    )
    fun observeConversationSummaries(): Flow<List<ConversationSummaryRow>>
}

@Dao
interface MessageVariantDao {
    @Query("SELECT * FROM message_variants WHERE messageId=:messageId ORDER BY id")
    fun observeForMessage(messageId: Long): Flow<List<MessageVariantEntity>>

    @Query("SELECT * FROM message_variants WHERE messageId=:messageId ORDER BY id")
    suspend fun forMessage(messageId: Long): List<MessageVariantEntity>

    @Query("SELECT * FROM message_variants WHERE id=:variantId AND messageId=:messageId")
    suspend fun get(messageId: Long, variantId: Long): MessageVariantEntity?

    @Insert
    suspend fun insert(variant: MessageVariantEntity): Long

    @Query("UPDATE message_variants SET isCurrent=0 WHERE messageId=:messageId")
    suspend fun clearCurrent(messageId: Long)

    @Query(
        "UPDATE message_variants SET isCurrent=1 WHERE id=:variantId AND messageId=:messageId",
    )
    suspend fun markCurrent(messageId: Long, variantId: Long): Int

    @Query("DELETE FROM message_variants WHERE messageId=:messageId")
    suspend fun clearForMessage(messageId: Long)

    @Query(
        """DELETE FROM message_variants
           WHERE messageId IN (
               SELECT id FROM messages WHERE characterId=:characterId
           )"""
    )
    suspend fun clearForCharacter(characterId: String)

    @Query(
        """DELETE FROM message_variants
           WHERE messageId IN (
               SELECT id FROM messages
               WHERE characterId=:characterId AND id>=:messageId
           )"""
    )
    suspend fun deleteFromMessage(characterId: String, messageId: Long)
}

@Dao
interface ConversationBranchDao {
    @Query(
        """SELECT * FROM conversation_branches
           WHERE characterId=:characterId ORDER BY createdAt DESC,id DESC""",
    )
    fun observeForCharacter(characterId: String): Flow<List<ConversationBranchEntity>>

    @Query("SELECT * FROM conversation_branches WHERE id=:id AND characterId=:characterId")
    suspend fun get(characterId: String, id: Long): ConversationBranchEntity?

    @Insert
    suspend fun insert(branch: ConversationBranchEntity): Long

    @Query("DELETE FROM conversation_branches WHERE id=:id AND characterId=:characterId")
    suspend fun delete(characterId: String, id: Long): Int

    @Query("DELETE FROM conversation_branches WHERE characterId=:characterId")
    suspend fun clear(characterId: String)
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories WHERE id=:id AND characterId=:characterId")
    suspend fun get(id: Long, characterId: String): MemoryEntity?

    @Query("SELECT COUNT(*) FROM memories WHERE characterId=:characterId AND sourceMessageIds=:sourceIds")
    suspend fun countSource(characterId: String, sourceIds: String): Int

    @Query("UPDATE memories SET content=:content, memoryType='user_edited' WHERE id=:id AND characterId=:characterId")
    suspend fun updateContent(id: Long, characterId: String, content: String): Int
    @Query("SELECT * FROM memories WHERE characterId=:characterId ORDER BY id DESC")
    fun observeForCharacter(characterId: String): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE characterId=:characterId ORDER BY id DESC LIMIT :limit")
    suspend fun recent(characterId: String, limit: Int): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE characterId=:characterId ORDER BY id")
    suspend fun allForCharacter(characterId: String): List<MemoryEntity>

    @Insert
    suspend fun insert(memory: MemoryEntity)

    @Query("SELECT COUNT(*) FROM memories WHERE characterId=:characterId AND content=:content")
    suspend fun countSame(characterId: String, content: String): Int

    @Query("DELETE FROM memories WHERE characterId=:characterId")
    suspend fun clear(characterId: String)

    @Query("DELETE FROM memories WHERE id=:id AND characterId=:characterId")
    suspend fun delete(id: Long, characterId: String)

    @Query("DELETE FROM memories WHERE id=:id AND characterId=:characterId")
    suspend fun deleteById(id: Long, characterId: String): Int
}

@Dao
interface MetaDao {
    @Query("SELECT value FROM app_meta WHERE `key`=:key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(value: AppMetaEntity)

    @Query("DELETE FROM app_meta WHERE substr(`key`, 1, length(:prefix)) = :prefix")
    suspend fun deletePrefix(prefix: String)
}

@Dao
interface StructuredMemoryDao {
    @Query("SELECT COUNT(*) FROM structured_memories WHERE characterId=:characterId")
    suspend fun countForCharacter(characterId: String): Int

    @Query("SELECT * FROM structured_memories WHERE characterId=:characterId ORDER BY observedOrder DESC,id DESC")
    fun observeForCharacter(characterId: String): Flow<List<StructuredMemoryEntity>>

    @Query("SELECT * FROM structured_memories WHERE characterId=:characterId ORDER BY observedOrder DESC,id DESC")
    suspend fun allForCharacter(characterId: String): List<StructuredMemoryEntity>

    @Query("SELECT * FROM structured_memories WHERE id=:id AND characterId=:characterId")
    suspend fun get(id: Long, characterId: String): StructuredMemoryEntity?

    @Query(
        """SELECT * FROM structured_memories
           WHERE characterId=:characterId AND id IN (:ids)"""
    )
    suspend fun byIds(
        characterId: String,
        ids: List<Long>,
    ): List<StructuredMemoryEntity>

    @Query(
        """SELECT * FROM structured_memories
           WHERE characterId=:characterId AND state='active'
           ORDER BY observedOrder DESC,id DESC LIMIT :limit"""
    )
    suspend fun recentActive(
        characterId: String,
        limit: Int,
    ): List<StructuredMemoryEntity>

    @Query(
        """SELECT * FROM structured_memories
           WHERE characterId=:characterId AND state='active' AND pinned=1
           ORDER BY observedOrder DESC,id DESC LIMIT :limit"""
    )
    suspend fun pinnedActive(
        characterId: String,
        limit: Int,
    ): List<StructuredMemoryEntity>

    @Query(
        """SELECT * FROM structured_memories
           WHERE characterId=:characterId AND state='active' AND (
             factKey LIKE '%boundary%'
             OR content LIKE '%不要%'
             OR content LIKE '%别%'
             OR content LIKE '%不希望%'
             OR content LIKE '%不喜欢%'
           )
           ORDER BY observedOrder DESC,id DESC LIMIT :limit"""
    )
    suspend fun boundaryCandidates(
        characterId: String,
        limit: Int,
    ): List<StructuredMemoryEntity>

    @Query(
        """SELECT * FROM structured_memories
           WHERE characterId=:characterId AND state='active'
             AND subject='user' AND kind='user_report'
             AND factType IN ('fact','preference')
           ORDER BY importance DESC,observedOrder DESC,id DESC
           LIMIT :limit"""
    )
    suspend fun coreProfileCandidates(
        characterId: String,
        limit: Int,
    ): List<StructuredMemoryEntity>

    @Query(
        """SELECT * FROM structured_memories
           WHERE characterId=:characterId AND kind=:kind AND subject=:subject
             AND factKey=:factKey AND state='active'
           ORDER BY observedOrder DESC,id DESC LIMIT 1"""
    )
    suspend fun activeByKey(
        characterId: String,
        kind: String,
        subject: String,
        factKey: String,
    ): StructuredMemoryEntity?

    @Insert
    suspend fun insert(memory: StructuredMemoryEntity): Long

    @Query(
        """UPDATE structured_memories
           SET evidenceJson=:evidenceJson,observedOrder=:observedOrder,
               semanticVerified=1,
               importance=MAX(importance,:importance),
               confidence=MIN(0.95,confidence+0.08),
               autoAction='merge',autoReason=:reason,updatedAt=:updatedAt
           WHERE id=:id AND characterId=:characterId"""
    )
    suspend fun mergeEvidence(
        id: Long,
        characterId: String,
        evidenceJson: String,
        observedOrder: Long,
        importance: Double,
        reason: String,
        updatedAt: Long,
    ): Int

    @Query(
        """UPDATE structured_memories
           SET state='superseded',autoAction='supersede',
               autoReason=:reason,updatedAt=:updatedAt,validTo=:updatedAt
           WHERE id=:id AND characterId=:characterId"""
    )
    suspend fun supersedeAutomatic(
        id: Long,
        characterId: String,
        reason: String,
        updatedAt: Long,
    ): Int

    @Query(
        """UPDATE structured_memories
           SET state='deleted',autoAction='delete',
               autoReason=:reason,updatedAt=:updatedAt,validTo=:updatedAt
           WHERE id=:id AND characterId=:characterId"""
    )
    suspend fun deleteAutomatic(
        id: Long,
        characterId: String,
        reason: String,
        updatedAt: Long,
    ): Int

    @Query(
        """UPDATE structured_memories
           SET pinned=:pinned,updatedAt=:updatedAt
           WHERE id=:id AND characterId=:characterId"""
    )
    suspend fun setPinned(
        id: Long,
        characterId: String,
        pinned: Boolean,
        updatedAt: Long,
    ): Int

    @Query(
        """UPDATE structured_memories
           SET state='active',content=:content,value=:value,
               semanticVerified=1,updatedAt=:updatedAt
           WHERE id=:id AND characterId=:characterId
             AND state IN ('active','candidate','contested','superseded')"""
    )
    suspend fun editContent(
        id: Long,
        characterId: String,
        content: String,
        value: String?,
        updatedAt: Long,
    ): Int

    @Query(
        """UPDATE structured_memories
           SET state='superseded',validTo=:updatedAt,updatedAt=:updatedAt
           WHERE id=:id AND characterId=:characterId AND state='active'"""
    )
    suspend fun markOutdated(
        id: Long,
        characterId: String,
        updatedAt: Long,
    ): Int

    @Query(
        """SELECT evidenceJson FROM structured_memories
           WHERE characterId=:characterId
             AND state IN ('active','superseded','deleted')"""
    )
    suspend fun coveredEvidenceJson(characterId: String): List<String>

    @Query(
        """SELECT id,subject,factKey,value,content FROM structured_memories
           WHERE searchTokens='' ORDER BY id LIMIT :limit"""
    )
    suspend fun searchTokenSeeds(limit: Int = 2_000): List<StructuredMemorySearchSeed>

    @Query("UPDATE structured_memories SET searchTokens=:tokens WHERE id=:id")
    suspend fun updateSearchTokens(id: Long, tokens: String): Int

    @Query("DELETE FROM structured_memories WHERE characterId=:characterId")
    suspend fun clear(characterId: String)

    @Query("DELETE FROM structured_memories WHERE id=:id AND characterId=:characterId")
    suspend fun deleteById(id: Long, characterId: String): Int
}

@Dao
interface StructuredMemoryJobDao {
    @Query(
        """SELECT * FROM structured_memory_jobs
           WHERE characterId=:characterId AND status='queued'
           ORDER BY id DESC LIMIT 1"""
    )
    suspend fun queuedForCharacter(characterId: String): StructuredMemoryJobEntity?

    @Insert
    suspend fun insert(job: StructuredMemoryJobEntity): Long

    @Query(
        """UPDATE structured_memory_jobs
           SET messageEnd=MAX(messageEnd,:messageEnd),readyAt=:readyAt,
               updatedAt=:updatedAt WHERE id=:id"""
    )
    suspend fun updateQueued(
        id: Long,
        messageEnd: Long,
        readyAt: Long,
        updatedAt: Long,
    )

    @Query(
        """SELECT * FROM structured_memory_jobs
           WHERE (status='queued' AND readyAt<=:now)
              OR (status='running' AND leaseUntil IS NOT NULL AND leaseUntil<:now)
           ORDER BY readyAt,id LIMIT 1"""
    )
    suspend fun readyOrExpired(now: Long): StructuredMemoryJobEntity?

    @Query(
        """SELECT * FROM structured_memory_jobs
           WHERE characterId=:characterId
             AND ((status='queued' AND readyAt<=:now)
               OR (status='running' AND leaseUntil IS NOT NULL AND leaseUntil<:now))
           ORDER BY readyAt,id LIMIT 1"""
    )
    suspend fun readyOrExpiredForCharacter(
        characterId: String,
        now: Long,
    ): StructuredMemoryJobEntity?

    @Query(
        """UPDATE structured_memory_jobs
           SET status='running',leaseUntil=:leaseUntil,updatedAt=:updatedAt
           WHERE id=:id"""
    )
    suspend fun markRunning(id: Long, leaseUntil: Long, updatedAt: Long)

    @Transaction
    suspend fun claimNext(
        now: Long,
        leaseUntil: Long,
        characterId: String? = null,
    ): StructuredMemoryJobEntity? {
        val job = if (characterId == null) {
            readyOrExpired(now)
        } else {
            readyOrExpiredForCharacter(characterId, now)
        } ?: return null
        markRunning(job.id, leaseUntil, now)
        return job.copy(status = "running", leaseUntil = leaseUntil, updatedAt = now)
    }

    @Query(
        """UPDATE structured_memory_jobs
           SET status='completed',leaseUntil=NULL,lastError=:detail,updatedAt=:updatedAt
           WHERE id=:id AND status='running'"""
    )
    suspend fun complete(id: Long, detail: String?, updatedAt: Long): Int

    @Query(
        """UPDATE structured_memory_jobs
           SET status='queued',readyAt=:readyAt,leaseUntil=NULL,lastError=:detail,
               updatedAt=:updatedAt WHERE id=:id AND status='running'"""
    )
    suspend fun defer(
        id: Long,
        readyAt: Long,
        detail: String?,
        updatedAt: Long,
    ): Int

    @Query(
        """UPDATE structured_memory_jobs
           SET status=:status,attempts=:attempts,readyAt=:readyAt,
               leaseUntil=NULL,lastError=:detail,updatedAt=:updatedAt
           WHERE id=:id AND status='running'"""
    )
    suspend fun retry(
        id: Long,
        status: String,
        attempts: Int,
        readyAt: Long,
        detail: String?,
        updatedAt: Long,
    ): Int

    @Query("SELECT COUNT(*) FROM structured_memory_jobs WHERE characterId=:characterId AND status=:status")
    suspend fun count(characterId: String, status: String): Int

    @Query("SELECT COUNT(*) FROM structured_memory_jobs WHERE status='queued'")
    suspend fun queuedCount(): Int

    @Query(
        """UPDATE structured_memory_jobs
           SET status='queued',leaseUntil=NULL,readyAt=:now,updatedAt=:now,
               lastError='recovered after app restart'
           WHERE status='running'"""
    )
    suspend fun recoverRunningJobs(now: Long)

    @Query(
        """SELECT DISTINCT characterId FROM structured_memory_jobs
           WHERE status='failed' AND (
               lastError LIKE 'Unterminated%' OR
               lastError LIKE '%JSONException%' OR
               lastError LIKE '%End of input%'
           )"""
    )
    suspend fun recoverableFailureCharacterIds(): List<String>

    @Query(
        """UPDATE structured_memory_jobs
           SET status='queued',attempts=0,readyAt=:now,leaseUntil=NULL,
               lastError='requeued after memory parser recovery',updatedAt=:now
           WHERE status='failed' AND (
               lastError LIKE 'Unterminated%' OR
               lastError LIKE '%JSONException%' OR
               lastError LIKE '%End of input%'
           )"""
    )
    suspend fun requeueRecoverableFailures(now: Long): Int

    @Query("DELETE FROM structured_memory_jobs WHERE characterId=:characterId")
    suspend fun clear(characterId: String)

    @Query("DELETE FROM structured_memory_jobs WHERE characterId=:characterId AND messageEnd>=:messageId")
    suspend fun deleteFromMessage(characterId: String, messageId: Long): Int
}

@Dao
interface TurnTraceDao {
    @Query(
        """SELECT * FROM turn_traces
           WHERE characterId=:characterId
           ORDER BY createdAt DESC,id DESC LIMIT :limit""",
    )
    fun observeForCharacter(
        characterId: String,
        limit: Int = 20,
    ): Flow<List<TurnTraceEntity>>

    @Query(
        """SELECT * FROM turn_traces
           WHERE characterId=:characterId
           ORDER BY createdAt DESC,id DESC LIMIT :limit""",
    )
    suspend fun recent(
        characterId: String,
        limit: Int = 20,
    ): List<TurnTraceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(trace: TurnTraceEntity): Long

    @Query(
        """UPDATE turn_traces SET memoryResultJson=:resultJson
           WHERE characterId=:characterId AND assistantMessageId=:assistantMessageId""",
    )
    suspend fun attachMemoryResult(
        characterId: String,
        assistantMessageId: Long,
        resultJson: String,
    ): Int

    @Query("DELETE FROM turn_traces WHERE characterId=:characterId")
    suspend fun clear(characterId: String)

    @Query(
        """DELETE FROM turn_traces WHERE characterId=:characterId
           AND (COALESCE(userMessageId,0)>=:messageId OR COALESCE(assistantMessageId,0)>=:messageId)"""
    )
    suspend fun deleteFromMessage(characterId: String, messageId: Long): Int
}

@Dao
interface StructuredMemorySupportDao {
    @Query("SELECT * FROM structured_memory_tombstones WHERE characterId=:characterId")
    suspend fun tombstones(characterId: String): List<StructuredMemoryTombstoneEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTombstone(tombstone: StructuredMemoryTombstoneEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAlias(alias: StructuredMemoryAliasEntity)

    @Query(
        """SELECT canonicalKey FROM structured_memory_aliases
           WHERE characterId=:characterId AND aliasKey=:aliasKey"""
    )
    suspend fun alias(characterId: String, aliasKey: String): String?

    @Query(
        """SELECT COALESCE(SUM(promptTokens),0) AS promptTokens,
                  COALESCE(SUM(completionTokens),0) AS completionTokens,
                  COUNT(*) AS calls
           FROM structured_memory_usage
           WHERE characterId=:characterId AND usageDay=:usageDay"""
    )
    suspend fun usageForDay(
        characterId: String,
        usageDay: String,
    ): StructuredMemoryUsageTotal

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUsage(usage: StructuredMemoryUsageEntity)

    @Query("SELECT * FROM structured_memory_cursors WHERE characterId=:characterId")
    suspend fun cursor(characterId: String): StructuredMemoryCursorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCursor(cursor: StructuredMemoryCursorEntity)

    @Query(
        """DELETE FROM structured_memory_usage
           WHERE characterId=:characterId AND jobId IN (
               SELECT id FROM structured_memory_jobs
               WHERE characterId=:characterId AND messageEnd>=:messageId
           )"""
    )
    suspend fun deleteUsageFromMessage(characterId: String, messageId: Long): Int

    @Query("DELETE FROM structured_memory_tombstones WHERE characterId=:characterId")
    suspend fun clearTombstones(characterId: String)

    @Query("DELETE FROM structured_memory_aliases WHERE characterId=:characterId")
    suspend fun clearAliases(characterId: String)

    @Query("DELETE FROM structured_memory_cursors WHERE characterId=:characterId")
    suspend fun clearCursor(characterId: String)
}

@Dao
interface InternalStateDao {
    @Query("SELECT * FROM character_states WHERE characterId=:characterId")
    suspend fun characterState(characterId: String): CharacterStateEntity?

    @Query("SELECT * FROM relationship_states WHERE characterId=:characterId")
    suspend fun relationshipState(characterId: String): RelationshipStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCharacterState(state: CharacterStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRelationshipState(state: RelationshipStateEntity)

    @Query("SELECT COUNT(*) FROM state_transitions WHERE dedupeKey=:dedupeKey")
    suspend fun transitionExists(dedupeKey: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransition(transition: StateTransitionEntity): Long

    @Query("SELECT COUNT(*) FROM state_transitions WHERE characterId=:characterId")
    suspend fun transitionTotal(characterId: String): Int

    @Query("DELETE FROM state_transitions WHERE characterId=:characterId")
    suspend fun clearTransitions(characterId: String)

    @Query("DELETE FROM character_states WHERE characterId=:characterId")
    suspend fun clearCharacterState(characterId: String)

    @Query("DELETE FROM relationship_states WHERE characterId=:characterId")
    suspend fun clearRelationshipState(characterId: String)

    @Query("SELECT * FROM state_transitions WHERE characterId=:characterId ORDER BY messageEnd,id")
    suspend fun transitionsForCharacter(characterId: String): List<StateTransitionEntity>

    @Query("DELETE FROM state_transitions WHERE characterId=:characterId AND messageEnd>=:messageId")
    suspend fun deleteTransitionsFromMessage(characterId: String, messageId: Long): Int
}

@Dao
interface MemoryEventDao {
    @Query("SELECT * FROM memory_events WHERE characterId=:characterId ORDER BY updatedAt DESC,id DESC")
    suspend fun forCharacter(characterId: String): List<MemoryEventEntity>

    @Query("SELECT * FROM memory_events WHERE characterId=:characterId AND topicKey=:topicKey LIMIT 1")
    suspend fun byTopicKey(characterId: String, topicKey: String): MemoryEventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(event: MemoryEventEntity): Long

    @Query("UPDATE memory_events SET status=:status,summary=:summary,timelineJson=:timelineJson,sourceMessageIds=:sourceMessageIds,updatedAt=:updatedAt WHERE id=:id AND characterId=:characterId")
    suspend fun update(
        id: Long,
        characterId: String,
        status: String,
        summary: String,
        timelineJson: String,
        sourceMessageIds: String,
        updatedAt: Long,
    ): Int

    @Query("UPDATE memory_events SET lastMentionedAt=:mentionedAt WHERE id=:id AND characterId=:characterId")
    suspend fun markMentioned(id: Long, characterId: String, mentionedAt: Long): Int

    @Query("DELETE FROM memory_events WHERE id=:id AND characterId=:characterId")
    suspend fun delete(id: Long, characterId: String): Int

    @Query("DELETE FROM memory_events WHERE characterId=:characterId")
    suspend fun clear(characterId: String): Int
}

@Dao
interface SceneStateDao {
    @Query("SELECT * FROM scene_states WHERE characterId=:characterId")
    suspend fun get(characterId: String): SceneStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: SceneStateEntity)

    @Query("DELETE FROM scene_states WHERE characterId=:characterId")
    suspend fun clear(characterId: String): Int
}

const val APP_DATABASE_VERSION = 20

@Database(
    entities = [
        CharacterEntity::class,
        MessageEntity::class,
        MessageVariantEntity::class,
        ConversationBranchEntity::class,
        MemoryEntity::class,
        StructuredMemoryEntity::class,
        StructuredMemoryJobEntity::class,
        StructuredMemoryTombstoneEntity::class,
        StructuredMemoryAliasEntity::class,
        StructuredMemoryUsageEntity::class,
        StructuredMemoryCursorEntity::class,
        CharacterStateEntity::class,
        RelationshipStateEntity::class,
        StateTransitionEntity::class,
        MemoryEventEntity::class,
        SceneStateEntity::class,
        TurnTraceEntity::class,
        AppMetaEntity::class,
    ],
    version = APP_DATABASE_VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun characters(): CharacterDao
    abstract fun messages(): MessageDao
    abstract fun messageVariants(): MessageVariantDao
    abstract fun conversationBranches(): ConversationBranchDao
    abstract fun memories(): MemoryDao
    abstract fun structuredMemories(): StructuredMemoryDao
    abstract fun structuredMemoryJobs(): StructuredMemoryJobDao
    abstract fun structuredMemorySupport(): StructuredMemorySupportDao
    abstract fun internalStates(): InternalStateDao
    abstract fun memoryEvents(): MemoryEventDao
    abstract fun sceneStates(): SceneStateDao
    abstract fun turnTraces(): TurnTraceDao
    abstract fun meta(): MetaDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE characters ADD COLUMN avatarPath TEXT")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS structured_memories(
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        characterId TEXT NOT NULL,
                        subject TEXT NOT NULL,
                        factKey TEXT,
                        value TEXT,
                        content TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        evidenceJson TEXT NOT NULL,
                        eventDate TEXT,
                        observedOrder INTEGER NOT NULL,
                        semanticVerified INTEGER NOT NULL,
                        state TEXT NOT NULL,
                        supersedes INTEGER,
                        createdAt INTEGER NOT NULL)""",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_structured_memories_characterId ON structured_memories(characterId)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_structured_memories_characterId_state ON structured_memories(characterId,state)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_structured_memories_characterId_factKey_state ON structured_memories(characterId,factKey,state)",
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE structured_memories ADD COLUMN contestedWith INTEGER")
                database.execSQL("ALTER TABLE structured_memories ADD COLUMN autoAction TEXT")
                database.execSQL("ALTER TABLE structured_memories ADD COLUMN autoReason TEXT")
                database.execSQL("ALTER TABLE structured_memories ADD COLUMN originJobId INTEGER")
                database.execSQL("ALTER TABLE structured_memories ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                database.execSQL("UPDATE structured_memories SET updatedAt=createdAt WHERE updatedAt=0")

                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS structured_memory_jobs(
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        characterId TEXT NOT NULL,
                        status TEXT NOT NULL,
                        messageEnd INTEGER NOT NULL,
                        attempts INTEGER NOT NULL,
                        readyAt INTEGER NOT NULL,
                        leaseUntil INTEGER,
                        lastError TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL)""",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_structured_memory_jobs_characterId_status ON structured_memory_jobs(characterId,status)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_structured_memory_jobs_status_readyAt ON structured_memory_jobs(status,readyAt)",
                )

                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS structured_memory_tombstones(
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        characterId TEXT NOT NULL,
                        evidenceHash TEXT NOT NULL,
                        subject TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        factKey TEXT,
                        value TEXT,
                        content TEXT NOT NULL,
                        sourceMessageIds TEXT NOT NULL,
                        createdAt INTEGER NOT NULL)""",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_structured_memory_tombstones_characterId ON structured_memory_tombstones(characterId)",
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_structured_memory_tombstones_characterId_evidenceHash ON structured_memory_tombstones(characterId,evidenceHash)",
                )

                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS structured_memory_aliases(
                        characterId TEXT NOT NULL,
                        aliasKey TEXT NOT NULL,
                        canonicalKey TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        PRIMARY KEY(characterId,aliasKey))""",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_structured_memory_aliases_characterId ON structured_memory_aliases(characterId)",
                )

                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS structured_memory_usage(
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        characterId TEXT NOT NULL,
                        jobId INTEGER NOT NULL,
                        provider TEXT NOT NULL,
                        model TEXT NOT NULL,
                        usageDay TEXT NOT NULL,
                        promptTokens INTEGER NOT NULL,
                        completionTokens INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL)""",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_structured_memory_usage_characterId ON structured_memory_usage(characterId)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_structured_memory_usage_characterId_usageDay ON structured_memory_usage(characterId,usageDay)",
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_structured_memory_usage_jobId ON structured_memory_usage(jobId)",
                )

                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS structured_memory_cursors(
                        characterId TEXT NOT NULL PRIMARY KEY,
                        lastMessageId INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL)""",
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS character_states(
                        characterId TEXT NOT NULL PRIMARY KEY,
                        moodValence REAL NOT NULL,
                        moodArousal REAL NOT NULL,
                        moodLabel TEXT NOT NULL,
                        moodUpdatedAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL)""",
                )
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS relationship_states(
                        characterId TEXT NOT NULL PRIMARY KEY,
                        familiarity REAL NOT NULL,
                        trust REAL NOT NULL,
                        warmth REAL NOT NULL,
                        stage TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL)""",
                )
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS state_transitions(
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        dedupeKey TEXT NOT NULL,
                        characterId TEXT NOT NULL,
                        originJobId INTEGER NOT NULL,
                        messageEnd INTEGER NOT NULL,
                        eventType TEXT NOT NULL,
                        importance TEXT NOT NULL,
                        evidenceJson TEXT NOT NULL,
                        moodBeforeJson TEXT NOT NULL,
                        moodDeltaJson TEXT NOT NULL,
                        moodAfterJson TEXT NOT NULL,
                        relationshipBeforeJson TEXT NOT NULL,
                        relationshipDeltaJson TEXT NOT NULL,
                        relationshipAfterJson TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        createdAt INTEGER NOT NULL)""",
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_state_transitions_dedupeKey ON state_transitions(dedupeKey)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_state_transitions_characterId_createdAt ON state_transitions(characterId,createdAt)",
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE structured_memories ADD COLUMN importance REAL NOT NULL DEFAULT 0.5",
                )
                database.execSQL(
                    "ALTER TABLE structured_memories ADD COLUMN confidence REAL NOT NULL DEFAULT 0.55",
                )
                database.execSQL(
                    "ALTER TABLE structured_memories ADD COLUMN validFrom INTEGER",
                )
                database.execSQL(
                    "ALTER TABLE structured_memories ADD COLUMN validTo INTEGER",
                )
                database.execSQL(
                    "ALTER TABLE structured_memories ADD COLUMN searchTokens TEXT NOT NULL DEFAULT ''",
                )
                database.execSQL(
                    """UPDATE structured_memories
                       SET importance=CASE
                           WHEN kind='user_report' THEN 0.84
                           WHEN kind='plan' THEN 0.74
                           WHEN kind='shared_event' THEN 0.70
                           WHEN kind='character_detail' THEN 0.58
                           WHEN kind='belief' THEN 0.42
                           ELSE 0.50 END""",
                )
                database.execSQL(
                    """UPDATE structured_memories
                       SET validFrom=createdAt
                       WHERE state IN ('active','superseded')""",
                )
                database.execSQL(
                    """UPDATE structured_memories
                       SET validTo=updatedAt
                       WHERE state IN ('superseded','deleted')""",
                )
                database.execSQL(
                    """CREATE INDEX IF NOT EXISTS
                       index_structured_memories_characterId_state_validFrom_validTo
                       ON structured_memories(characterId,state,validFrom,validTo)""",
                )
                MemorySearchIndex.createStatements.forEach(database::execSQL)
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE messages ADD COLUMN displayJson TEXT",
                )
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE characters ADD COLUMN greeting TEXT NOT NULL DEFAULT ''",
                )
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE characters ADD COLUMN appearance TEXT NOT NULL DEFAULT ''",
                )
                database.execSQL(
                    "ALTER TABLE characters ADD COLUMN appearancePrompt TEXT NOT NULL DEFAULT ''",
                )
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE structured_memories ADD COLUMN factType TEXT NOT NULL DEFAULT 'fact'",
                )
                database.execSQL(
                    "ALTER TABLE structured_memories ADD COLUMN epistemicStatus TEXT NOT NULL DEFAULT 'explicit'",
                )
                database.execSQL(
                    "ALTER TABLE structured_memories ADD COLUMN sourceAuthority TEXT NOT NULL DEFAULT 'user_explicit'",
                )
                database.execSQL(
                    "ALTER TABLE structured_memories ADD COLUMN knowledgeSource TEXT NOT NULL DEFAULT 'user_told'",
                )
                database.execSQL(
                    "ALTER TABLE structured_memories ADD COLUMN knownAt INTEGER",
                )
                database.execSQL(
                    "ALTER TABLE structured_memories ADD COLUMN visibility TEXT NOT NULL DEFAULT 'character_private'",
                )
                database.execSQL(
                    "ALTER TABLE structured_memories ADD COLUMN semanticReason TEXT NOT NULL DEFAULT ''",
                )
                database.execSQL(
                    """UPDATE structured_memories SET
                           factType=CASE
                               WHEN kind='shared_event' THEN 'event'
                               WHEN kind='plan' THEN 'plan'
                               WHEN kind='belief' THEN 'opinion'
                               WHEN kind='character_detail' THEN 'role_claim'
                               ELSE 'fact' END,
                           sourceAuthority=CASE
                               WHEN kind='character_detail' THEN 'character_self_report'
                               WHEN kind='shared_event' THEN 'shared_event'
                               ELSE 'user_explicit' END,
                           knowledgeSource=CASE
                               WHEN kind='character_detail' THEN 'character_told'
                               WHEN kind='shared_event' THEN 'observed'
                               ELSE 'user_told' END,
                           knownAt=COALESCE(knownAt,createdAt)""",
                )
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS turn_traces(
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        runId TEXT NOT NULL,
                        characterId TEXT NOT NULL,
                        userMessageId INTEGER,
                        assistantMessageId INTEGER,
                        provider TEXT NOT NULL,
                        model TEXT NOT NULL,
                        promptVersion TEXT NOT NULL,
                        promptHash TEXT NOT NULL,
                        roleVersion TEXT NOT NULL,
                        generationParamsJson TEXT NOT NULL,
                        traceJson TEXT NOT NULL,
                        memoryResultJson TEXT NOT NULL DEFAULT '{}',
                        inputTokens INTEGER NOT NULL,
                        outputTokens INTEGER NOT NULL,
                        latencyMs INTEGER,
                        status TEXT NOT NULL,
                        createdAt INTEGER NOT NULL)""",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_turn_traces_characterId ON turn_traces(characterId)",
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_turn_traces_runId ON turn_traces(runId)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_turn_traces_characterId_createdAt ON turn_traces(characterId,createdAt)",
                )
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE characters ADD COLUMN modelSource TEXT NOT NULL DEFAULT 'api'",
                )
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE characters ADD COLUMN backgroundPath TEXT",
                )
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE characters ADD COLUMN publicIntro TEXT NOT NULL DEFAULT ''",
                )
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE characters ADD COLUMN roleContextJson TEXT NOT NULL DEFAULT '{}'",
                )
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS memory_events(
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        characterId TEXT NOT NULL,
                        topicKey TEXT NOT NULL,
                        title TEXT NOT NULL,
                        summary TEXT NOT NULL,
                        status TEXT NOT NULL,
                        realityMode TEXT NOT NULL,
                        participantsJson TEXT NOT NULL,
                        sourceMessageIds TEXT NOT NULL,
                        timelineJson TEXT NOT NULL,
                        lastMentionedAt INTEGER,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_memory_events_characterId ON memory_events(characterId)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_memory_events_characterId_status ON memory_events(characterId,status)",
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_memory_events_characterId_topicKey ON memory_events(characterId,topicKey)",
                )
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS scene_states(
                        characterId TEXT PRIMARY KEY NOT NULL,
                        mode TEXT NOT NULL,
                        location TEXT NOT NULL,
                        participantsJson TEXT NOT NULL,
                        activity TEXT NOT NULL,
                        sourceMessageIds TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE structured_memories ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS message_variants(
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        messageId INTEGER NOT NULL,
                        content TEXT NOT NULL,
                        displayJson TEXT,
                        createdAt INTEGER NOT NULL,
                        isCurrent INTEGER NOT NULL DEFAULT 0)""".trimIndent(),
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_message_variants_messageId ON message_variants(messageId)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_message_variants_messageId_isCurrent ON message_variants(messageId,isCurrent)",
                )
            }
        }

        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS conversation_branches(
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        characterId TEXT NOT NULL,
                        sourceMessageId INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        snapshotJson TEXT NOT NULL,
                        createdAt INTEGER NOT NULL)""".trimIndent(),
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_conversation_branches_characterId ON conversation_branches(characterId)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_conversation_branches_characterId_sourceMessageId ON conversation_branches(characterId,sourceMessageId)",
                )
            }
        }

        val ALL_MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10,
            MIGRATION_10_11,
            MIGRATION_11_12,
            MIGRATION_12_13,
            MIGRATION_13_14,
            MIGRATION_14_15,
            MIGRATION_15_16,
            MIGRATION_16_17,
            MIGRATION_17_18,
            MIGRATION_18_19,
            MIGRATION_19_20,
        )

        val CREATE_SEARCH_INDEX_CALLBACK = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                MemorySearchIndex.createStatements.forEach(db::execSQL)
            }
        }
    }
}
