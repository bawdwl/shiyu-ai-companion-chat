package com.shiyu.chat.data

import org.json.JSONArray
import org.json.JSONObject

internal data class RoleFixedFact(
    val key: String,
    val value: String,
    val status: String = "confirmed",
)

internal data class RoleExampleDialogue(
    val user: String,
    val character: String,
)

internal data class CharacterRoleContext(
    val version: Int = 1,
    val scenario: String = "",
    val motivation: String = "",
    val background: String = "",
    val boundaries: String = "",
    val fixedFacts: List<RoleFixedFact> = emptyList(),
    val examples: List<RoleExampleDialogue> = emptyList(),
    val useExamples: Boolean = false,
    val interactionMode: String = "daily",
    val goals: List<String> = emptyList(),
)

/**
 * A versioned, backward-compatible context shared by generated cards and the
 * Android editor. Unknown fields are ignored and old characters migrate to an
 * empty context without changing their visible profile.
 */
internal object RoleContextPolicy {
    fun parse(raw: String?): CharacterRoleContext {
        if (raw.isNullOrBlank()) return CharacterRoleContext()
        val root = JSONObject(raw)
        val fixedFacts = root.optJSONArray("fixed_facts").toFixedFacts()
        val examples = root.optJSONArray("example_dialogue").toExamples()
        val goals = root.optJSONArray("goals").toStringList(limit = 8, maxLength = 160)
        return CharacterRoleContext(
            version = root.optInt("version", 1).coerceAtLeast(1),
            scenario = root.optString("scenario").trim().take(600),
            motivation = root.optString("motivation").trim().take(400),
            background = root.optString("background").trim().take(1_200),
            boundaries = root.optString("boundaries").trim().take(800),
            fixedFacts = fixedFacts,
            examples = examples,
            useExamples = root.optBoolean("use_examples", false),
            interactionMode = root.optString(
                "interaction_mode",
                "daily",
            ).takeIf { it in setOf("daily", "immersive") } ?: "daily",
            goals = goals,
        )
    }

    fun normalizeJson(raw: String?): String = toJson(parse(raw))

    fun toJson(context: CharacterRoleContext): String =
        JSONObject()
            .put("version", 1)
            .put("scenario", context.scenario)
            .put("motivation", context.motivation)
            .put("background", context.background)
            .put("boundaries", context.boundaries)
            .put(
                "fixed_facts",
                JSONArray().apply {
                    context.fixedFacts.forEach {
                        put(
                            JSONObject()
                                .put("key", it.key)
                                .put("value", it.value)
                                .put("status", it.status),
                        )
                    }
                },
            )
            .put(
                "example_dialogue",
                JSONArray().apply {
                    context.examples.forEach {
                        put(
                            JSONObject()
                                .put("user", it.user)
                                .put("character", it.character),
                        )
                    }
                },
            )
            .put("use_examples", context.useExamples)
            .put("interaction_mode", context.interactionMode)
            .put("goals", JSONArray(context.goals))
            .toString()

    fun fromGenerated(root: JSONObject): CharacterRoleContext {
        val fixedFacts = (
            root.optJSONArray("fixed_facts")
                ?: root.optJSONArray("fixedFacts")
            )
            .toFixedFacts(defaultStatus = "proposal")
        val examples = (
            root.optJSONArray("example_dialogue")
                ?: root.optJSONArray("exampleDialogue")
            ).toExamples()
        val interactionMode = root.optString("interaction_mode")
            .ifBlank { root.optString("interactionMode", "daily") }
            .takeIf { it in setOf("daily", "immersive") }
            ?: "daily"
        return CharacterRoleContext(
            scenario = root.optString("scenario").trim().take(600),
            motivation = root.optString("motivation").trim().take(400),
            background = root.optString("background").trim().take(1_200),
            boundaries = root.optString("boundaries").trim().take(800),
            fixedFacts = fixedFacts,
            examples = examples,
            useExamples = root.optBoolean(
                "use_examples",
                root.optBoolean("useExamples", false),
            ),
            interactionMode = interactionMode,
            goals = root.optJSONArray("goals").toStringList(limit = 8, maxLength = 160),
        )
    }

    fun promptSections(context: CharacterRoleContext): List<Pair<String, String>> =
        buildList {
            if (context.background.isNotBlank()) {
                add("角色背景" to context.background)
            }
            if (context.motivation.isNotBlank()) {
                add("人物动机" to context.motivation)
            }
            if (context.scenario.isNotBlank()) {
                add("角色日常情境" to context.scenario)
            }
            if (context.boundaries.isNotBlank()) {
                add("互动边界" to context.boundaries)
            }
            val confirmed = context.fixedFacts.filter { it.status == "confirmed" }
            if (confirmed.isNotEmpty()) {
                add(
                    "作者确认的固定事实" to confirmed.joinToString("\n") {
                        "- ${it.key}：${it.value}"
                    },
                )
            }
            val proposals = context.fixedFacts.filter { it.status == "proposal" }
            if (proposals.isNotEmpty()) {
                add(
                    "待确认草稿，不是固定事实" to proposals.joinToString("\n") {
                        "- ${it.key}：${it.value}"
                    },
                )
            }
            if (context.goals.isNotEmpty()) {
                add(
                    "角色自己的稳定关注与目标" to context.goals.joinToString("\n") {
                        "- $it"
                    },
                )
            }
        }

    fun selectedExamples(
        context: CharacterRoleContext,
        historyCount: Int,
    ): List<RoleExampleDialogue> {
        if (!context.useExamples || historyCount > 2) return emptyList()
        return context.examples.take(2)
    }

    private fun JSONArray?.toFixedFacts(
        defaultStatus: String = "confirmed",
    ): List<RoleFixedFact> {
        if (this == null) return emptyList()
        val seen = mutableSetOf<String>()
        return buildList {
            for (index in 0 until minOf(length(), 30)) {
                val item = optJSONObject(index) ?: continue
                val key = item.optString("key").trim().take(60)
                val value = item.optString("value").trim().take(240)
                if (key.isBlank() || value.isBlank() || !seen.add(key)) continue
                val status = item.optString("status", defaultStatus)
                    .takeIf { it in setOf("confirmed", "proposal") }
                    ?: defaultStatus
                add(RoleFixedFact(key, value, status))
            }
        }
    }

    private fun JSONArray?.toExamples(): List<RoleExampleDialogue> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until minOf(length(), 6)) {
                val item = optJSONObject(index) ?: continue
                val user = item.optString("user").trim().take(300)
                val character = item.optString("character").trim().take(500)
                if (user.isBlank() || character.length < 6) continue
                add(RoleExampleDialogue(user, character))
            }
        }
    }

    private fun JSONArray?.toStringList(
        limit: Int,
        maxLength: Int,
    ): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until minOf(length(), limit)) {
                val value = optString(index).trim().take(maxLength)
                if (value.isNotBlank()) add(value)
            }
        }.distinct()
    }
}
