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
        val payload = JSONObject()
            .put("model", "deepseek-flash")
            .put("messages", messages)
            .put("max_tokens", maxTokens)
            .put("stream", true)
            .put("thinking", JSONObject().put("type", "disabled"))
            .put("stream_options", JSONObject().put("include_usage", true))
        val request = Request.Builder()
            .url("https://api.deepseek.com/chat/completions")
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
        val payload = JSONObject()
            .put("model", "deepseek-flash")
            .put("messages", messages)
            .put("max_tokens", maxTokens)
            .put("thinking", JSONObject().put("type", "disabled"))
        if (json) payload.put("response_format", JSONObject().put("type", "json_object"))
        val request = Request.Builder()
            .url("https://api.deepseek.com/chat/completions")
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
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
