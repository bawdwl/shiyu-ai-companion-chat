package com.shiyu.chat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryControlPolicyTest {
    @Test
    fun originLabelsKeepRoleClaimsAndPlotEventsDistinct() {
        val roleClaim = memory(
            kind = "character_detail",
            factType = "role_claim",
            sourceAuthority = "character_self_report",
        )
        val sharedEvent = memory(
            kind = "shared_event",
            factType = "event",
            sourceAuthority = "shared_event",
        )

        assertEquals("角色自述 · 非正式设定", memoryOriginLabel(roleClaim))
        assertEquals("共同经历 · 当前剧情", memoryOriginLabel(sharedEvent))
    }

    @Test
    fun evidenceFallbackNeverBecomesEmpty() {
        assertTrue(memoryEvidenceLines(12, "not-json").single().contains("#12"))
    }

    private fun memory(
        kind: String,
        factType: String,
        sourceAuthority: String,
    ) = StructuredMemoryEntity(
        characterId = "test",
        subject = "user",
        factKey = null,
        value = null,
        content = "测试",
        kind = kind,
        evidenceJson = "[]",
        observedOrder = 1,
        factType = factType,
        sourceAuthority = sourceAuthority,
        semanticVerified = true,
        state = "active",
    )
}
