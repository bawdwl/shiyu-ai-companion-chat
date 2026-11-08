package com.shiyu.chat.data

internal data class MemoryNeedDecision(
    val retrieve: Boolean,
    val query: String,
    val expandedSourceIds: List<Long> = emptyList(),
    val reason: String,
)

/**
 * Decide whether this turn should pay the retrieval cost at all. The decision
 * uses only the current message and already stored conversation text; it never
 * asks the model to invent query terms.
 */
internal object MemoryNeedPolicy {
    private val anaphoraCues = listOf(
        "那个",
        "那件",
        "这件事",
        "那件事",
        "上次",
        "刚才",
        "之前说",
        "后来呢",
        "然后呢",
        "怎么样了",
    )

    private val recallCues = listOf(
        "记得",
        "还记得",
        "我以前",
        "我之前",
        "原来",
        "原本",
        "最初",
        "以前住",
        "现在住",
        "我的偏好",
        "我说过",
        "共同经历",
        "我们的约定",
    )

    private val correctionCues = listOf(
        "不是",
        "改成",
        "改到",
        "改为",
        "更正",
        "说错",
        "现在已经",
        "后来",
        "不再",
        "取消了",
        "搬到",
        "换成",
        "目前",
        "如今",
    )

    private val boundaryCues = listOf(
        "不要记",
        "别记",
        "不要主动提",
        "不希望被",
        "不要追问",
        "别追问",
    )

    fun decide(history: List<MessageEntity>, currentText: String): MemoryNeedDecision {
        val current = currentText.trim()
        val expanded = expandedQuery(history, current)
        return when {
            current.isBlank() -> MemoryNeedDecision(
                false,
                current,
                reason = "empty_message",
            )
            correctionCues.any(current::contains) -> MemoryNeedDecision(
                true,
                expanded.query,
                expanded.sourceIds,
                "explicit_correction_or_current_state",
            )
            boundaryCues.any(current::contains) -> MemoryNeedDecision(
                true,
                expanded.query,
                expanded.sourceIds,
                "explicit_boundary",
            )
            recallCues.any(current::contains) -> MemoryNeedDecision(
                true,
                expanded.query,
                expanded.sourceIds,
                "explicit_recall",
            )
            anaphoraCues.any(current::contains) -> MemoryNeedDecision(
                true,
                expanded.query,
                expanded.sourceIds,
                "anaphoric_reference",
            )
            questionLike(current) -> MemoryNeedDecision(
                true,
                expanded.query,
                expanded.sourceIds,
                "question_may_need_context",
            )
            isLowInformation(current) -> MemoryNeedDecision(
                false,
                current,
                reason = "low_information_casual_turn",
            )
            else -> MemoryNeedDecision(
                true,
                current,
                reason = "topic_may_need_context",
            )
        }
    }

    private fun expandedQuery(
        history: List<MessageEntity>,
        current: String,
    ): ExpandedQuery {
        val needsExpansion =
            anaphoraCues.any(current::contains) ||
                correctionCues.any(current::contains) ||
                current.length <= 18
        if (!needsExpansion) return ExpandedQuery(current, emptyList())
        val previous = history.asSequence()
            .filter { it.role == "user" }
            .filter { it.content.isNotBlank() && it.content != current }
            .toList()
            .takeLast(2)
        if (previous.isEmpty()) return ExpandedQuery(current, emptyList())
        return ExpandedQuery(
            query = previous.joinToString("\n") { it.content } + "\n" + current,
            sourceIds = previous.map { it.id },
        )
    }

    private fun isLowInformation(text: String): Boolean {
        val normalized = text.lowercase().filterNot(Char::isWhitespace)
        if (normalized.length <= 6) {
            return true
        }
        return listOf(
            "哈哈",
            "嘿嘿",
            "嗯嗯",
            "好的",
            "收到",
            "谢谢",
            "晚安",
            "早安",
            "在吗",
            "我回来了",
            "笑死",
        ).any { normalized == it || normalized.startsWith(it) }
    }

    private fun questionLike(text: String): Boolean =
        text.trim().endsWith("?") ||
            text.trim().endsWith("？") ||
            listOf(
                "什么",
                "哪",
                "多少",
                "谁",
                "为什么",
                "怎么",
                "是否",
                "有没有",
                "告诉我",
            ).any(text::contains)

    private data class ExpandedQuery(
        val query: String,
        val sourceIds: List<Long>,
    )
}
