package com.shiyu.chat.data

internal fun activeBranchMetaKey(characterId: String): String =
    "active-branch:${characterId.length}:$characterId"

internal fun conversationEpochMetaKey(characterId: String): String =
    "conversation-epoch:${characterId.length}:$characterId"

internal suspend fun conversationEpoch(
    database: AppDatabase,
    characterId: String,
): Long =
    database.meta().get(conversationEpochMetaKey(characterId))
        ?.toLongOrNull()
        ?: 0L

internal suspend fun bumpConversationEpoch(
    database: AppDatabase,
    characterId: String,
): Long {
    val next = conversationEpoch(database, characterId) + 1L
    database.meta().put(
        AppMetaEntity(conversationEpochMetaKey(characterId), next.toString()),
    )
    return next
}
