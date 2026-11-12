package com.shiyu.chat.data

import android.content.SharedPreferences
import android.database.Cursor
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

internal class ConversationBranchStore(
    private val database: AppDatabase,
    private val prefs: SharedPreferences,
) {
    fun observe(characterId: String): Flow<List<ConversationBranchEntity>> =
        database.conversationBranches().observeForCharacter(characterId)

    suspend fun create(
        characterId: String,
        sourceMessageId: Long,
        title: String = "消息 #$sourceMessageId 的检查点",
    ): ConversationBranchEntity {
        val source = database.messages().get(characterId, sourceMessageId)
            ?: error("找不到要创建分支的消息")
        return database.withTransaction {
            val snapshot = capture(characterId, sourceMessageId)
            val branch = ConversationBranchEntity(
                characterId = characterId,
                sourceMessageId = sourceMessageId,
                title = title.take(80),
                snapshotJson = snapshot.toString(),
            )
            val id = database.conversationBranches().insert(branch)
            branch.copy(id = id)
        }
    }

    suspend fun switchTo(characterId: String, branchId: Long) {
        database.withTransaction {
            val branch = database.conversationBranches().get(characterId, branchId)
                ?: error("分支不存在")
            val activeKey = activeBranchMetaKey(characterId)
            val activeId = database.meta().get(activeKey)?.toLongOrNull()
            if (activeId == branchId) return@withTransaction
            val latestMessageId = database.messages()
                .recent(characterId, 1)
                .firstOrNull()
                ?.id
            if (latestMessageId != null && latestMessageId > 0) {
                val snapshot = capture(characterId, latestMessageId).toString()
                val activeBranch = activeId?.let {
                    database.conversationBranches().get(characterId, it)
                }
                if (activeBranch != null) {
                    database.conversationBranches().updateSnapshot(
                        characterId,
                        activeBranch.id,
                        snapshot,
                    )
                } else {
                    val saved = database.conversationBranches().insert(
                        ConversationBranchEntity(
                            characterId = characterId,
                            sourceMessageId = latestMessageId,
                            title = "切换前自动保存",
                            snapshotJson = snapshot,
                        ),
                    )
                    database.meta().put(
                        AppMetaEntity(activeKey, saved.toString()),
                    )
                }
            }
            restore(characterId, branch)
            database.meta().put(AppMetaEntity(activeKey, branchId.toString()))
            bumpConversationEpoch(database, characterId)
            clearWorldbookState(characterId)
        }
    }

    suspend fun delete(characterId: String, branchId: Long) {
        check(database.conversationBranches().delete(characterId, branchId) == 1) {
            "分支不存在"
        }
        if (database.meta().get(activeBranchMetaKey(characterId)) == branchId.toString()) {
            database.meta().deletePrefix(activeBranchMetaKey(characterId))
        }
    }

    private fun capture(characterId: String, sourceMessageId: Long): JSONObject {
        val db = database.openHelper.writableDatabase
        val tables = JSONObject()
        tables.put("messages", readRows(
            db = db,
            table = "messages",
            selection = "characterId=? AND id<=?",
            args = arrayOf(characterId, sourceMessageId.toString()),
        ))
        tables.put("message_variants", readRows(
            db = db,
            table = "message_variants",
            selection = """messageId IN (
                SELECT id FROM messages WHERE characterId=? AND id<=?
            )""",
            args = arrayOf(characterId, sourceMessageId.toString()),
        ))
        for (table in setOf(
            "memories",
            "structured_memories",
            "structured_memory_tombstones",
            "structured_memory_aliases",
            "memory_events",
            "scene_states",
        )) {
            val rows = readRows(
                db,
                table,
                "characterId=?",
                arrayOf(characterId),
            ).filterSourceThrough(table, sourceMessageId)
            tables.put(table, rows)
        }
        tables.put("turn_traces", readRows(
            db = db,
            table = "turn_traces",
            selection = """characterId=? AND
                (userMessageId IS NULL OR userMessageId<=?) AND
                (assistantMessageId IS NULL OR assistantMessageId<=?)""",
            args = arrayOf(
                characterId,
                sourceMessageId.toString(),
                sourceMessageId.toString(),
            ),
        ))
        return JSONObject()
            .put("formatVersion", 1)
            .put("sourceMessageId", sourceMessageId)
            .put("tables", tables)
    }

    private suspend fun restore(
        characterId: String,
        branch: ConversationBranchEntity,
    ) {
        val root = JSONObject(branch.snapshotJson)
        require(root.optInt("formatVersion") == 1) { "分支格式版本不受支持" }
        val tables = root.getJSONObject("tables")
        clearConversationData(characterId)
        RESTORE_TABLES.forEach { table ->
            val rows = tables.optJSONArray(table) ?: JSONArray()
            for (index in 0 until rows.length()) {
                insertRow(table, rows.getJSONObject(index))
            }
        }
        database.structuredMemorySupport().upsertCursor(
            StructuredMemoryCursorEntity(
                characterId = characterId,
                lastMessageId = branch.sourceMessageId,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun clearConversationData(characterId: String) {
        database.messageVariants().clearForCharacter(characterId)
        database.messages().clear(characterId)
        database.memories().clear(characterId)
        database.structuredMemories().clear(characterId)
        database.structuredMemorySupport().clearTombstones(characterId)
        database.structuredMemorySupport().clearAliases(characterId)
        database.structuredMemoryJobs().clear(characterId)
        database.structuredMemorySupport().clearCursor(characterId)
        database.memoryEvents().clear(characterId)
        database.sceneStates().clear(characterId)
        database.turnTraces().clear(characterId)
    }

    fun clearWorldbookState(characterId: String) {
        val prefix = worldbookStatePrefix(characterId)
        val editor = prefs.edit()
        prefs.all.keys
            .filter { it.startsWith(prefix) }
            .forEach(editor::remove)
        editor.apply()
    }

    private fun readRows(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        table: String,
        selection: String,
        args: Array<String>,
    ): JSONArray = db.query(
        "SELECT * FROM $table WHERE $selection",
        args,
    ).use { cursor ->
        JSONArray().also { rows ->
            while (cursor.moveToNext()) {
                rows.put(cursor.toJson())
            }
        }
    }

    private fun Cursor.toJson(): JSONObject {
        val row = JSONObject()
        for (index in 0 until columnCount) {
            val name = getColumnName(index)
            when (getType(index)) {
                Cursor.FIELD_TYPE_NULL -> row.put(name, JSONObject.NULL)
                Cursor.FIELD_TYPE_INTEGER -> row.put(name, getLong(index))
                Cursor.FIELD_TYPE_FLOAT -> row.put(name, getDouble(index))
                Cursor.FIELD_TYPE_STRING -> row.put(name, getString(index))
                Cursor.FIELD_TYPE_BLOB -> row.put(
                    name,
                    Base64.getEncoder().encodeToString(getBlob(index)),
                )
                else -> row.put(name, JSONObject.NULL)
            }
        }
        return row
    }

    private fun JSONArray.filterSourceThrough(
        table: String,
        sourceMessageId: Long,
    ): JSONArray = JSONArray().also { output ->
        for (index in 0 until length()) {
            val row = getJSONObject(index)
            val sourceIds = when (table) {
                "structured_memories" -> evidenceSourceIds(row.optString("evidenceJson"))
                "memories", "structured_memory_tombstones" ->
                    numericIds(row.optString("sourceMessageIds"))
                "memory_events", "scene_states" ->
                    numericIds(row.optString("sourceMessageIds"))
                else -> emptySet()
            }
            if (sourceIds.isEmpty() || sourceIds.maxOrNull()!! <= sourceMessageId) {
                output.put(row)
            }
        }
    }

    private fun numericIds(value: String): Set<Long> =
        Regex("\\d+")
            .findAll(value)
            .mapNotNull { it.value.toLongOrNull() }
            .toSet()

    private fun insertRow(table: String, row: JSONObject) {
        val columns = row.keys().asSequence().toList()
        require(columns.isNotEmpty()) { "分支表 $table 包含空行" }
        val placeholders = columns.joinToString(",") { "?" }
        val sql = "INSERT INTO $table (${columns.joinToString(",")}) VALUES ($placeholders)"
        val args = columns.map { column ->
            val value = row.get(column)
            if (value == JSONObject.NULL) null else value
        }.toTypedArray()
        database.openHelper.writableDatabase.execSQL(sql, args)
    }

    companion object {
        private val RESTORE_TABLES = listOf(
            "messages",
            "message_variants",
            "memories",
            "structured_memories",
            "structured_memory_tombstones",
            "structured_memory_aliases",
            "memory_events",
            "scene_states",
            "turn_traces",
        )
    }
}

private fun worldbookStatePrefix(characterId: String): String =
    "worldbook-state:${characterId.length}:$characterId:"
