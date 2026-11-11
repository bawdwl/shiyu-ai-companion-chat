package com.shiyu.chat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExperiencePoliciesTest {
    @Test
    fun roleContextNormalizesLegacyAndGeneratedCards() {
        val legacy = CharacterRoleContext()
        assertEquals("daily", legacy.interactionMode)
        assertEquals("off", legacy.internalStateMode)
        assertEquals(emptyList<RoleFixedFact>(), legacy.fixedFacts)

        val context = CharacterRoleContext(
            scenario = "在书店整理新到的诗集",
            fixedFacts = listOf(
                RoleFixedFact("职业", "独立书店店员"),
            ),
            examples = listOf(
                RoleExampleDialogue(
                    "今天好累",
                    "先坐一会儿，我去给你倒杯水。",
                ),
            ),
            useExamples = true,
            internalStateMode = "legacy",
            goals = listOf("完成自己的插画集"),
        )
        assertEquals("独立书店店员", context.fixedFacts.single().value)
        assertEquals(1, context.examples.size)
        assertTrue(context.useExamples)
        assertEquals("legacy", context.internalStateMode)
        assertEquals(1, context.goals.size)
        assertTrue(
            RoleContextPolicy.promptSections(context).any {
                it.first == "作者确认的固定事实"
            },
        )
    }

    @Test
    fun examplesOnlyEnterTheFirstTwoHistoryMessages() {
        val context = CharacterRoleContext(
            examples = listOf(RoleExampleDialogue("你好", "我刚从外面回来。")),
            useExamples = true,
        )
        assertEquals(1, RoleContextPolicy.selectedExamples(context, 0).size)
        assertEquals(1, RoleContextPolicy.selectedExamples(context, 2).size)
        assertTrue(RoleContextPolicy.selectedExamples(context, 3).isEmpty())
    }

    @Test
    fun dialoguePolicyDefaultsToNoneAndHonorsExplicitBoundaries() {
        assertEquals(
            "NONE",
            DialoguePolicy.decide("今天路上看到一只很胖的猫", emptyList()).strategy,
        )
        val listen = DialoguePolicy.decide(
            "我现在不想听建议，也别追问",
            emptyList(),
        )
        assertEquals("LISTEN", listen.strategy)
        assertEquals("low", listen.questionPreference)
        val news = DialoguePolicy.decide("我考上研究生了", emptyList())
        assertEquals("CELEBRATE", news.strategy)
    }

    @Test
    fun memoryNeedSkipsSmallTalkAndExpandsAnaphora() {
        val history = listOf(
            MessageEntity(
                id = 1,
                characterId = "a",
                role = "user",
                content = "周五下午要去软件园面试",
            ),
            MessageEntity(
                id = 2,
                characterId = "a",
                role = "assistant",
                content = "我记住了。",
            ),
        )
        val skipped = MemoryNeedPolicy.decide(history, "哈哈")
        assertFalse(skipped.retrieve)
        assertEquals("low_information_casual_turn", skipped.reason)

        val expanded = MemoryNeedPolicy.decide(history, "那个后来怎么样了？")
        assertTrue(expanded.retrieve)
        assertTrue(expanded.query.contains("软件园面试"))
        assertEquals(listOf(1L), expanded.expandedSourceIds)
    }

    @Test
    fun currentTurnAffectDoesNotPretendToPersistRelationship() {
        val affect = CurrentTurnAffectPolicy.detect("我考上研究生了")
        assertNotNull(affect)
        val prompt = CurrentTurnAffectPolicy.promptText(affect)
        assertTrue(prompt.contains("高兴"))
        assertTrue(prompt.contains("不代表关系数值已经永久变化"))
    }

    @Test
    fun sceneOnlyChangesOnExplicitSceneLanguage() {
        val unchanged = SceneStatePolicy.next(
            SceneStateEntity("a"),
            "今天好累",
            messageId = 1,
        )
        assertFalse(unchanged.changed)

        val changed = SceneStatePolicy.next(
            SceneStateEntity("a"),
            "我们现在在海边散步",
            messageId = 2,
        )
        assertTrue(changed.changed)
        assertEquals("immersive", changed.state?.mode)
        assertEquals("海边", changed.state?.location)
        assertEquals("散步", changed.state?.activity)
    }

    @Test
    fun verifiedPlansBecomeOpenEventsAndEventsCompleteThem() {
        val plan = candidate(
            id = 1,
            kind = "plan",
            factType = "plan",
            factKey = "user.interview.plan",
            content = "用户计划周五参加面试",
        )
        val first = MemoryEventPolicy.next(plan, null, "daily", now = 100)
        assertNotNull(first)
        assertEquals("interview", first?.topicKey)
        assertEquals("open", first?.status)

        val existing = MemoryEventEntity(
            id = 9,
            characterId = "a",
            topicKey = "interview",
            title = "面试",
            summary = "用户计划周五参加面试",
            status = "open",
            sourceMessageIds = "1",
            timelineJson = "[]",
        )
        val completed = candidate(
            id = 2,
            kind = "shared_event",
            factType = "event",
            factKey = "shared.interview.result",
            content = "用户已经完成面试",
        )
        val update = MemoryEventPolicy.next(
            completed,
            existing,
            "daily",
            now = 200,
        )
        assertEquals("completed", update?.status)
        assertTrue(update?.timelineJson?.contains("用户已经完成面试") == true)
    }

    @Test
    fun eventSelectionRequiresARelevantOrContinuationCue() {
        val event = MemoryEventEntity(
            id = 1,
            characterId = "a",
            topicKey = "interview",
            title = "面试准备",
            summary = "用户准备周五参加软件园面试",
            status = "open",
            updatedAt = 1,
        )
        val newerUnrelated = MemoryEventEntity(
            id = 2,
            characterId = "a",
            topicKey = "moving",
            title = "搬家计划",
            summary = "用户准备月底搬家",
            status = "open",
            updatedAt = 10,
        )
        assertTrue(MemoryEventPolicy.selectRelevant(listOf(event), "面试怎么样了").isNotEmpty())
        assertTrue(MemoryEventPolicy.selectRelevant(listOf(event), "今天天气真好").isEmpty())
        assertEquals(
            listOf(2L),
            MemoryEventPolicy.selectRelevant(
                listOf(event, newerUnrelated),
                "那件事后来怎么样了",
            ).map { it.id },
        )
        assertNull(
            MemoryEventPolicy.topicKey(
                StructuredMemoryEntity(
                    characterId = "a",
                    subject = "user",
                    factKey = "user.preference.drink",
                    value = "咖啡",
                    content = "用户喜欢咖啡",
                    kind = "user_report",
                    evidenceJson = "[]",
                    observedOrder = 1,
                ),
            ),
        )
    }

    private fun candidate(
        id: Long,
        kind: String,
        factType: String,
        factKey: String,
        content: String,
    ): ParsedStructuredCandidate {
        val evidence = """[{"message_id":$id,"role":"user","quote":"$content"}]"""
        return ParsedStructuredCandidate(
            memory = StructuredMemoryEntity(
                characterId = "a",
                subject = "user",
                factKey = factKey,
                value = null,
                content = content,
                kind = kind,
                evidenceJson = evidence,
                observedOrder = id,
                factType = factType,
                semanticVerified = true,
            ),
            operation = "add",
            targetItemId = null,
            sourceIds = setOf(id),
        )
    }
}
