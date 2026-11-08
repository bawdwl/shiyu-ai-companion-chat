package com.shiyu.chat.data

import java.security.MessageDigest


internal const val PROMPT_PIPELINE_VERSION = "api-role-chat/android-2026-11-07.2"

internal fun sha256Text(text: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

internal fun roleConfigVersion(character: CharacterEntity): String = sha256Text(
    listOf(
        character.id,
        character.name,
        character.tag,
        character.description,
        character.personality,
        character.speechStyle,
        character.relationship,
        character.appearance,
        character.greeting,
        character.characterKind,
        character.era,
        character.knowledgeCutoff,
        character.forbiddenKnowledge,
        character.forbiddenTerms,
        character.worldbookJson,
        character.canLearnFromUser.toString(),
        character.allowEverydayInvention.toString(),
    ).joinToString("\u0000"),
)
