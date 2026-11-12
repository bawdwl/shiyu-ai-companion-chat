package com.shiyu.chat.data

import org.json.JSONObject
import kotlin.math.ceil

internal data class PromptSectionEstimate(
    val label: String,
    val characters: Int,
) {
    val approximateTokens: Int
        get() = approximatePromptTokens(characters)
}

internal data class PromptInspectorSnapshot(
    val sections: List<PromptSectionEstimate>,
    val inputTokens: Int,
    val outputTokens: Int,
    val requestLimit: Int,
    val historyMessages: Int,
    val memoryCandidates: Int,
    val memorySelected: Int,
    val memoryTruncated: Boolean,
    val ignoredMemoryIds: List<Long>,
    val memoryReasons: List<String>,
)

internal fun approximatePromptTokens(characters: Int): Int =
    if (characters <= 0) 0 else ceil(characters / 1.7).toInt()

internal fun parsePromptInspectorSnapshot(traceJson: String): PromptInspectorSnapshot? =
    runCatching {
        val root = JSONObject(traceJson).getJSONObject("metadata")
        val breakdown = root.optJSONObject("prompt_breakdown")
            ?: return@runCatching null
        val sections = listOfNotNull(
            section("角色卡", breakdown, "character_card_chars"),
            section("世界书与边界", breakdown, "worldbook_chars"),
            section("长期记忆", breakdown, "memory_chars"),
            section("事件与场景", breakdown, "event_chars"),
            section("近期聊天", breakdown, "history_chars"),
            section("本轮输出", breakdown, "output_chars"),
        )
        PromptInspectorSnapshot(
            sections = sections,
            inputTokens = breakdown.optInt("estimated_input_tokens"),
            outputTokens = breakdown.optInt("estimated_output_tokens"),
            requestLimit = breakdown.optInt("request_token_limit"),
            historyMessages = breakdown.optInt("history_messages"),
            memoryCandidates = root.optInt("memory_candidates"),
            memorySelected = root.optInt("memory_selected"),
            memoryTruncated = root.optBoolean("memory_budget_truncated"),
            ignoredMemoryIds = buildList {
                val ids = root.optJSONArray("ignored_memory_ids") ?: return@buildList
                for (index in 0 until ids.length()) {
                    ids.optLong(index).takeIf { it > 0 }?.let(::add)
                }
            },
            memoryReasons = buildList {
                val reasons = root.optJSONArray("memory_selection_reasons") ?: return@buildList
                for (index in 0 until reasons.length()) {
                    val item = reasons.optJSONObject(index) ?: continue
                    val id = item.optLong("id")
                    val reason = item.optString("reason").trim()
                    if (id > 0 && reason.isNotBlank()) add("#$id $reason")
                }
            },
        )
    }.getOrNull()

private fun section(
    label: String,
    root: JSONObject,
    key: String,
): PromptSectionEstimate? =
    root.optInt(key, -1)
        .takeIf { it >= 0 }
        ?.let { PromptSectionEstimate(label, it) }
