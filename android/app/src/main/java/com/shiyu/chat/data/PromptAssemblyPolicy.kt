package com.shiyu.chat.data

internal enum class PromptSectionKind(
    val label: String,
    val required: Boolean,
    val priority: Int,
    val maxCharacters: Int,
) {
    TRAINING_PROMPT("固定训练角色 Prompt", required = true, priority = 100, maxCharacters = 20_000),
    ROLE_IDENTITY("角色扮演原则", required = true, priority = 100, maxCharacters = 700),
    ROLE_BEHAVIOR("角色行为与用户控制", required = true, priority = 99, maxCharacters = 1_400),
    ROLE_CARD("正式角色档案", required = true, priority = 99, maxCharacters = 2_600),
    ROLE_CONTEXT("角色背景与固定事实", required = true, priority = 98, maxCharacters = 2_600),
    KNOWLEDGE_BOUNDARY("认知边界", required = false, priority = 94, maxCharacters = 1_500),
    USER_PERSONA("用户 Persona", required = false, priority = 92, maxCharacters = 800),
    SCENE("当前场景", required = false, priority = 90, maxCharacters = 1_000),
    WORLD_LORE("世界设定 Lore", required = false, priority = 86, maxCharacters = 2_600),
    SUMMARY("更早剧情摘要", required = false, priority = 82, maxCharacters = 900),
    MEMORY("聊天长期记忆", required = false, priority = 78, maxCharacters = 2_200),
    EVENTS("相关事件进展", required = false, priority = 72, maxCharacters = 1_000),
    EXCERPTS("旧对话摘录", required = false, priority = 58, maxCharacters = 700),
    TURN_POLICY("本轮交流倾向", required = false, priority = 50, maxCharacters = 800),
    AFFECT("本轮即时反应", required = false, priority = 45, maxCharacters = 600),
    EXAMPLES("语气示例", required = false, priority = 35, maxCharacters = 900),
    SAFETY("输入与输出约束", required = true, priority = 98, maxCharacters = 1_800),
    USER_BOUNDARY("用户边界", required = true, priority = 100, maxCharacters = 900),
}

internal data class PromptSectionDraft(
    val kind: PromptSectionKind,
    val content: String,
)

internal data class PromptSectionResult(
    val kind: PromptSectionKind,
    val label: String,
    val characters: Int,
    val approximateTokens: Int,
)

internal data class PromptAssemblyResult(
    val systemText: String,
    val sections: List<PromptSectionResult>,
    val droppedSections: List<String>,
    val usedCharacters: Int,
    val budgetCharacters: Int,
)

/**
 * Mirrors the mature prompt-manager pattern used by role-play frontends:
 * independent prompt items, stable ordering, required sections, and a bounded
 * budget. Optional sections are admitted by priority but still emitted in the
 * declared order.
 */
internal object PromptAssemblyPolicy {
    const val DEFAULT_MAX_CHARACTERS = 9_000

    fun assemble(
        drafts: List<PromptSectionDraft>,
        maxCharacters: Int = DEFAULT_MAX_CHARACTERS,
    ): PromptAssemblyResult {
        val normalized = linkedMapOf<PromptSectionKind, String>()
        drafts.forEach { draft ->
            val content = draft.content.trim()
            if (content.isBlank()) return@forEach
            val capped = content.take(draft.kind.maxCharacters).trim()
            if (capped.isBlank()) return@forEach
            normalized[draft.kind] = listOfNotNull(
                normalized[draft.kind],
                capped,
            ).joinToString("\n")
        }

        val selected = linkedSetOf<PromptSectionKind>()
        var used = 0
        normalized.forEach { (kind, content) ->
            if (!kind.required) return@forEach
            selected += kind
            used += renderedSection(kind, content).length
        }

        val budget = maxCharacters.coerceAtLeast(1_000)
        normalized.entries
            .filter { !it.key.required && it.key !in selected }
            .sortedWith(
                compareByDescending<Map.Entry<PromptSectionKind, String>> {
                    it.key.priority
                }.thenBy { it.key.ordinal },
            )
            .forEach { (kind, content) ->
                val rendered = renderedSection(kind, content)
                if (used + rendered.length + 2 <= budget) {
                    selected += kind
                    used += rendered.length + 2
                }
            }

        val orderedEntries = normalized.entries.filter { it.key in selected }
        val systemText = orderedEntries.joinToString("\n\n") { (kind, content) ->
            renderedSection(kind, content)
        }
        val sections = orderedEntries.map { (kind, content) ->
            PromptSectionResult(
                kind = kind,
                label = kind.label,
                characters = content.length,
                approximateTokens = approximatePromptTokens(content.length),
            )
        }
        val dropped = normalized.keys
            .filter { it !in selected }
            .map { it.label }
        return PromptAssemblyResult(
            systemText = systemText,
            sections = sections,
            droppedSections = dropped,
            usedCharacters = systemText.length,
            budgetCharacters = budget,
        )
    }

    private fun renderedSection(
        kind: PromptSectionKind,
        content: String,
    ): String = "【${kind.label}】\n$content"
}
