package com.shiyu.chat.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Adapter around the Local Dream SD1.5 NPU process bundled in this app.
 * It imports the QNN model package directly and talks to the private loopback
 * backend, so the user never has to install or switch to another app.
 */
class DreamAvatarGenerator(private val context: Context) {
    private val modelRoot = File(context.filesDir, "dream-models")
    private val modelDirectory = File(modelRoot, MODEL_ID)
    private val avatarDirectory = File(context.filesDir, "character-avatars")
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .callTimeout(6, TimeUnit.MINUTES)
        .build()

    init {
        val cutoff = System.currentTimeMillis() - STALE_FILE_MS
        modelRoot.listFiles()
            ?.filter { it.name.startsWith("staging-") && it.lastModified() < cutoff }
            ?.forEach { it.deleteRecursively() }
        avatarDirectory.listFiles()
            ?.filter { it.name.startsWith("cloud-avatar-") && it.lastModified() < cutoff }
            ?.forEach { it.delete() }
    }

    fun isModelReady(): Boolean = REQUIRED_FILES.all {
        File(modelDirectory, it).isFile
    }

    fun modelBytes(): Long = modelDirectory.walkTopDown()
        .filter(File::isFile)
        .sumOf(File::length)

    fun modelName(): String = File(modelDirectory, "display-name.txt")
        .takeIf(File::isFile)
        ?.readText()
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?: DEFAULT_MODEL_NAME

    fun modelRootDirectory(): File = modelDirectory

    suspend fun importModel(uri: Uri) = withContext(Dispatchers.IO) {
        importModelInternal(resolveDisplayName(uri)) {
            context.contentResolver.openInputStream(uri)
                ?: error("无法读取模型压缩包")
        }
    }

    suspend fun importModel(file: File) = withContext(Dispatchers.IO) {
        importModelInternal(file.nameWithoutExtension) {
            file.inputStream().buffered()
        }
    }

