package com.shiyu.chat.data

internal data class CurrentTurnAffect(
    val label: String,
    val tone: String,
    val humor: String,
    val initiative: String,
    val reason: String,
)

/**
 * A temporary reaction for the current reply. It never mutates trust,
 * familiarity, warmth or relationship stage; the durable state worker still
 * has to verify evidence in the background.
 */
internal object CurrentTurnAffectPolicy {
    fun detect(text: String): CurrentTurnAffect? {
        val value = text.trim()
        if (value.isBlank()) return null
        return when {
            listOf("考上", "通过", "拿到offer", "拿到 offer", "录取", "升职", "中奖")
                .any { value.contains(it, ignoreCase = true) } -> CurrentTurnAffect(
                label = "替用户感到高兴",
                tone = "轻快自然",
                humor = "允许轻微玩笑",
                initiative = "先回应好消息，不急于追问细节",
                reason = "current_turn_good_news",
            )
            listOf("难过", "很累", "好累", "焦虑", "委屈", "想哭", "崩溃", "心累")
                .any(value::contains) -> CurrentTurnAffect(
                label = "关切，先接住情绪",
                tone = "放轻",
                humor = "低",
                initiative = "除非用户求助，不立即给方案",
                reason = "current_turn_distress",
            )
            listOf("不舒服", "生气", "失望", "你让我难受", "你刚才")
                .any(value::contains) -> CurrentTurnAffect(
                label = "意识到关系出现张力",
                tone = "认真",
                humor = "关闭",
                initiative = "先回应具体感受，不急着辩解",
                reason = "current_turn_conflict",
            )
            listOf("对不起", "是我误会", "我道歉", "刚才是我").any(value::contains) ->
                CurrentTurnAffect(
                    label = "愿意修复",
                    tone = "坦诚",
                    humor = "低",
                    initiative = "接住和解，不反复翻旧账",
                    reason = "current_turn_repair",
                )
            listOf("不要记", "别记", "不要追问", "别追问", "不想听建议")
                .any(value::contains) -> CurrentTurnAffect(
                label = "尊重用户刚明确的边界",
                tone = "克制",
                humor = "低",
                initiative = "不主动越过边界",
                reason = "current_turn_boundary",
            )
            else -> null
        }
    }

    fun promptText(affect: CurrentTurnAffect?): String {
        if (affect == null) return ""
        return "本轮即时反应：${affect.label}；语气${affect.tone}，幽默${affect.humor}，" +
            "${affect.initiative}。这只是本轮临时反应，不改写角色的稳定人格，" +
            "也不代表关系数值已经永久变化。"
    }
}
