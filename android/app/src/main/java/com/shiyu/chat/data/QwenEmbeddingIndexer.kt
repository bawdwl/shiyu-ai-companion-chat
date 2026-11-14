package com.shiyu.chat.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

class QwenEmbeddingIndexer(
    private val database: AppDatabase,
    private val runtime: QwenEmbeddingRuntime,
) {
    suspend fun indexCharacter(
        characterId: String,
        limit: Int = 96,
    ): Int = withContext(Dispatchers.Default) {
        if (!runtime.isReady()) return@withContext 0
        val memories = database.structuredMemories()
            .allForCharacter(characterId)
            .filter { it.state != "deleted" }
        if (memories.isEmpty()) return@withContext 0
        val existing = database.memoryEmbeddings()
            .forCharacter(
                characterId,
                QwenEmbeddingModelManager.MODEL_VERSION,
                SOURCE_TYPE,
            )
            .associateBy { it.sourceId }
        val pending = memories
            .asSequence()
            .map { memory ->
                val text = structuredEmbeddingText(memory)
                memory to (text to sha256Hex(text.toByteArray(Charsets.UTF_8)))
            }
            .filter { (memory, payload) ->
                existing[memory.id]?.contentHash != payload.second
            }
            .take(limit.coerceAtLeast(1))
            .toList()
        if (pending.isEmpty()) return@withContext 0

        val records = pending.map { (memory, payload) ->
            val vector = runtime.embedDocument(payload.first)
            MemoryEmbeddingEntity(
                sourceType = SOURCE_TYPE,
                sourceId = memory.id,
                modelVersion = QwenEmbeddingModelManager.MODEL_VERSION,
                characterId = characterId,
                dimension = vector.size,
                contentHash = payload.second,
                vector = encodeVector(vector),
                updatedAt = System.currentTimeMillis(),
            )
        }
        database.memoryEmbeddings().upsertAll(records)
        records.size
    }

    suspend fun indexAllCharacters(limitPerCharacter: Int = 96): Int =
        withContext(Dispatchers.Default) {
            if (!runtime.isReady()) return@withContext 0
            var total = 0
            database.characters().getAll().forEach { character ->
                total += indexCharacter(character.id, limitPerCharacter)
            }
            total
        }

    suspend fun semanticScores(
        characterId: String,
        memories: List<StructuredMemoryEntity>,
        query: String,
    ): Map<Long, Double> = withContext(Dispatchers.Default) {
        if (!runtime.isReady() || memories.isEmpty() || query.isBlank()) {
            return@withContext emptyMap()
        }
        val expectedHashes = memories.associate { memory ->
            memory.id to sha256Hex(
                structuredEmbeddingText(memory).toByteArray(Charsets.UTF_8),
            )
        }
        val records = database.memoryEmbeddings().forStructuredIds(
            characterId = characterId,
            modelVersion = QwenEmbeddingModelManager.MODEL_VERSION,
            sourceType = SOURCE_TYPE,
            sourceIds = expectedHashes.keys.toList(),
        )
        if (records.isEmpty()) return@withContext emptyMap()
        val queryVector = runtime.embedQuery(query)
        buildMap {
            records.forEach { record ->
                if (expectedHashes[record.sourceId] != record.contentHash) {
                    return@forEach
                }
                if (record.dimension != queryVector.size) return@forEach
                val vector = decodeVector(record.vector)
                if (vector.size != queryVector.size) return@forEach
                var score = 0.0
                for (index in vector.indices) {
                    score += vector[index] * queryVector[index]
                }
                put(record.sourceId, score.coerceIn(-1.0, 1.0))
            }
        }
    }

    fun dispose() {
        runtime.close()
    }

    companion object {
        const val SOURCE_TYPE = "structured_memory"

        internal fun structuredEmbeddingText(memory: StructuredMemoryEntity): String =
            buildString {
                append("subject: ").append(memory.subject)
                memory.factKey?.takeIf(String::isNotBlank)?.let {
                    append("\nfact_key: ").append(it)
                }
                memory.value?.takeIf(String::isNotBlank)?.let {
                    append("\nvalue: ").append(it)
                }
                append("\ncontent: ").append(memory.content)
                if (memory.factType.isNotBlank()) {
                    append("\nfact_type: ").append(memory.factType)
                }
            }

        internal fun encodeVector(vector: FloatArray): ByteArray =
            ByteBuffer.allocate(vector.size * Float.SIZE_BYTES)
                .order(ByteOrder.LITTLE_ENDIAN)
                .apply { vector.forEach(::putFloat) }
                .array()

        internal fun decodeVector(bytes: ByteArray): FloatArray {
            require(bytes.size % Float.SIZE_BYTES == 0) { "Vector BLOB 长度不正确" }
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            return FloatArray(bytes.size / Float.SIZE_BYTES) { buffer.float }
        }
    }
}
