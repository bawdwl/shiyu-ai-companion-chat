package com.shiyu.chat.data

data class DreamGenerationSettings(
    val positiveTemplate: String = DEFAULT_POSITIVE_TEMPLATE,
    val negativePrompt: String = DEFAULT_NEGATIVE_PROMPT,
    val steps: Int = 50,
    val cfg: Float = 7.0f,
    val scheduler: String = "dpm_sde_karras",
    val seed: Long = 20261102L,
    val resolution: Int = 1024,
) {
    fun normalized(): DreamGenerationSettings = copy(
        positiveTemplate = positiveTemplate.trim().ifBlank { DEFAULT_POSITIVE_TEMPLATE },
        negativePrompt = negativePrompt.trim().ifBlank { DEFAULT_NEGATIVE_PROMPT },
        steps = steps.coerceIn(1, 50),
        cfg = cfg.coerceIn(1.0f, 30.0f),
        scheduler = scheduler.takeIf { it in SCHEDULERS } ?: "dpm_sde_karras",
        resolution = resolution.takeIf { it in RESOLUTIONS } ?: 1024,
    )

    fun canvasSize(): Pair<Int, Int> = when (normalized().resolution) {
        1024 -> 1024 to 1536
        768 -> 768 to 1344
        else -> 512 to 512
    }

    fun buildPrompt(character: CharacterEntity): String {
        val appearance = sequenceOf(character.appearancePrompt, character.appearance)
            .map(String::trim)
            .firstOrNull(String::isNotEmpty)
            .orEmpty()
        val subjectAwareAppearance = ensureVisualSubjectTags(
            appearance,
            inferVisualSubject(character),
        )
        val hasAppearanceToken = positiveTemplate.contains(
            APPEARANCE_TOKEN,
            ignoreCase = true,
        )
        return if (hasAppearanceToken) {
            positiveTemplate.replace(
                APPEARANCE_TOKEN,
                subjectAwareAppearance,
                ignoreCase = true,
            )
        } else {
            listOf(positiveTemplate.trimEnd(','), subjectAwareAppearance)
                .filter(String::isNotBlank)
                .joinToString(", ")
        }.take(1_800)
    }

    companion object {
        const val APPEARANCE_TOKEN = "{appearance}"
        val SCHEDULERS = setOf(
            "dpm",
            "dpm_karras",
            "dpm_sde",
            "dpm_sde_karras",
            "euler_a",
            "euler_a_karras",
            "euler",
            "euler_karras",
            "lcm",
        )
        val SCHEDULER_OPTIONS = listOf(
            "dpm" to "DPM++ 2M",
            "dpm_sde" to "DPM++ 2M SDE",
            "euler_a" to "Euler A",
            "euler" to "Euler",
            "lcm" to "LCM",
        )
        val RESOLUTIONS = setOf(512, 768, 1024)

        const val DEFAULT_POSITIVE_TEMPLATE =
            "masterpiece, best quality, anime illustration, " +
                "three-quarter upper body, {appearance}"

        const val DEFAULT_NEGATIVE_PROMPT =
            "lowres, bad anatomy, bad hands, missing fingers, extra fingers, " +
                "bad arms, missing legs, missing arms, poorly drawn face, bad face, " +
                "fused face, cloned face, three crus, fused feet, fused thigh, " +
                "extra crus, ugly fingers, horn, realistic photo, huge eyes, " +
                "worst face, 2girl, " +
                "long fingers, disconnected limbs"
    }
}