    private fun importModelInternal(
        displayName: String,
        openInput: () -> InputStream,
    ) {
        DreamBackendService.stop(context)
        modelRoot.mkdirs()
        val staging = File(modelRoot, "staging-${System.currentTimeMillis()}")
        staging.deleteRecursively()
        staging.mkdirs()
        try {
            var extracted = 0L
            ZipInputStream(openInput().buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory) {
                        val name = entry.name
                            .replace('\\', '/')
                            .substringAfterLast('/')
                            .trim()
                        require(name.isNotEmpty() && name != "." && name != "..") {
                            "模型压缩包包含非法文件名"
                        }
                        require(!name.startsWith(".") && !name.startsWith("__MACOSX")) {
                            "模型压缩包包含系统临时文件"
                        }
                        val output = File(staging, name)
                        output.outputStream().buffered().use { stream ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val read = zip.read(buffer)
                                if (read < 0) break
                                stream.write(buffer, 0, read)
                                extracted += read
                                require(extracted <= MAX_EXTRACTED_BYTES) {
                                    "模型解压后超过 3 GiB，已停止导入"
                                }
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }

            val missing = REQUIRED_FILES.filterNot { File(staging, it).isFile }
            require(missing.isEmpty()) {
                "这不是兼容的 SD1.5 QNN 模型包，缺少：${missing.joinToString()}"
            }
            File(staging, "npucustom").createNewFile()
            File(staging, "display-name.txt").writeText(displayName)

            require(modelDirectory.parentFile?.canonicalFile == modelRoot.canonicalFile) {
                "模型目录校验失败"
            }
            modelDirectory.deleteRecursively()
            check(staging.renameTo(modelDirectory)) {
                "无法启用 MeinaMix V12 模型"
            }
        } catch (error: Throwable) {
            staging.deleteRecursively()
            throw error
        }
    }

    suspend fun deleteModel() = withContext(Dispatchers.IO) {
        DreamBackendService.stop(context)
        require(modelDirectory.parentFile?.canonicalFile == modelRoot.canonicalFile) {
            "模型目录校验失败"
        }
        if (modelDirectory.exists()) {
            check(modelDirectory.deleteRecursively()) { "无法删除本地生图模型" }
        }
    }

    suspend fun generate(
        character: CharacterEntity,
        settings: DreamGenerationSettings,
    ): String {
        check(isModelReady()) {
            "请先在设置中导入 Local Dream 兼容的 SD1.5 NPU 模型 ZIP"
        }
        require(character.name.isNotBlank()) { "角色名称不能为空" }
        File(context.filesDir, DreamBackendService.BACKEND_ERROR_FILE).delete()
        DreamBackendService.start(context, modelDirectory)
        return withContext(Dispatchers.IO) {
            awaitBackend()
            val normalized = settings.normalized()
            val prompt = compactCommaPromptToLimit(
                normalized.buildPrompt(character),
                CLIP_TOKEN_LIMIT,
                ::tokenCount,
            )
            val negativePrompt = compactCommaPromptToLimit(
                normalized.negativePrompt,
                CLIP_TOKEN_LIMIT,
                ::tokenCount,
            )
            val payload = JSONObject()
                .put("prompt", prompt)
                .put("negative_prompt", negativePrompt)
                .put("steps", normalized.steps)
                .put("cfg", normalized.cfg.toDouble())
                .put("seed", normalized.seed)
                .put("scheduler", normalized.scheduler)
                .put("size", 512)
                .put("show_diffusion_process", false)
                .toString()
            val request = Request.Builder()
                .url("$BASE_URL/generate")
                .header("Accept", "text/event-stream")
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val detail = response.body?.string()?.take(500).orEmpty()
                    throw IOException(
                        "本地生图失败（HTTP ${response.code}）" +
                            if (detail.isBlank()) "" else "：$detail",
                    )
                }
                val source = response.body?.source()
                    ?: throw IOException("本地生图引擎没有返回响应")
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.substringAfter("data:").trim()
                    if (data.isBlank() || data == "[DONE]") continue
                    val event = JSONObject(data)
                    when (event.optString("type")) {
                        "error" -> throw IOException(
                            event.optString("message", "本地生图失败"),
                        )
                        "complete" -> return@withContext saveRawRgb(event, character.id)
                    }
                }
            }
            throw IOException("本地生图引擎在完成前结束")
        }
    }

    fun deleteAvatar(path: String?) {
        path ?: return
        runCatching {
            val file = File(path).canonicalFile
            file.relativeTo(avatarDirectory.canonicalFile)
            file.delete()
        }
    }

    fun deleteCharacterAvatars(id: String) {
        val safeId = id.replace(Regex("[^A-Za-z0-9._-]"), "_")
        listOf(
            "$safeId.jpg",
            "$safeId.webp",
            "$safeId-original.webp",
            "$safeId-translated.webp",
            "$safeId-cloud.webp",
            "$safeId-cloud.jpg",
        ).forEach { deleteAvatar(File(avatarDirectory, it).absolutePath) }
    }

    private suspend fun awaitBackend() {
        repeat(BACKEND_READY_ATTEMPTS) {
            if (backendHealthy()) return
            val errorFile = File(
                context.filesDir,
                DreamBackendService.BACKEND_ERROR_FILE,
            )
            if (errorFile.isFile) {
                throw IOException(
                    errorFile.readText().trim().ifBlank { "本地生图引擎启动失败" },
                )
            }
            delay(BACKEND_READY_DELAY_MS)
        }
        throw IOException("本地生图引擎启动超时。请确认手机支持 Snapdragon NPU，并重新导入模型")
    }

    private fun resolveDisplayName(uri: Uri): String {
        val picked = runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
        return (picked ?: uri.lastPathSegment.orEmpty())
            .substringBeforeLast('/')
            .substringBeforeLast('.')
            .replace('_', ' ')
            .replace('-', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(80)
            .ifBlank { DEFAULT_MODEL_NAME }
    }

    private fun backendHealthy(): Boolean = runCatching {
        val request = Request.Builder().url("$BASE_URL/health").get().build()
        httpClient.newCall(request).execute().use { it.isSuccessful }
    }.getOrDefault(false)

    private fun tokenCount(text: String): Int {
        val payload = JSONObject().put("prompt", text).toString()
        val request = Request.Builder()
            .url("$BASE_URL/tokenize")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("本地生图 Token 检查失败（HTTP ${response.code}）")
            }
            JSONObject(response.body?.string().orEmpty()).optInt("count")
        }
    }

    private fun saveRawRgb(event: JSONObject, characterId: String): String {
        val width = event.optInt("width", 512)
        val height = event.optInt("height", 512)
        val channels = event.optInt("channels", 3)
        require(width > 0 && height > 0) { "本地模型返回了无效尺寸" }
        require(channels == 3) { "本地模型返回了不支持的通道数：$channels" }
        val raw = Base64.getDecoder().decode(event.getString("image"))
        require(raw.size == width * height * channels) {
            "本地模型返回的图片数据长度不正确"
        }
        val pixels = IntArray(width * height)
        var source = 0
        for (index in pixels.indices) {
            val red = raw[source++].toInt() and 0xff
            val green = raw[source++].toInt() and 0xff
            val blue = raw[source++].toInt() and 0xff
            pixels[index] = (0xff shl 24) or (red shl 16) or (green shl 8) or blue
        }
        val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        return try {
            avatarDirectory.mkdirs()
            val safeId = characterId.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val output = File(avatarDirectory, "$safeId.jpg")
            val temporary = File.createTempFile("dream-avatar-", ".tmp", avatarDirectory)
            try {
                temporary.outputStream().buffered().use { stream ->
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, 94, stream)) {
                        "头像文件写入失败"
                    }
                }
                if (output.exists()) output.delete()
                check(temporary.renameTo(output)) { "头像保存失败" }
                output.absolutePath
            } finally {
                temporary.delete()
            }
        } finally {
            bitmap.recycle()
        }
    }

    companion object {
        private const val MODEL_ID = "meinamix-v12"
        private const val DEFAULT_MODEL_NAME = "Dream SD1.5"
        private const val BASE_URL = "http://127.0.0.1:${DreamBackendService.PORT}"
        private const val MAX_EXTRACTED_BYTES = 3L * 1024 * 1024 * 1024
        private const val STALE_FILE_MS = 24L * 60 * 60 * 1000
        private const val BACKEND_READY_ATTEMPTS = 120
        private const val BACKEND_READY_DELAY_MS = 750L
        private const val CLIP_TOKEN_LIMIT = 77
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val REQUIRED_FILES = setOf(
            "tokenizer.json",
            "vae_encoder.bin",
            "vae_decoder.bin",
            "unet.bin",
            "clip_v2.mnn",
            "pos_emb.bin",
            "token_emb.bin",
        )
    }
}
