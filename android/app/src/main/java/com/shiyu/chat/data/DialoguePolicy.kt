package com.shiyu.chat.data

internal data class DialoguePolicyDecision(
    val strategy: String = "NONE",
    val secondaryStrategy: String? = null,
    val questionPreference: String = "natural",
    val replyLength: String = "natural",
    val reason: String = "",
)

/**
 * A cheap, observable turn-level hint. It deliberately defaults to NONE so a
 * normal chat is not forced into a script or an extra model call.
 */
internal object DialoguePolicy {
    fun decide(currentText: String, history: List<MessageEntity>): DialoguePolicyDecision {
        val text = currentText.trim()
        if (text.isBlank()) return DialoguePolicyDecision()
        val recent = history.takeLast(6).joinToString(" ") { it.content }
        val combined = "$recent $text"
        return when {
            explicitListenCue(text) -> DialoguePolicyDecision(
                strategy = "LISTEN",
                questionPreference = "low",
                replyLength = "short",
                reason = "用户明确要求先倾听或不要追问",
            )
            closeCue(text) -> DialoguePolicyDecision(
                strategy = "CLOSE",
                questionPreference = "none",
                replyLength = "short",
                reason = "用户表达了结束或暂停当前话题",
            )
            continuationCue(text) -> DialoguePolicyDecision(
                strategy = "CONTINUE",
                questionPreference = "natural",
                reason = "用户要求承接之前的话题或事件",
            )
            goodNewsCue(text) -> DialoguePolicyDecision(
                strategy = "CELEBRATE",
                questionPreference = "low",
                replyLength = "short",
                reason = "用户分享了明确的好消息",
            )
            distressCue(text) -> DialoguePolicyDecision(
                strategy = "LISTEN",
                questionPreference = "low",
                reason = "用户正在表达压力、难过或疲惫",
            )
            disagreementCue(text) -> DialoguePolicyDecision(
                strategy = "DISCUSS",
                secondaryStrategy = "DISAGREE",
                questionPreference = "natural",
                reason = "用户提出了不同意见",
            )
            shareRequestCue(text) -> DialoguePolicyDecision(
                strategy = "SHARE",
                questionPreference = "low",
                reason = "用户邀请角色分享自己的看法或经历",
            )
            discussionCue(combined) -> DialoguePolicyDecision(
                strategy = "DISCUSS",
                questionPreference = "natural",
                reason = "当前话题适合表达角色自己的判断",
            )
            else -> DialoguePolicyDecision()
        }
    }

    fun promptText(decision: DialoguePolicyDecision): String {
        if (decision.strategy == "NONE") return ""
        val secondary = decision.secondaryStrategy
            ?.takeIf { it != decision.strategy }
            ?.let { "，可轻微叠加 $it" }
            .orEmpty()
        return "本轮交流倾向：${decision.strategy}$secondary；" +
            "追问倾向为 ${decision.questionPreference}，回复篇幅为 ${decision.replyLength}。" +
            "这只是表达倾向，不是硬脚本；以用户明确要求和角色人格为准。"
    }

    private fun explicitListenCue(text: String): Boolean =
        listOf(
            "不想听建议",
            "别给建议",
            "不要建议",
            "别劝我",
            "不要安慰我",
            "只想倾诉",
            "先听我说",
            "别追问",
            "不要追问",
        ).any(text::contains)

    private fun closeCue(text: String): Boolean {
        val normalized = text.trim().lowercase()
        return normalized in setOf("晚安", "拜拜", "再见", "先这样", "不聊了", "睡了") ||
            listOf("先不聊", "下次再聊", "今天到这里").any(normalized::contains)
    }

    private fun continuationCue(text: String): Boolean =
        listOf(
            "后来呢",
            "然后呢",
            "接着呢",
            "上次",
            "之前那件事",
            "那个事",
            "那件事",
            "结果怎么样",
            "怎么样了",
        ).any(text::contains)

    private fun goodNewsCue(text: String): Boolean =
        listOf(
            "考上了",
            "考上",
            "通过了",
            "拿到 offer",
            "拿到offer",
            "升职了",
            "成功了",
            "终于完成",
            "录取了",
            "中奖了",
        ).any { text.contains(it, ignoreCase = true) }

    private fun distressCue(text: String): Boolean =
        listOf(
            "难过",
            "好累",
            "很累",
            "烦死",
            "压力大",
            "焦虑",
            "委屈",
            "想哭",
            "崩溃",
            "伤心",
            "心累",
            "撑不住",
        ).any(text::contains)

    private fun disagreementCue(text: String): Boolean =
        listOf("不同意", "我不这么觉得", "你错了", "不是这样", "我有不同看法")
            .any(text::contains)

    private fun shareRequestCue(text: String): Boolean =
        listOf("你觉得呢", "你怎么看", "你呢", "你会怎么做", "你以前有没有")
            .any(text::contains)

    private fun discussionCue(text: String): Boolean =
        listOf("我觉得", "我认为", "是不是应该", "到底该", "怎么看")
            .any(text::contains)
}
