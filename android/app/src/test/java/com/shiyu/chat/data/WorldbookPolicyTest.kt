package com.shiyu.chat.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WorldbookPolicyTest {
    @Test
    fun andOrNotAndInclusionGroupAreApplied() {
        val entries = listOf(
            entry("海边", any = listOf("海", "潮"), all = listOf("约定")),
            entry("夜晚", any = listOf("夜")),
            entry("禁忌", any = listOf("海"), excluded = listOf("不想")),
            entry("低优先级", any = listOf("海"), group = "scene", priority = 1),
            entry("高优先级", any = listOf("海"), group = "scene", priority = 5),
        )

        val selected = selectWorldbookEntries(
            entries,
            "我们去看海，也记得那个约定，但不想再提那件事",
        )

        assertEquals(listOf("高优先级", "海边"), selected.map { it.title })
        assertEquals(listOf("夜晚"), selectWorldbookEntries(entries, "夜").map { it.title })
    }

    @Test
    fun stickyDoesNotRenewItselfAndExclusionWins() {
        val sticky = entry("港口", any = listOf("港口")).copy(sticky = true)
        val now = 10_000L
        val active = decideWorldbookActivation(
            entry = sticky,
            regularHit = false,
            explicitlyExcluded = false,
            lastHit = 1_000L,
            stickyUntil = 20_000L,
            now = now,
            stickyWindowMs = 60_000L,
        )
        val excluded = decideWorldbookActivation(
            entry = sticky,
            regularHit = false,
            explicitlyExcluded = true,
            lastHit = 1_000L,
            stickyUntil = 20_000L,
            now = now,
            stickyWindowMs = 60_000L,
        )

        assertEquals(true, active.inject)
        assertEquals(false, active.updateLastHit)
        assertEquals(null, active.renewStickyUntil)
        assertEquals(false, excluded.inject)
    }

    private fun entry(
        title: String,
        any: List<String> = emptyList(),
        all: List<String> = emptyList(),
        excluded: List<String> = emptyList(),
        group: String = "",
        priority: Int = 0,
    ) = WorldbookEntry(
        title = title,
        content = title,
        anyKeywords = any,
        allKeywords = all,
        excludedKeywords = excluded,
        priority = priority,
        inclusionGroup = group,
        sticky = false,
        cooldownSeconds = 0,
    )
}
