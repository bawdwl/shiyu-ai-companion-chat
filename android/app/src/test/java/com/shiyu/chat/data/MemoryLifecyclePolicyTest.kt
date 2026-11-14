package com.shiyu.chat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryLifecyclePolicyTest {
    private val now = 2_000_000_000_000L
    private val day = 86_400_000L

    private fun memory(
        id: Long,
        content: String,
        factType: String = "fact",
        updatedAt: Long = now,
        accessCount: Int = 0,
        pinned: Boolean = false,
    ) = StructuredMemoryEntity(
        id = id,
        characterId = "a",
        subject = "user",
        factKey = "user.test.$id",
        value = content,
        content = content,
        kind = "user_report",
        factType = factType,
        evidenceJson = "[]",
        observedOrder = id,
        semanticVerified = true,
        importance = 0.7,
        confidence = 0.8,
        pinned = pinned,
        state = "active",
        accessCount = accessCount,
        lastAccessedAt = updatedAt,
        createdAt = updatedAt,
        updatedAt = updatedAt,
    )

    @Test
    fun plansDecayFasterThanStableFacts() {
        val oldPlan = MemoryLifecyclePolicy.signals(
            memory(1, "准备下个月搬家", factType = "plan", updatedAt = now - 90 * day),
            now,
        )
        val oldFact = MemoryLifecyclePolicy.signals(
            memory(2, "用户长期住在杭州", factType = "fact", updatedAt = now - 90 * day),
            now,
        )

        assertTrue(oldPlan.stalePlan)
        assertFalse(oldFact.stalePlan)
        assertTrue(oldPlan.freshness < oldFact.freshness)
    }

    @Test
    fun repeatedUseRaisesBoostButPinnedPlanDoesNotExpire() {
        val used = MemoryLifecyclePolicy.signals(
            memory(3, "用户经常提到面试", accessCount = 8),
            now,
        )
        val pinnedPlan = MemoryLifecyclePolicy.signals(
            memory(
                4,
                "用户钉住的重要约定",
                factType = "plan",
                updatedAt = now - 365 * day,
                pinned = true,
            ),
            now,
        )

        assertTrue(used.accessBoost > 0.0)
        assertFalse(pinnedPlan.stalePlan)
    }

    @Test
    fun exactDuplicatesKeepPinnedOrNewestReplacement() {
        val pinned = memory(1, "用户喜欢咖啡", pinned = true, updatedAt = now - day)
        val newer = memory(2, "用户喜欢咖啡", updatedAt = now)
        val duplicates = MemoryLifecyclePolicy.duplicateReplacements(listOf(newer, pinned))

        assertEquals(1, duplicates.size)
        assertEquals(2L, duplicates.single().first.id)
        assertEquals(1L, duplicates.single().second.id)
    }
}
