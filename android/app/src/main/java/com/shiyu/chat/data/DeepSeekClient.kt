package com.shiyu.chat.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

internal object DeepSeekRequestPolicy {
    const val MODEL = "deepseek-chat"

    fun fields(
        maxTokens: Int,
        stream: Boolean,
        json: Boolean,
    ): Map<String, Any> = buildMap {
        put("model", MODEL)
        put("max_tokens", maxTokens)
        put("stream", stream)
        if (stream) {
            put("stream_options", mapOf("include_usage" to true))
        }
        if (json) {
            put("response_format", mapOf("type" to "json_object"))
        }
    }
}

class DeepSeekClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun stream(
        apiKey: String,
        messages: JSONArray,
        maxTokens: Int,
        isCancelled: () -> Boolean = { false },
        onDelta: (String) -> Unit,
    ): TokenUsage = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "请先在设置中填写 DeepSeek API Key" }
        val payload = buildPayload(messages, maxTokens, stream = true)
        val request = Request.Builder()
            .url("$BASE_URL/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "text/event-stream")
            .post(payload.toString().toRequestBody(JSON))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val error = response.body?.string().orEmpty().take(500)
                throw IOException("DeepSeek 请求失败（${response.code}）：$error")
            }
            val source = response.body?.source() ?: throw IOException("DeepSeek 未返回内容")
            var usage = TokenUsage(0, 0, 0)
            while (!source.exhausted()) {
                if (isCancelled()) break
                val line = source.readUtf8Line()?.trim().orEmpty()
                if (!line.startsWith("data:")) continue
                val raw = line.removePrefix("data:").trim()
                if (raw == "[DONE]") break
                val event = runCatching { JSONObject(raw) }.getOrNull() ?: continue
                val choices = event.optJSONArray("choices") ?: JSONArray()
                for (i in 0 until choices.length()) {
                    val delta = choices.optJSONObject(i)?.optJSONObject("delta")
                    val text = delta?.optString("content").orEmpty()
                    if (text.isNotEmpty()) onDelta(text)
                }
                event.optJSONObject("usage")?.let {
                    usage = TokenUsage(
                        promptTokens = it.optInt("prompt_tokens"),
                        completionTokens = it.optInt("completion_tokens"),
                        cachedTokens = it.optInt("prompt_cache_hit_tokens"),
                    )
                }
            }
            usage
        }
    }

    suspend fun complete(
        apiKey: String,
        messages: JSONArray,
        maxTokens: Int,
        json: Boolean = false,
    ): String = completeWithUsage(apiKey, messages, maxTokens, json).first

    suspend fun completeWithUsage(
        apiKey: String,
        messages: JSONArray,
        maxTokens: Int,
        json: Boolean = false,
    ): Pair<String, TokenUsage> = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "请先在设置中填写 DeepSeek API Key" }
        val payload = buildPayload(messages, maxTokens, stream = false, json = json)
        val request = Request.Builder()
            .url("$BASE_URL/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(payload.toString().toRequestBody(JSON))
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("DeepSeek 请求失败（${response.code}）：$raw")
            val payload = JSONObject(raw)
            val text = payload
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .optString("content")
            val usage = payload.optJSONObject("usage")?.let {
                TokenUsage(
                    promptTokens = it.optInt("prompt_tokens"),
                    completionTokens = it.optInt("completion_tokens"),
                    cachedTokens = it.optInt("prompt_cache_hit_tokens"),
                )
            } ?: TokenUsage(0, 0, 0)
            text to usage
        }
    }

    companion object {
        const val MODEL = DeepSeekRequestPolicy.MODEL
        const val BASE_URL = "https://api.deepseek.com"

        internal fun buildPayload(
            messages: JSONArray,
            maxTokens: Int,
            stream: Boolean,
            json: Boolean = false,
        ): JSONObject = JSONObject(DeepSeekRequestPolicy.fields(maxTokens, stream, json))
            .put("messages", messages)

        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
