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

internal object OpenCodeRequestPolicy {
    fun fields(
        maxTokens: Int,
        stream: Boolean,
    ): Map<String, Any> = buildMap {
        put("model", OpenCodeClient.MODEL)
        put("max_tokens", maxTokens)
        put("stream", stream)
        put("thinking", mapOf("type" to "disabled"))
        if (stream) {
            put("stream_options", mapOf("include_usage" to true))
        }
    }
}

/** OpenCode Go uses an OpenAI-compatible Chat Completions endpoint. */
class OpenCodeClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun requestBuilder(
        apiKey: String,
        sessionId: String,
        stream: Boolean,
    ): Request.Builder {
        require(apiKey.isNotBlank()) { "请先在设置中填写 OpenCode Go API Key" }
        require(sessionId.isNotBlank()) { "OpenCode 会话标识不能为空" }
        return Request.Builder()
            .url("$BASE_URL/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("User-Agent", USER_AGENT)
            .header("x-opencode-session", sessionId)
            .header("Accept", if (stream) "text/event-stream" else "application/json")
    }

    suspend fun stream(
        apiKey: String,
        messages: JSONArray,
        maxTokens: Int,
        sessionId: String,
        isCancelled: () -> Boolean = { false },
        onDelta: (String) -> Unit,
    ): TokenUsage = withContext(Dispatchers.IO) {
        val payload = JSONObject(
            OpenCodeRequestPolicy.fields(maxTokens, stream = true),
        ).put("messages", messages)
        val request = requestBuilder(apiKey, sessionId, stream = true)
            .post(payload.toString().toRequestBody(JSON))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val error = response.body?.string().orEmpty().take(500)
                throw IOException("OpenCode Go 请求失败（${response.code}）：$error")
            }
            val source = response.body?.source()
                ?: throw IOException("OpenCode Go 未返回内容")
            var usage = TokenUsage(0, 0, 0)
            while (!source.exhausted()) {
                if (isCancelled()) break
                val line = source.readUtf8Line()?.trim().orEmpty()
                if (!line.startsWith("data:")) continue
                val raw = line.removePrefix("data:").trim()
                if (raw == "[DONE]") break
                val event = runCatching { JSONObject(raw) }.getOrNull() ?: continue
                val choices = event.optJSONArray("choices") ?: JSONArray()
                for (index in 0 until choices.length()) {
                    val delta = choices.optJSONObject(index)?.optJSONObject("delta")
                    val text = delta?.optString("content").orEmpty()
                    if (text.isNotEmpty()) onDelta(text)
                }
                event.optJSONObject("usage")?.let {
                    usage = TokenUsage(
                        promptTokens = it.optInt("prompt_tokens"),
                        completionTokens = it.optInt("completion_tokens"),
                        cachedTokens = it.optJSONObject("prompt_tokens_details")
                            ?.optInt("cached_tokens", 0) ?: 0,
                    )
                }
            }
            usage
        }
    }

    suspend fun completeWithUsage(
        apiKey: String,
        messages: JSONArray,
        maxTokens: Int,
        sessionId: String,
        json: Boolean = false,
    ): Pair<String, TokenUsage> = withContext(Dispatchers.IO) {
        // OpenCode Go's atlas gateway may reject response_format=json_object.
        // JSON output is enforced by the prompt for every OpenCode request.
        val payload = JSONObject(
            OpenCodeRequestPolicy.fields(maxTokens, stream = false),
        ).put("messages", messages)
        val request = requestBuilder(apiKey, sessionId, stream = false)
            .post(payload.toString().toRequestBody(JSON))
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("OpenCode Go 请求失败（${response.code}）：$raw")
            }
            val data = JSONObject(raw)
            val text = data
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .optString("content")
            val usage = data.optJSONObject("usage")?.let {
                TokenUsage(
                    promptTokens = it.optInt("prompt_tokens"),
                    completionTokens = it.optInt("completion_tokens"),
                    cachedTokens = it.optJSONObject("prompt_tokens_details")
                        ?.optInt("cached_tokens", 0) ?: 0,
                )
            } ?: TokenUsage(0, 0, 0)
            text to usage
        }
    }

    companion object {
        const val MODEL = "deepseek-v4.1-flash"
        const val BASE_URL = "https://opencode.ai/zen/go/v1"
        const val USER_AGENT = "shiyu-chat/0.21.0"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
