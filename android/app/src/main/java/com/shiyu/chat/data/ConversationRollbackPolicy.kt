package com.shiyu.chat.data

/**
 * Message rollback is a destructive history operation. Keep the source checks
 * deterministic so chat rows and their derived memory/trace records cannot
 * silently diverge.
 */
private val evidenceMessageId = Regex(
    """"message_id"\s*:\s*(\d+)""",
)

internal fun legacyMemoryTouchesRollback(
    sourceMessageIds: String,
    targetMessageId: Long,
): Boolean =
    sourceMessageIds
        .split(',')
        .mapNotNull(String::trim)
        .mapNotNull(String::toLongOrNull)
        .any { it >= targetMessageId }

internal fun structuredMemoryTouchesRollback(
    evidenceJson: String,
    targetMessageId: Long,
): Boolean = evidenceMessageId.findAll(evidenceJson)
    .mapNotNull { it.groupValues.getOrNull(1)?.toLongOrNull() }
    .any { it >= targetMessageId } || !evidenceMessageId.containsMatchIn(evidenceJson)
