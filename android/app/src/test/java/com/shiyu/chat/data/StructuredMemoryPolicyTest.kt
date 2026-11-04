package com.shiyu.chat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuredMemoryPolicyTest {
    private fun structured(
        id: Long,
        state: String,
        value: String?,
        evidence: String = """[{"message_id":$id,"role":"user","quote":"$value"}]""",
    ) = StructuredMemoryEntity(
        id = id,
        characterId = "xiaoman",
        subject = "user",
        factKey = "user.interview.time",
        value = value,
        content = "面试时间",
        kind = "user_report",
        evidenceJson = evidence,
        observedOrder = id,
        state = state,
    )

    @Test
    fun factKeysCanonicalizeSynonymsAndSubjectPrefix() {
        assertEquals(
            "user.interview.time",
            canonicalFactKey("current.interview.day", "user"),
        )
        assertEquals(
            "character.home.place",
            canonicalFactKey("profile.home.city", "character"),
        )
        assertEquals(
            "interview.time",
            factKeySignature("user.interview.day"),
        )
    }

    @Test
    fun automaticPolicyMergesRepeatsAndSupersedesExplicitUpdates() {
        assertEquals(
            "merge",
            automaticOperation(
                requested = "add",
                hasTarget = true,
                samePayload = true,
                explicitCorrection = false,
                newerEvidence = true,
            ),
        )
        assertEquals(
            "supersede",
            automaticOperation(
                requested = "add",
                hasTarget = true,
                samePayload = false,
                explicitCorrection = true,
                newerEvidence = true,
            ),
        )
        assertEquals(
            "contested",
            automaticOperation(
                requested = "add",
                hasTarget = true,
                samePayload = false,
                explicitCorrection = false,
                newerEvidence = true,
            ),
        )
        assertEquals(
            "add",
            automaticOperation(
                requested = "merge",
                hasTarget = false,
                samePayload = false,
                explicitCorrection = false,
                newerEvidence = false,
            ),
        )
        assertEquals(
            "contested",
            automaticOperation(
                requested = "supersede",
                hasTarget = true,
                samePayload = false,
                explicitCorrection = false,
                newerEvidence = true,
            ),
        )
        assertEquals(
            "supersede",
            automaticOperation(
                requested = "supersede",
                hasTarget = true,
                samePayload = false,
                explicitCorrection = true,
                newerEvidence = true,
            ),
        )
    }

    @Test
    fun deletedEvidenceStaysBlockedButNewerMessageCanRelearn() {
        val prior = StructuredMemoryTombstoneEntity(
            id = 1,
            characterId = "xiaoman",
            evidenceHash = "hash",
            subject = "user",
            kind = "user_report",
            factKey = "user.interview.time",
            value = "周三",
            content = "面试在周三",
            sourceMessageIds = "1,2",
        )
        assertTrue(
            tombstoneMatches(
                characterId = "xiaoman",
                evidenceHash = "different-substring-hash",
                subject = "user",
                kind = "user_report",
                factKey = "user.interview.time",
                value = "周三",
                content = "面试在周三",
                sourceIds = setOf(1L),
                userSourceIds = setOf(1L),
                prior = prior,
            ),
        )
        assertFalse(
            tombstoneMatches(
                characterId = "xiaoman",
                evidenceHash = "new-message-hash",
                subject = "user",
                kind = "user_report",
                factKey = "user.interview.time",
                value = "周三",
                content = "面试在周三",
                sourceIds = setOf(9L),
                userSourceIds = setOf(9L),
                prior = prior,
            ),
        )
        assertTrue(
            tombstoneMatches(
                characterId = "xiaoman",
                evidenceHash = "mixed-evidence",
                subject = "user",
                kind = "user_report",
                factKey = "user.interview.time",
                value = "周三",
                content = "面试在周三",
                sourceIds = setOf(1L, 9L),
                userSourceIds = emptySet(),
                prior = prior,
            ),
        )
    }

    @Test
    fun nullValueTombstoneUsesContentSimilarity() {
        val prior = StructuredMemoryTombstoneEntity(
            id = 2,
            characterId = "xiaoman",
            evidenceHash = "hash",
            subject = "character",
            kind = "character_detail",
            factKey = "character.daily.habit",
            value = null,
            content = "角色喜欢在雨天读书",
            sourceMessageIds = "2",
        )
        assertTrue(
            tombstoneMatches(
                characterId = "xiaoman",
                evidenceHash = "different",
                subject = "character",
                kind = "character_detail",
                factKey = "character.daily.habit",
                value = null,
                content = "角色喜欢在雨天阅读",
                sourceIds = setOf(2L),
                userSourceIds = emptySet(),
                prior = prior,
            ),
        )
    }

    @Test
    fun extractionFactsPreferTopicOverPureRecency() {
        val items = buildList {
            add(
                structured(1, "active", null).copy(
                    factKey = "user.project.alpha",
                    content = "用户正在准备项目 Alpha",
                ),
            )
            for (id in 2L..7L) {
                add(
                    structured(id, "active", null).copy(
                        factKey = "user.misc.$id",
                        content = "普通话题 $id",
                    ),
                )
            }
        }
        val selected = selectExtractionFacts(
            items,
            "项目 Alpha 还需要准备什么",
            limit = 2,
        )
        assertTrue(selected.any { it.factKey == "user.project.alpha" })
    }

    @Test
    fun backlogIsSplitIntoCompleteTurnWindows() {
        val messages = (1L..30L).map { id ->
            MessageEntity(
                id = id,
                characterId = "xiaoman",
                role = if (id % 2L == 0L) "assistant" else "user",
                content = "消息 $id",
            )
        }
        val first = selectMemoryWindow(messages, cursor = 0, messageEnd = 30)
        assertTrue(first != null)
        assertEquals(24, first!!.newBatch.size)
        assertEquals(24L, first.processedThrough)
        assertTrue(first.hasMore)

        val second = selectMemoryWindow(messages, cursor = 24, messageEnd = 30)
        assertTrue(second != null)
        assertEquals(6, second!!.newBatch.size)
        assertEquals(30L, second.processedThrough)
        assertEquals(4, second.sources.size - second.newBatch.size)
        assertFalse(second.hasMore)
    }

    @Test
    fun deletedStructuredEvidenceSuppressesLegacyExcerpt() {
        val legacy = MemoryEntity(
            id = 1,
            characterId = "xiaoman",
            memoryType = "episodic_excerpt",
            content = "用户说面试在周三",
            sourceMessageIds = "1,2",
        )
        val deleted = structured(
            id = 3,
            state = "deleted",
            value = "周三",
            evidence = """[{"message_id":1,"role":"user","quote":"面试在周三"}]""",
        )
        val kept = legacy.copy(id = 2, sourceMessageIds = "5,6")
        assertEquals(
            listOf(2L),
            suppressCoveredExcerpts(listOf(legacy, kept), listOf(deleted))
                .map { it.id },
        )
    }

    @Test
    fun importanceAndConfidenceAreDerivedFromFactMeaningAndEvidence() {
        assertEquals(
            1.0,
            memoryImportance(
                kind = "user_report",
                factKey = "user.boundary.topic",
                content = "用户不希望聊这个话题",
            ),
            0.0001,
        )
        assertEquals(
            0.84,
            memoryImportance(
                kind = "user_report",
                factKey = "user.interview.time",
                content = "面试改到周五",
            ),
            0.0001,
        )
        assertEquals(
            0.60,
            memoryConfidence(
                """[{"message_id":1,"role":"user","quote":"面试改到周五"}]""",
            ),
            0.0001,
        )
        assertEquals(0.95, memoryConfidence("[" + List(5) {
            """{"message_id":${it + 1},"role":"user","quote":"补充"}"""
        }.joinToString(",") + "]"), 0.0001)
    }
}
