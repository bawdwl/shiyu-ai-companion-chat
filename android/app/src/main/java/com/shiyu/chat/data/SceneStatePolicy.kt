package com.shiyu.chat.data

internal data class SceneDecision(
    val state: SceneStateEntity?,
    val changed: Boolean,
    val reason: String,
)

/**
 * Scene changes only when the user explicitly establishes or exits a scene.
 * Casual messages such as "今天好累" must not silently teleport the character.
 */
internal object SceneStatePolicy {
    private val exitCues = listOf(
        "退出剧情",
        "回到现实",
        "结束这个场景",
        "不演了",
        "从场景里出来",
    )
    private val activityWords = listOf(
        "散步",
        "吃饭",
        "喝茶",
        "喝咖啡",
        "看书",
        "聊天",
        "坐车",
        "等人",
        "逛街",
        "看海",
        "休息",
    )
    private val scenePatterns = listOf(
        Regex("(?:我们现在在|我们正坐在|我们已经在|我们来到|场景是|设定在|想象我们在)([^，。！？]{2,36})"),
        Regex("把场景(?:改成|换到)([^，。！？]{2,36})"),
    )

    fun next(
        current: SceneStateEntity?,
        currentText: String,
        messageId: Long,
        now: Long = System.currentTimeMillis(),
    ): SceneDecision {
        val text = currentText.trim()
        if (exitCues.any(text::contains)) {
            return SceneDecision(
                state = SceneStateEntity(
                    characterId = current?.characterId.orEmpty(),
                    mode = "daily",
                    updatedAt = now,
                ),
                changed = true,
                reason = "explicit_scene_exit",
            )
        }
        val captured = scenePatterns.firstNotNullOfOrNull { regex ->
            regex.find(text)?.groupValues?.getOrNull(1)?.trim()
        } ?: return SceneDecision(current, false, "no_explicit_scene_update")
        val activity = activityWords.firstOrNull(captured::contains).orEmpty()
        val location = if (activity.isBlank()) {
            captured
        } else {
            captured.replace(activity, "").trim().trim('的')
        }
        val sourceIds = (
            current?.sourceMessageIds
                ?.split(',')
                ?.mapNotNull(String::trim)
                ?.mapNotNull(String::toLongOrNull)
                .orEmpty() + messageId
            ).distinct().takeLast(8).joinToString(",")
        return SceneDecision(
            state = SceneStateEntity(
                characterId = current?.characterId.orEmpty(),
                mode = "immersive",
                location = location.take(120),
                participantsJson = "[\"user\",\"character\"]",
                activity = activity.take(80),
                sourceMessageIds = sourceIds,
                updatedAt = now,
            ),
            changed = true,
            reason = "explicit_scene_establishment",
        )
    }

    fun promptText(
        state: SceneStateEntity?,
        roleContext: CharacterRoleContext,
    ): String {
        val mode = state?.mode ?: roleContext.interactionMode
        if (mode != "immersive") {
            return "当前为日常陪伴模式。除非用户明确建立场景，不要自行声称双方身处某个地点，" +
                "也不要替用户安排现实行动。"
        }
        val parts = buildList {
            if (!state?.location.isNullOrBlank()) add("地点：${state!!.location}")
            if (!state?.activity.isNullOrBlank()) add("当前活动：${state!!.activity}")
            if (!state?.participantsJson.isNullOrBlank()) add("在场人物：用户与角色")
        }
        return if (parts.isEmpty()) {
            "当前为沉浸式剧情模式；尚未明确地点和活动，只能承接用户已经建立的场景。"
        } else {
            "当前为沉浸式剧情模式。${parts.joinToString("；")}。" +
                "只使用用户已明确建立或前文已经发生的内容，不擅自推进未发生的剧情。"
        }
    }
}
