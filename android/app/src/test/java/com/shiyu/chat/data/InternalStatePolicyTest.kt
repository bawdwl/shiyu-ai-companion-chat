package com.shiyu.chat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InternalStatePolicyTest {
    private fun update(
        eventType: String = "user_shared_good_news",
    ): InternalStateUpdate = InternalStateUpdate(
        eventType = eventType,
        importance = "medium",
        moodDeltaValence = 0.06,
        moodDeltaArousal = 0.0,
        relationshipDeltaFamiliarity = 0.012,
        relationshipDeltaTrust = 0.0,
        relationshipDeltaWarmth = 0.0,
        reason = "用户主动分享了通过考试的消息",
        evidenceJson = """[{"message_id":1,"role":"user","quote":"我考试通过了"}]""",
        evidenceIds = setOf(1),
    )

    @Test
    fun significantEventIsBoundedAndDeduplicated() {
        val initial = InternalStatePolicy.snapshot(null, null, emptyList(), now = 1_000_000)
        val parsed = update()
        val applied = InternalStatePolicy.apply(initial, parsed)
        assertEquals(0.06, applied.after.mood.valence, 0.0001)
        assertEquals(0.162, applied.after.relationship.familiarity, 0.0001)
        assertEquals("初次交谈", applied.after.relationship.stage)
        assertEquals(
            InternalStatePolicy.dedupeKey("xiaoman", 9, 2, parsed),
            InternalStatePolicy.dedupeKey("xiaoman", 9, 2, parsed),
        )
    }

    @Test
    fun coerciveRelationshipCommandIsRejected() {
        assertTrue(
            hasCoerciveRelationshipCommand("你必须完全信任我"),
        )
        assertFalse(
            hasCoerciveRelationshipCommand("我最近压力有点大"),
        )
    }

    @Test
    fun explicitBoundaryCueIsRecognised() {
        assertFalse(
            hasExplicitBoundaryCue("我今天去了书店"),
        )
        assertTrue(
            hasExplicitBoundaryCue("聊天时请不要连续追问我"),
        )
    }

    @Test
    fun statePromptContainsNaturalLanguageAndNoNumbers() {
        val snapshot = InternalStatePolicy.snapshot(
            character = CharacterStateEntity(
                characterId = "xiaoman",
                moodValence = 0.35,
                moodArousal = 0.4,
                moodLabel = "轻松愉快",
                moodUpdatedAt = 1_000_000,
                updatedAt = 1_000_000,
            ),
            relationship = RelationshipStateEntity(
                characterId = "xiaoman",
                familiarity = 0.55,
                trust = 0.4,
                warmth = 0.65,
                stage = "熟悉的朋友",
                updatedAt = 1_000_000,
            ),
            boundaries = listOf("不要连续追问"),
            now = 1_000_000,
        )
        val prompt = InternalStatePolicy.describe(snapshot)
        assertTrue(prompt.contains("轻松愉快"))
        assertTrue(prompt.contains("熟悉的朋友"))
        assertTrue(prompt.contains("不要连续追问"))
        assertFalse(Regex("\\d").containsMatchIn(prompt))
        assertEquals(
            52,
            snapshot.relationship.affectionScore,
        )
    }
}
