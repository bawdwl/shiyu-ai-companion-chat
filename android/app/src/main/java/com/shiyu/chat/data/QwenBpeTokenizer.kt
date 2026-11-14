package com.shiyu.chat.data

import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.regex.Pattern

/**
 * Minimal Qwen byte-level BPE tokenizer backed by tokenizer.json. It covers the
 * normal text path used by embeddings and deliberately avoids a second native
 * tokenizer dependency.
 */
internal class QwenBpeTokenizer private constructor(
    private val vocabulary: Map<String, Int>,
    mergeRanks: Map<Pair<String, String>, Int>,
    private val endOfTextId: Int,
) {
    private val merges = mergeRanks
    private val byteEncoder = byteLevelEncoder()
    private val splitPattern = Pattern.compile(
        PRETOKEN_PATTERN,
        Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE,
    )

    fun encode(
        text: String,
        maxTokens: Int,
    ): LongArray {
        require(maxTokens >= 2)
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFC)
        val matcher = splitPattern.matcher(normalized)
        val ids = mutableListOf<Int>()
        while (matcher.find()) {
            val piece = matcher.group()
            if (piece.isEmpty()) continue
            encodePiece(piece).forEach { token ->
                if (ids.size >= maxTokens - 1) return@forEach
                vocabulary[token]?.let(ids::add)
            }
            if (ids.size >= maxTokens - 1) break
        }
        ids += endOfTextId
        return LongArray(ids.size) { index -> ids[index].toLong() }
    }

    private fun encodePiece(piece: String): List<String> {
        val mapped = buildString {
            piece.toByteArray(Charsets.UTF_8).forEach { byte ->
                append(byteEncoder[byte.toInt() and 0xff])
            }
        }
        var symbols = mapped.map(Char::toString)
        while (symbols.size > 1) {
            var bestRank = Int.MAX_VALUE
            var bestPair: Pair<String, String>? = null
            for (index in 0 until symbols.lastIndex) {
                val pair = symbols[index] to symbols[index + 1]
                val rank = merges[pair] ?: continue
                if (rank < bestRank) {
                    bestRank = rank
                    bestPair = pair
                }
            }
            val pair = bestPair ?: break
            symbols = mergePair(symbols, pair)
        }
        return symbols.flatMap { symbol ->
            vocabulary[symbol]?.let { listOf(symbol) }
                ?: symbol.map(Char::toString)
        }
    }

    private fun mergePair(
        symbols: List<String>,
        pair: Pair<String, String>,
    ): List<String> {
        val output = mutableListOf<String>()
        var index = 0
        while (index < symbols.size) {
            if (
                index < symbols.lastIndex &&
                symbols[index] == pair.first &&
                symbols[index + 1] == pair.second
            ) {
                output += pair.first + pair.second
                index += 2
            } else {
                output += symbols[index]
                index++
            }
        }
        return output
    }

    companion object {
        private const val EOS_TOKEN = "<|endoftext|>"
        private const val PRETOKEN_PATTERN =
            "(?i:'s|'t|'re|'ve|'m|'ll|'d)|[^\\r\\n\\p{L}\\p{N}]?\\p{L}+|" +
                "\\p{N}| ?[^\\s\\p{L}\\p{N}]+[\\r\\n]*|\\s*[\\r\\n]+|" +
                "\\s+(?!\\S)|\\s+"

        fun fromJson(raw: String): QwenBpeTokenizer {
            val root = JSONObject(raw)
            val model = root.getJSONObject("model")
            val vocabJson = model.getJSONObject("vocab")
            val vocabulary = buildMap {
                vocabJson.keys().forEach { token ->
                    put(token, vocabJson.getInt(token))
                }
            }
            val mergesJson = model.getJSONArray("merges")
            val merges = buildMap {
                for (index in 0 until mergesJson.length()) {
                    val item = mergesJson.optJSONArray(index)
                    val pair = if (item != null) {
                        item.optString(0) to item.optString(1)
                    } else {
                        val parts = mergesJson.optString(index)
                            .trim()
                            .split(Regex("\\s+"))
                        if (parts.size == 2) parts[0] to parts[1] else continue
                    }
                    if (pair.first.isNotBlank() && pair.second.isNotBlank()) {
                        put(pair, index)
                    }
                }
            }
            val addedTokens = root.optJSONArray("added_tokens")
            val eosId = findAddedTokenId(addedTokens, EOS_TOKEN)
                ?: vocabulary[EOS_TOKEN]
                ?: 151643
            return QwenBpeTokenizer(vocabulary, merges, eosId)
        }

        private fun findAddedTokenId(
            tokens: JSONArray?,
            content: String,
        ): Int? {
            if (tokens == null) return null
            for (index in 0 until tokens.length()) {
                val item = tokens.optJSONObject(index) ?: continue
                if (item.optString("content") == content) {
                    return item.optInt("id", -1).takeIf { it >= 0 }
                }
            }
            return null
        }

        internal fun byteLevelEncoder(): Array<Char> {
            val direct = (33..126) + (161..172) + (174..255)
            val mapped = mutableListOf<Int>()
            val chars = mutableListOf<Char>()
            direct.forEach {
                mapped += it
                chars += it.toChar()
            }
            var offset = 0
            for (byte in 0..255) {
                if (byte in mapped) continue
                mapped += byte
                chars += (256 + offset).toChar()
                offset++
            }
            val result = Array(256) { ' ' }
            mapped.forEachIndexed { index, byte ->
                result[byte] = chars[index]
            }
            return result
        }
    }
}
