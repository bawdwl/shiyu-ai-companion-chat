package com.shiyu.chat.data

/**
 * Android's bundled SQLite does not consistently support FTS5, so P1 uses
 * FTS4 over program-generated Chinese n-grams and English words. The virtual
 * table is intentionally outside Room's entity model; triggers keep it in
 * lockstep with structured_memories.
 */
internal object MemorySearchIndex {
    const val TABLE = "structured_memory_fts"

    val createStatements: List<String> = listOf(
        """
            CREATE VIRTUAL TABLE IF NOT EXISTS structured_memory_fts
            USING fts4(searchTokens, tokenize=unicode61)
        """.trimIndent(),
        """
            CREATE TRIGGER IF NOT EXISTS structured_memory_fts_ai
            AFTER INSERT ON structured_memories
            WHEN new.searchTokens <> ''
            BEGIN
                DELETE FROM structured_memory_fts WHERE docid = new.id;
                INSERT INTO structured_memory_fts(docid, searchTokens)
                VALUES(new.id, new.searchTokens);
            END
        """.trimIndent(),
        """
            CREATE TRIGGER IF NOT EXISTS structured_memory_fts_ad
            AFTER DELETE ON structured_memories
            BEGIN
                DELETE FROM structured_memory_fts WHERE docid = old.id;
            END
        """.trimIndent(),
        """
            CREATE TRIGGER IF NOT EXISTS structured_memory_fts_au
            AFTER UPDATE OF searchTokens ON structured_memories
            BEGIN
                DELETE FROM structured_memory_fts WHERE docid = old.id;
                INSERT INTO structured_memory_fts(docid, searchTokens)
                SELECT new.id, new.searchTokens WHERE new.searchTokens <> '';
            END
        """.trimIndent(),
    )

    fun querySql(includeHistory: Boolean): String {
        val states = if (includeHistory) "('active','superseded')" else "('active')"
        return """
            SELECT id FROM structured_memories
            WHERE characterId = ? AND state IN $states
              AND id IN (
                  SELECT docid FROM structured_memory_fts
                  WHERE searchTokens MATCH ?
              )
        """.trimIndent()
    }
}

/**
 * Build the FTS4 document representation. Chinese text is segmented into
 * single characters, bigrams and trigrams so a question containing 面试日期
 * can recall a fact such as 面试改到周五 without relying on a dictionary.
 */
internal fun memorySearchTokens(text: String): String {
    if (text.isBlank()) return ""
    val tokens = linkedSetOf<String>()
    Regex("[\\p{IsHan}]+|[a-z0-9]+").findAll(text.lowercase()).forEach { match ->
        val part = match.value
        if (part.first().code in 0x3400..0x9fff) {
            part.forEach { character -> tokens += character.toString() }
            for (size in 2..3) {
                if (part.length < size) continue
                part.windowed(size).forEach(tokens::add)
            }
        } else {
            tokens += part
        }
    }
    return tokens.joinToString(" ")
}

/**
 * Only tokens produced by our own tokenizer reach MATCH. Every token is
 * quoted, so quotes, wildcards and boolean operators in user text cannot
 * become FTS syntax.
 */
internal fun buildMemoryFtsQuery(query: String): String =
    run {
        val tokens = memorySearchTokens(query.take(2_000))
            .split(' ')
            .filter(String::isNotEmpty)
        fun isChinese(token: String) =
            token.first().code in 0x3400..0x9fff
        val ordered = buildList {
            addAll(tokens.filterNot(::isChinese))
            addAll(tokens.filter { isChinese(it) && it.length >= 3 })
            addAll(tokens.filter { isChinese(it) && it.length == 2 })
            if (isEmpty()) addAll(tokens.filter { isChinese(it) && it.length == 1 })
        }
        ordered.asSequence()
            .distinct()
            .take(64)
            .map { "\"" + it.replace("\"", "\"\"") + "\"" }
            .joinToString(" OR ")
    }
