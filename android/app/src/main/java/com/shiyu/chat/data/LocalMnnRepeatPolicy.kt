package com.shiyu.chat.data

internal data class RepetitionCleanup(
    val text: String,
    val removedSegments: Int,
)

private val sentencePattern =
    Regex("[^。！？!?；;\\n]+[。！？!?；;]?|\\n+")
private val actionPattern = Regex("[（(][^）)]*[）)]")
private val removableKeyCharacters = Regex("[\\s\\p{P}\\p{S}]+")

internal fun cleanRepeatedResponse(
    answer: String,
    previousAssistantAnswers: List<String> = emptyList(),
): RepetitionCleanup {
    val seen = previousAssistantAnswers
        .flatMap(::responseSegments)
        .mapNotNull(::repetitionKey)
        .toMutableSet()
    var removed = 0
    val kept = mutableListOf<String>()

    responseSegments(answer).forEach { segment ->
        if (segment.isBlank()) return@forEach
        val key = repetitionKey(segment)
        if (key != null && !seen.add(key)) {
            removed += 1
            val actions = actionPattern.findAll(segment)
                .map { it.value.trim() }
                .filter(String::isNotBlank)
                .joinToString("")
            if (actions.isNotBlank()) kept += actions
        } else {
            kept += segment
        }
    }

    val cleaned = kept.joinToString("")
        .trim()
        .replace(Regex("\\n{3,}"), "\n\n")
    return RepetitionCleanup(
        text = cleaned.ifBlank { answer.trim() },
        removedSegments = removed,
    )
}

private fun responseSegments(text: String): List<String> =
    sentencePattern.findAll(text).map { it.value }.toList()

private fun repetitionKey(segment: String): String? {
    val withoutActions = actionPattern.replace(segment, "")
    val key = removableKeyCharacters.replace(withoutActions.lowercase(), "")
    return key.takeIf { it.length >= 8 }
}

internal fun localMnnSamplerConfig(isRetry: Boolean): String {
    val penalty = if (isRetry) "1.22" else "1.15"
    val ngramFactor = if (isRetry) "1.12" else "1.08"
    return """
        {
          "sampler_type": "mixed",
          "mixed_samplers": ["penalty", "topK", "topP", "temperature"],
          "temperature": 0.65,
          "topP": 0.90,
          "topK": 40,
          "penalty": $penalty,
          "n_gram": 4,
          "ngram_factor": $ngramFactor,
          "penalty_sampler": "greedy"
        }
    """.trimIndent()
}

internal fun localMnnSummaryConfig(): String = """
    {
      "sampler_type": "mixed",
      "mixed_samplers": ["penalty", "temperature"],
      "temperature": 0.20,
      "penalty": 1.08,
      "n_gram": 5,
      "ngram_factor": 1.05,
      "penalty_sampler": "greedy"
    }
""".trimIndent()
