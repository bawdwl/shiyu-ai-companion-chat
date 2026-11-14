package com.shiyu.chat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemorySelectionTest {
    private fun memory(id: Long, text: String) = MemoryEntity(id = id, characterId = "a", memoryType = "episodic_excerpt", content = text, sourceMessageIds = "$id")
    private fun structured(
        id: Long,
        text: String,
        characterId: String = "a",
        state: String = "active",
        factKey: String = "user.interview.time",
        supersedes: Long? = null,
        subject: String = "user",
        kind: String = "user_report",
        factType: String = "fact",
        pinned: Boolean = false,
        importance: Double = 0.84,
        validFrom: Long? = if (state == "active" || state == "superseded") 100 else null,
        validTo: Long? = if (state == "superseded" || state == "deleted") 200 else null,
    ) = StructuredMemoryEntity(
        id = id,
        characterId = characterId,
        subject = subject,
        factKey = factKey,
        value = text,
        content = text,
        kind = kind,
        factType = factType,
        evidenceJson = "[]",
        observedOrder = id,
        semanticVerified = state == "active",
        importance = importance,
        confidence = 0.60,
        state = state,
        supersedes = supersedes,
        pinned = pinned,
        validFrom = validFrom,
        validTo = validTo,
    )
    @Test fun relevantOldExcerptRanksAheadOfRecentUnrelatedExcerpt() {
        val items = listOf(memory(2, "用户今天吃了面条"), memory(1, "用户准备去厦门面试"))
        assertEquals(1L, selectRelevantMemories(items, "厦门面试准备得如何", 1).single().id)
    }
    @Test fun noMatchPreservesRecentOrderAndLimit() {
        val items = listOf(memory(2, "去看电影"), memory(1, "买了一本书"))
        assertEquals(listOf(2L), selectRelevantMemories(items, "天气很好", 1).map { it.id })
        assertEquals(emptyList<MemoryEntity>(), selectRelevantMemories(items, "天气", 0))
    }

    @Test fun currentStructuredFactExcludesSupersededValue() {
        val old = structured(1, "面试在周三", state = "superseded")
        val current = structured(3, "面试在周五", supersedes = 1)
        val selected = selectStructuredMemories(listOf(old, current), "面试是什么时候", 8)
        assertEquals(listOf(3L), selected.map { it.id })
    }

    @Test fun historicalStructuredQuestionIncludesPredecessor() {
        val old = structured(1, "周末原本计划去爬山", state = "superseded")
        val current = structured(3, "周末改到周日下午去博物馆", supersedes = 1)
        val selected = selectStructuredMemories(listOf(old, current), "我原来周末想去做什么", 8)
        assertTrue(selected.any { it.id == 1L })
        assertTrue(selected.any { it.id == 3L })
    }

    @Test fun chineseNgramsRecallRewrittenInterviewFact() {
        val index = memorySearchTokens("用户的面试改到周五")
        assertTrue("面试" in index)
        assertTrue("周五" in index)
        val query = buildMemoryFtsQuery("面试日期")
        assertTrue(query.contains("\"面试\""))

        val current = structured(3, "面试改到周五", supersedes = 1)
        val selected = selectStructuredMemories(
            listOf(structured(1, "面试原本在周三", state = "superseded"), current),
            "面试日期",
            8,
            ftsScores = mapOf(3L to 1.0),
        )
        assertEquals(listOf(3L), selected.map { it.id })
    }

    @Test fun ftsOperatorsInUserTextRemainData() {
        val query = buildMemoryFtsQuery("面试\" OR * secret*")
        assertTrue(query.contains("\"面试\""))
        assertTrue(query.contains("\"secret\""))
        assertTrue("*" !in query)
        assertTrue("NEAR" !in query)
    }

    @Test fun historicalStateUsesValidityWithoutLeakingIntoCurrentQuestion() {
        val old = structured(1, "面试在周三", state = "superseded")
        val current = structured(3, "面试在周五", supersedes = 1)
        val currentOnly = selectStructuredMemories(
            listOf(old, current),
            "面试在什么时候",
            8,
            ftsScores = mapOf(1L to 1.0, 3L to 1.0),
        )
        assertEquals(listOf(3L), currentOnly.map { it.id })

        val historical = selectStructuredMemories(
            listOf(old, current),
            "面试以前是什么时候",
            8,
            ftsScores = mapOf(1L to 1.0, 3L to 1.0),
        )
        assertTrue(historical.any { it.id == 1L && it.validTo == 200L })
        assertTrue(historical.any { it.id == 3L && it.validTo == null })
    }

    @Test fun unifiedContextEnforcesCharacterScopeDeduplicationAndBudget() {
        val first = structured(
            id = 1,
            text = "用户准备厦门面试",
            factKey = "user.interview.place",
        )
        val duplicateExcerpt = memory(2, first.content).copy(characterId = "a", id = 2)
        val otherCharacter = structured(
            id = 3,
            text = "另一个角色知道厦门面试",
            characterId = "b",
            factKey = "character.interview.place",
        )
        val selection = selectUnifiedMemoryContext(
            characterId = "a",
            structuredMemories = listOf(first, otherCharacter),
            excerpts = listOf(duplicateExcerpt),
            query = "厦门面试准备得怎样",
            maxFacts = 8,
            maxExcerpts = 4,
            maxCharacters = 1_600,
            ftsScores = mapOf(1L to 1.0, 3L to 1.0),
        )
        assertEquals(listOf(1L), selection.structuredMemories.map { it.id })
        assertTrue(selection.excerpts.isEmpty())
        assertTrue(selection.charactersUsed <= 1_600)
    }

    @Test fun throwingSemanticProviderFallsBackToLexicalRetrieval() {
        val current = structured(3, "面试改到周五", supersedes = 1)
        val provider = MemorySemanticProvider { _, _ -> error("model unavailable") }
        val selected = selectStructuredMemories(
            listOf(structured(1, "面试原本在周三", state = "superseded"), current),
            "面试什么时候",
            8,
            semanticProvider = provider,
        )
        assertEquals(listOf(3L), selected.map { it.id })
    }

    @Test fun semanticProviderCanRecallWhenNoLiteralTermOverlaps() {
        val current = structured(3, "会面挪到周五")
        val provider = MemorySemanticProvider { _, texts ->
            texts.map { if ("会面" in it) 0.82 else 0.0 }
        }
        val selected = selectStructuredMemories(
            listOf(current),
            "日程",
            8,
            semanticProvider = provider,
        )
        assertEquals(listOf(3L), selected.map { it.id })
    }

    @Test fun indexedSemanticScoresCanRecallWithoutLiteralOverlap() {
        val current = structured(3, "会面挪到周五")
        val selected = selectStructuredMemories(
            memories = listOf(current),
            query = "日程",
            limit = 8,
            semanticScores = mapOf(3L to 0.82),
        )
        assertEquals(listOf(3L), selected.map { it.id })
    }

    @Test fun unifiedBudgetLimitsLongContent() {
        val facts = (1L..8L).map { id ->
            structured(
                id = id,
                text = "面试安排第${id}项" + "补充".repeat(80),
                factKey = "user.interview.item$id",
            )
        }
        val selection = selectUnifiedMemoryContext(
            characterId = "a",
            structuredMemories = facts,
            excerpts = emptyList(),
            query = "面试安排",
            maxFacts = 8,
            maxExcerpts = 4,
            maxCharacters = 300,
        )
        assertTrue(selection.charactersUsed <= 300)
        assertTrue(selection.structuredMemories.size < facts.size)
    }

    @Test fun coreUserFactStaysAvailableWithoutLexicalOverlap() {
        val stablePreference = structured(
            id = 7,
            text = "用户长期偏好安静的环境",
            factKey = "user.preference.noise",
        )
        val selection = selectUnifiedMemoryContext(
            characterId = "a",
            structuredMemories = listOf(stablePreference),
            excerpts = emptyList(),
            query = "今天天气不错",
            maxFacts = 8,
            maxExcerpts = 2,
            maxCharacters = 1_600,
        )
        assertEquals(listOf(7L), selection.structuredMemories.map { it.id })
        assertEquals(
            MemorySelectionReason.CORE_PROFILE,
            selection.structuredReasons[7L],
        )
    }

    @Test fun pinnedFactSurvivesWhenNoQueryTermMatches() {
        val pinned = structured(
            id = 8,
            text = "用户希望在提到家人时避开具体住址",
            factKey = "user.boundary.privacy",
            pinned = true,
        )
        val selection = selectUnifiedMemoryContext(
            characterId = "a",
            structuredMemories = listOf(pinned),
            excerpts = emptyList(),
            query = "今天工作有点累",
            maxFacts = 8,
            maxExcerpts = 2,
            maxCharacters = 1_600,
        )
        assertEquals(listOf(8L), selection.structuredMemories.map { it.id })
        assertEquals(MemorySelectionReason.PINNED, selection.structuredReasons[8L])
    }

    @Test fun recentFactsProvideBoundedFallbackWithoutDumpingAllHistory() {
        val facts = (1L..8L).map { id ->
            structured(
                id = id,
                text = "第${id}条共同经历",
                factKey = "shared.event.$id",
                subject = "shared",
                kind = "shared_event",
                factType = "event",
            )
        }
        val selection = selectUnifiedMemoryContext(
            characterId = "a",
            structuredMemories = facts,
            excerpts = emptyList(),
            query = "今天想随便聊聊",
            maxFacts = 8,
            maxExcerpts = 2,
            maxCharacters = 1_600,
        )
        assertEquals(listOf(8L, 7L, 6L), selection.structuredMemories.map { it.id })
        assertTrue(
            selection.structuredReasons.values.all {
                it == MemorySelectionReason.RECENT
            },
        )
    }

    @Test fun relevantOldFactIsPreferredBeforeRecentFallback() {
        val oldRelevant = structured(
            id = 1,
            text = "用户准备去厦门参加面试",
            factKey = "user.interview.place",
            kind = "shared_event",
            factType = "event",
        )
        val recent = (2L..6L).map { id ->
            structured(
                id = id,
                text = "第${id}条无关日常",
                factKey = "shared.daily.$id",
                subject = "shared",
                kind = "shared_event",
                factType = "event",
            )
        }
        val selection = selectUnifiedMemoryContext(
            characterId = "a",
            structuredMemories = listOf(oldRelevant) + recent,
            excerpts = emptyList(),
            query = "厦门面试准备得怎么样",
            maxFacts = 8,
            maxExcerpts = 2,
            maxCharacters = 1_600,
        )
        assertEquals(1L, selection.structuredMemories.first().id)
        assertEquals(
            MemorySelectionReason.RELEVANT,
            selection.structuredReasons[1L],
        )
    }

    @Test fun commonChineseParaphraseRecallsTheOriginalMemory() {
        val travel = structured(
            id = 9,
            text = "用户上次去厦门旅游",
            factKey = "shared.trip.xiamen",
            subject = "shared",
            kind = "shared_event",
            factType = "event",
        )
        val selected = selectStructuredMemories(
            memories = listOf(travel),
            query = "还记得那次出去玩吗",
            limit = 4,
        )

        assertEquals(listOf(9L), selected.map { it.id })
    }

    @Test fun longExcerptKeepsQueryRelevantWindowAndStaysCompact() {
        val content = "前文".repeat(200) + "那柄剑是谁留下的" + "后文".repeat(200)
        val compact = compactMemoryExcerptForPrompt(content, "那柄剑是谁留下的")
        assertTrue(compact.contains("那柄剑是谁留下的"))
        assertTrue(compact.length <= 262)
    }

    @Test fun unifiedContextHonorsTwoExcerptLimit() {
        val excerpts = (1L..4L).map { id ->
            memory(id, "第${id}条历史：用户提到剑与宗门")
        }
        val selection = selectUnifiedMemoryContext(
            characterId = "a",
            structuredMemories = emptyList(),
            excerpts = excerpts,
            query = "剑和宗门",
            maxFacts = 8,
            maxExcerpts = 2,
            maxCharacters = 1_600,
        )
        assertEquals(2, selection.excerpts.size)
    }
}
