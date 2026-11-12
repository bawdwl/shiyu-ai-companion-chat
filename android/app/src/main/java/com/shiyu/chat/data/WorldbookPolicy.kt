package com.shiyu.chat.data

import org.json.JSONArray

internal data class WorldbookEntry(
    val title: String,
    val content: String,
    val anyKeywords: List<String>,
    val allKeywords: List<String>,
    val excludedKeywords: List<String>,
    val priority: Int,
    val inclusionGroup: String,
    val sticky: Boolean,
    val cooldownSeconds: Long,
)

internal fun parseWorldbookEntries(raw: String): List<WorldbookEntry> =
    runCatching {
        val entries = JSONArray(raw)
        buildList {
            for (index in 0 until entries.length()) {
                val entry = entries.optJSONObject(index) ?: continue
                if (!entry.optBoolean("enabled", true)) continue
                val title = entry.optString("title").trim().take(80)
                val content = entry.optString("content").trim().take(1_200)
                if (title.isBlank() || content.isBlank()) continue
                add(
                    WorldbookEntry(
                        title = title,
                        content = content,
                        anyKeywords = readKeywords(
                            entry.optJSONArray("keywords_any")
                                ?: entry.optJSONArray("keywords"),
                        ),
                        allKeywords = readKeywords(entry.optJSONArray("keywords_all")),
                        excludedKeywords = readKeywords(
                            entry.optJSONArray("keywords_not")
                                ?: entry.optJSONArray("not_keywords"),
                        ),
                        priority = entry.optInt("priority", 0),
                        inclusionGroup = entry.optString(
                            "inclusion_group",
                            entry.optString("inclusionGroup"),
                        ).trim(),
                        sticky = entry.optBoolean("sticky", false),
                        cooldownSeconds = entry.optLong("cooldown", 0L)
                            .coerceIn(0L, 86_400L),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

internal fun worldbookEntryMatches(
    entry: WorldbookEntry,
    text: String,
): Boolean {
    val scan = text.lowercase()
    val hasPositive = entry.anyKeywords.isNotEmpty() || entry.allKeywords.isNotEmpty()
    val anyHit = entry.anyKeywords.isEmpty() ||
        entry.anyKeywords.any { scan.contains(it.lowercase()) }
    val allHit = entry.allKeywords.all { scan.contains(it.lowercase()) }
    val excluded = entry.excludedKeywords.any { scan.contains(it.lowercase()) }
    return hasPositive && anyHit && allHit && !excluded
}

internal fun selectWorldbookEntries(
    entries: List<WorldbookEntry>,
    text: String,
    maxEntries: Int = 4,
): List<WorldbookEntry> {
    return selectMatchedWorldbookEntries(
        entries
        .filter { worldbookEntryMatches(it, text) }
        .sortedWith(
            compareByDescending<WorldbookEntry> { it.priority }
                .thenBy { it.title },
        ),
        maxEntries,
    )
}

internal fun selectMatchedWorldbookEntries(
    matched: List<WorldbookEntry>,
    maxEntries: Int = 4,
): List<WorldbookEntry> {
    val sorted = matched
        .sortedWith(
            compareByDescending<WorldbookEntry> { it.priority }
                .thenBy { it.title },
        )
    val groups = linkedMapOf<String, WorldbookEntry>()
    val ungrouped = mutableListOf<WorldbookEntry>()
    sorted.forEach { entry ->
        if (entry.inclusionGroup.isBlank()) {
            ungrouped += entry
        } else {
            val current = groups[entry.inclusionGroup]
            if (current == null || entry.priority > current.priority) {
                groups[entry.inclusionGroup] = entry
            }
        }
    }
    return (ungrouped + groups.values)
        .distinctBy { it.title }
        .sortedWith(
            compareByDescending<WorldbookEntry> { it.priority }
                .thenBy { it.title },
        )
        .take(maxEntries.coerceAtLeast(0))
}

private fun readKeywords(array: JSONArray?): List<String> =
    if (array == null) {
        emptyList()
    } else {
        buildList {
            for (index in 0 until array.length()) {
                array.optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
            }
        }.distinct()
    }
