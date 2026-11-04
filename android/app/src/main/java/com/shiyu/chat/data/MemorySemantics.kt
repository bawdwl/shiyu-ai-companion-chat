package com.shiyu.chat.data

internal data class MemorySemanticMetadata(
    val kind: String,
    val subject: String,
    val factType: String,
    val epistemicStatus: String,
    val sourceAuthority: String,
    val knowledgeSource: String,
    val knownAt: Long?,
    val visibility: String = "character_private",
    val semanticVerified: Boolean = false,
    val semanticReason: String = "",
)

private val hypothesisCues = listOf(
    "如果", "假如", "假如说", "要是", "万一", "或许", "也许", "可能",
    "说不定", "不一定", "未必", "假设", "设想", "希望有一天",
)
private val planCues = listOf(
    "计划", "打算", "准备", "下次", "以后", "回头", "考虑搬到",
    "考虑换", "准备去", "准备搬", "将会", "约好", "约定",
)
private val opinionCues = listOf("我觉得", "我认为", "在我看来", "我感觉", "个人认为")
private val preferenceCues = listOf(
    "我喜欢", "我不喜欢", "我最喜欢", "我更喜欢", "我偏爱",
    "喜欢的", "讨厌", "偏好",
)

internal fun inferFactType(
    kind: String,
    content: String,
    quotes: String,
): String {
    val text = "$content $quotes"
    if (kind == "role_claim") return "role_claim"
    if (hypothesisCues.any(text::contains)) return "hypothesis"
    if (kind == "plan" || planCues.any(text::contains)) return "plan"
    if (kind == "belief" || opinionCues.any(text::contains)) return "opinion"
    if (preferenceCues.any(text::contains)) return "preference"
    if (kind == "shared_event") return "event"
    return "fact"
}

internal fun inferEpistemicStatus(text: String, factType: String): String = when {
    hypothesisCues.any(text::contains) -> "uncertain"
    listOf("似乎", "听起来", "看起来").any(text::contains) -> "inferred"
    else -> "explicit"
}

internal fun classifyMemorySemantics(
    kind: String,
    subject: String,
    content: String,
    quotes: String,
    roles: Set<String>,
    knownAt: Long?,
    requestedFactType: String? = null,
    requestedEpistemicStatus: String? = null,
    requestedAuthority: String? = null,
    requestedKnowledgeSource: String? = null,
): MemorySemanticMetadata {
    val effectiveKind = if (
        kind == "character_detail" && roles.isNotEmpty() &&
        roles.all { it == "assistant" }
    ) "role_claim" else kind
    val effectiveSubject = if (effectiveKind == "role_claim") "character" else subject
    val factType = if (effectiveKind == "role_claim") {
        "role_claim"
    } else {
        requestedFactType?.takeIf {
            it in setOf(
                "fact", "preference", "plan", "hypothesis",
                "opinion", "event", "role_claim",
            )
        } ?: inferFactType(effectiveKind, content, quotes)
    }
    val epistemic = requestedEpistemicStatus?.takeIf {
        it in setOf("explicit", "uncertain", "inferred", "disputed")
    } ?: inferEpistemicStatus("$content $quotes", factType)
    val authority = when {
        effectiveKind == "role_claim" || roles == setOf("assistant") ->
            "character_self_report"
        effectiveKind == "shared_event" -> "shared_event"
        effectiveKind == "belief" && "user" !in roles -> "model_inference"
        "user" in roles -> "user_explicit"
        requestedAuthority?.takeIf {
            it in setOf(
                "user_explicit", "shared_event",
                "character_self_report", "model_inference",
            )
        } != null -> requestedAuthority ?: "model_inference"
        else -> "model_inference"
    }
    val source = when (authority) {
        "character_self_report" -> "character_told"
        "shared_event" -> "observed"
        "model_inference" -> "inferred"
        else -> requestedKnowledgeSource?.takeIf {
            it in setOf("user_told", "character_told", "observed", "inferred")
        } ?: "user_told"
    }
    return MemorySemanticMetadata(
        kind = effectiveKind,
        subject = effectiveSubject,
        factType = if (factType == "role_claim") "role_claim" else factType,
        epistemicStatus = if (factType == "hypothesis") "uncertain" else epistemic,
        sourceAuthority = authority,
        knowledgeSource = source,
        knownAt = knownAt,
    )
}
