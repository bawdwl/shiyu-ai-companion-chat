package com.shiyu.chat.data

import org.json.JSONArray

internal fun memoryOriginLabel(memory: StructuredMemoryEntity): String = when {
    memory.kind == "role_claim" ||
        memory.factType == "role_claim" ||
        memory.sourceAuthority == "character_self_report" ->
        "角色自述 · 非正式设定"
    memory.kind == "shared_event" || memory.factType == "event" ->
        "共同经历 · 当前剧情"
    memory.factType == "plan" ->
        "约定与计划"
    memory.subject == "user" ->
        "用户共享资料"
    else ->
        "角色设定或确定事实"
}

internal fun memoryEvidenceLines(memoryId: Long, evidenceJson: String): List<String> =
    runCatching {
        val array = JSONArray(evidenceJson)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val content = item.optString("content").trim()
                if (content.isBlank()) continue
                val role = item.optString("role")
                val speaker = if (role == "user") "用户" else "角色"
                val sourceId = item.optLong(
                    "message_id",
                    item.optLong("id", 0L),
                )
                val prefix = if (sourceId > 0) "#$sourceId " else ""
                add("$prefix$speaker：${content.take(180)}")
            }
        }
    }.getOrDefault(emptyList()).ifEmpty {
        listOf("尚无逐字证据 · 记忆 #$memoryId")
    }
