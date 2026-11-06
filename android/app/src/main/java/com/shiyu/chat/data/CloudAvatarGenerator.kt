package com.shiyu.chat.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Beijing-region text-to-image API; image retrieval never receives the API key. */
class CloudAvatarGenerator(private val context: Context) {
    private val api = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(240, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()
    private val downloads = api.newBuilder().readTimeout(60, TimeUnit.SECONDS).build()

    suspend fun generate(
        character: CharacterEntity,
        apiKey: String,
        onProgress: (Float) -> Unit = {},
    ): String = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "请先在设置中填写千问 AI 平台 API Key" }
        onProgress(0.05f)
        val description = listOf(
            character.appearance,
            character.description,
            character.personality,
            character.era,
        )
            .filter(String::isNotBlank).joinToString("；").take(600)
        val prompt = "请依据以下角色资料画一张适合手机全屏背景的竖版单人角色插画，" +
            "完整人物或大部分身体入镜，站立姿态，人物居中，脸部位于画面上半部分，" +
            "背景与角色故事一致，画面干净、五官清晰，不含文字或水印。" +
            "角色名：${character.name.take(60)}。角色资料：$description"
        val payload = JSONObject()
            .put("model", "qwen-image-2.0")
            .put("input", JSONObject().put("messages", JSONArray().put(
                JSONObject().put("role", "user").put("content", JSONArray().put(JSONObject().put("text", prompt)))
            )))
            .put(
                "parameters",
                JSONObject()
                    .put("size", "768*1344")
                    .put("n", 1)
                    .put("prompt_extend", true),
            )
        val request = Request.Builder()
            .url("https://maas.qianwenaiapi.com/api/v1/services/aigc/multimodal-generation/generation")
            .header("Authorization", "Bearer $apiKey")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        onProgress(0.15f)
        val imageUrl = api.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching { JSONObject(body).optString("message").take(200) }.getOrDefault("")
                throw IOException("千问 AI 平台生图失败（${response.code}）${if (message.isNotBlank()) "：$message" else ""}")
            }
            val root = JSONObject(body)
            val content = root.getJSONObject("output").getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getJSONArray("content")
            (0 until content.length()).mapNotNull { content.optJSONObject(it)?.optString("image")?.takeIf(String::isNotBlank) }
                .firstOrNull() ?: error("千问 AI 平台没有返回图片地址；请查看千问 AI 平台账单后再重试")
        }
        onProgress(0.70f)
        val url = imageUrl.toHttpUrlOrNull() ?: error("千问 AI 平台返回的图片地址无效")
        require(url.isHttps && url.port == 443 && url.username.isEmpty() && url.password.isEmpty() &&
            url.host.startsWith("dashscope-") && url.host.endsWith(".aliyuncs.com")) { "千问 AI 平台返回的图片地址不受支持" }
        val bytes = downloads.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("图片已生成，但下载失败（${response.code}）；请勿重复点击以免再次计费")
            val body = response.body ?: error("图片已生成，但下载内容为空；请勿重复点击以免再次计费")
            require(body.contentLength() <= MAX_IMAGE_BYTES) { "图片已生成，但文件过大；请勿重复点击以免再次计费" }
            val source = body.source()
            val buffer = okio.Buffer()
            while (true) {
                val count = source.read(buffer, 8192)
                if (count == -1L) break
                require(buffer.size <= MAX_IMAGE_BYTES) { "图片已生成，但文件过大；请勿重复点击以免再次计费" }
            }
            buffer.readByteArray()
        }
        onProgress(0.90f)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        require(options.outWidth in 1..4096 && options.outHeight in 1..4096) { "图片已生成，但格式无效；请勿重复点击以免再次计费" }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("图片已生成，但解析失败；请勿重复点击以免再次计费")
        val portrait = CharacterArtworkPolicy.toPortrait(bitmap)
        val directory = File(context.filesDir, "character-avatars").apply { mkdirs() }
        val safeId = character.id.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val output = File(directory, "$safeId-cloud.webp")
        val temporary = File.createTempFile("cloud-avatar-", ".tmp", directory)
        try {
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                Bitmap.CompressFormat.WEBP
            }
            temporary.outputStream().buffered().use { check(portrait.compress(format, 92, it)) }
            check(temporary.renameTo(output)) { "图片已生成，但保存失败；请勿重复点击以免再次计费" }
        } finally {
            temporary.delete()
            portrait.recycle()
            bitmap.recycle()
        }
        output.absolutePath
            .also { onProgress(1f) }
    }

    companion object { private const val MAX_IMAGE_BYTES = 15L * 1024 * 1024 }
}
