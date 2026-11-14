package com.shiyu.chat.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class ManagedModelDownloader(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun cleanupStaleDownloads() {
        val cutoff = System.currentTimeMillis() - STALE_DOWNLOAD_MS
        val externalRoot = context.getExternalFilesDir(null) ?: context.filesDir
        File(externalRoot, "managed-models").listFiles()
            ?.filter { it.lastModified() < cutoff }
            ?.forEach { it.deleteRecursively() }
        context.cacheDir.listFiles()
            ?.filter {
                (it.name.startsWith("MeinaMixV12_qnn2.28_") || it.name == "managed-models") &&
                    it.lastModified() < cutoff
            }
            ?.forEach { if (it.isDirectory) it.deleteRecursively() else it.delete() }
    }

    suspend fun downloadMnn(
        offer: MnnDownloadOffer,
        onProgress: (label: String, progress: Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val downloadRoot = context.getExternalFilesDir(null) ?: context.filesDir
        val root = File(downloadRoot, "managed-models/${offer.id}")
        root.deleteRecursively()
        root.mkdirs()
        try {
            val manifestUrl =
                "https://modelscope.cn/models/${offer.repoId}/resolve/master/MANIFEST.json"
            val manifestFile = File(root, "MANIFEST.json")
            downloadUrl(manifestUrl, manifestFile, null, "读取模型清单", onProgress)
            val rootJson = JSONObject(manifestFile.readText())
            val files = rootJson.getJSONArray("files")
            val metadata = (0 until files.length()).associate { index ->
                val item = files.getJSONObject(index)
                item.getString("path") to item
            }
            val required = listOf(
                "config.json",
                "llm_config.json",
                "llm.mnn",
                "llm.mnn.json",
                "llm.mnn.weight",
                "tokenizer.mtok",
                "SYSTEM_PROMPT.txt",
            )
            val total = required.sumOf { name ->
                val item = metadata[name] ?: return@sumOf 0L
                item.optLong("size", item.optLong("bytes", 0L))
            }.coerceAtLeast(1L)
            ensureFreeSpace(root, total, "MNN 模型")
            var completed = 0L
            required.forEach { name ->
                val item = metadata[name] ?: error("远端模型清单缺少 $name")
                val expectedSize = item.optLong("size", item.optLong("bytes", 0L))
                val expectedHash = item.optString("sha256").lowercase()
                val target = File(root, name)
                downloadUrl(
                    "https://modelscope.cn/models/${offer.repoId}/resolve/master/$name",
                    target,
                    expectedHash.takeIf(String::isNotBlank),
                    name,
                ) { label, itemProgress ->
                    onProgress(
                        label,
                        ((completed + itemProgress * expectedSize) / total)
                            .toFloat()
                            .coerceIn(0f, 1f),
                    )
                }
                require(expectedSize <= 0 || target.length() == expectedSize) {
                    "$name 下载大小不正确"
                }
                completed += expectedSize
                onProgress(name, (completed.toFloat() / total).coerceIn(0f, 1f))
            }
            File(root, "display-name.txt").writeText(offer.displayName)
            root
        } catch (error: Throwable) {
            root.deleteRecursively()
            throw error
        }
    }

    suspend fun downloadDream(
        compatibility: DeviceModelCompatibility,
        onProgress: (label: String, progress: Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val url = compatibility.dreamDownloadUrl
            ?: error("当前设备不支持 Local Dream SD1.5 NPU")
        val suffix = compatibility.dreamPackageSuffix
            ?: error("无法确定 MeinaMix 模型包")
        val (size, hash) = when (suffix) {
            "min" -> 1_156_786_441L to
                "7ed33339b305f0df97fd746790962808c38e56bb98e560d55d8eb239fa6c4dc8"
            "8gen1" -> 1_032_314_916L to
                "10d9f52607146c85428d3f66919a8a86ccf8f680c21b51ef2a39c6c0ff1b2695"
            "8gen2" -> 1_031_765_133L to
                "90463ac0db6eb7424bb6c7a048c811bde2996b2321eaf58b64d02d626c333098"
            else -> error("未知 MeinaMix 包：$suffix")
        }
        val output = File(context.cacheDir, "MeinaMixV12_qnn2.28_$suffix.zip")
        ensureFreeSpace(context.cacheDir, size * 2, "MeinaMix V12")
        try {
            downloadUrl(url, output, hash, "MeinaMix V12", onProgress)
            require(output.length() == size) { "MeinaMix V12 ZIP 大小不正确" }
            output
        } catch (error: Throwable) {
            output.delete()
            File(output.parentFile, "${output.name}.part").delete()
            throw error
        }
    }

    suspend fun downloadQwenEmbedding(
        onProgress: (label: String, progress: Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val downloadRoot = context.getExternalFilesDir(null) ?: context.filesDir
        val root = File(
            downloadRoot,
            "managed-models/${QwenEmbeddingModelManager.MODEL_ID}",
        )
        root.deleteRecursively()
        root.mkdirs()
        val files = listOf(
            EmbeddingModelFile(
                path = "onnx/model_int8.onnx",
                size = 613_527_539L,
                sha256 = "6D0EA863F78B4A84AFA3C7FCBA1EC341572B5E28121AEF77B7092B1DFDF679C7",
            ),
            EmbeddingModelFile(
                path = "tokenizer.json",
                size = 11_423_705L,
                sha256 = "DEF76FB086971C7867B829C23A26261E38D9D74E02139253B38AEB9DF8B4B50A",
            ),
            EmbeddingModelFile(
                path = "config.json",
                size = 1_576L,
                sha256 = "66A10929782F3C9A3CD5DEC90E2A95C60E05736134A63CD54479EEAE80BED175",
            ),
            EmbeddingModelFile(
                path = "tokenizer_config.json",
                size = 9_731L,
                sha256 = "977648852447CB6587327FF3205B0A84CF2FC9F05621D6C8E88A497CAAFAB2E1",
            ),
        )
        val total = files.sumOf { it.size }.coerceAtLeast(1L)
        ensureFreeSpace(root, total, "Qwen3-Embedding 0.6B")
        var completed = 0L
        try {
            files.forEach { item ->
                val target = File(root, item.path)
                downloadUrl(
                    url = "${QWEN_EMBEDDING_BASE_URL}${item.path}",
                    output = target,
                    expectedSha256 = item.sha256,
                    label = item.path.substringAfterLast('/'),
                ) { label, itemProgress ->
                    onProgress(
                        label,
                        ((completed + itemProgress * item.size) / total)
                            .toFloat()
                            .coerceIn(0f, 1f),
                    )
                }
                require(target.length() == item.size) {
                    "${item.path} 下载大小不正确"
                }
                completed += item.size
                onProgress(
                    item.path.substringAfterLast('/'),
                    (completed.toFloat() / total).coerceIn(0f, 1f),
                )
            }
            File(root, "display-name.txt")
                .writeText("Qwen3-Embedding 0.6B ONNX INT8")
            root
        } catch (error: Throwable) {
            root.deleteRecursively()
            throw error
        }
    }

    private fun downloadUrl(
        url: String,
        output: File,
        expectedSha256: String?,
        label: String,
        onProgress: (label: String, progress: Float) -> Unit,
    ) {
        val temporary = File(output.parentFile, "${output.name}.part")
        temporary.parentFile?.mkdirs()
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("下载失败（HTTP ${response.code}）：$url")
            }
            val body = response.body ?: throw IOException("下载内容为空：$url")
            val total = body.contentLength().takeIf { it > 0 } ?: 0L
            val digest = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L
            var lastReported = -1f
            body.byteStream().use { input ->
                temporary.outputStream().buffered().use { stream ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        stream.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        downloaded += count
                        if (total > 0) {
                            val progress = downloaded.toFloat() / total
                            if (progress - lastReported >= 0.002f || downloaded == total) {
                                lastReported = progress
                                onProgress(label, progress)
                            }
                        }
                    }
                }
            }
            if (expectedSha256 != null) {
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                require(actual.equals(expectedSha256, ignoreCase = true)) {
                    "$label SHA-256 校验失败"
                }
            }
        }
        if (output.exists()) output.delete()
        check(temporary.renameTo(output)) { "无法保存下载文件：$label" }
    }

    private fun ensureFreeSpace(directory: File, requiredBytes: Long, label: String) {
        val available = allocatableBytes(context, directory)
        require(available >= requiredBytes + DOWNLOAD_MARGIN_BYTES) {
            "$label 需要约 ${formatGiB(requiredBytes)} GiB 空闲存储，" +
                "当前仅 ${formatGiB(available)} GiB"
        }
    }

    private fun formatGiB(bytes: Long): String =
        String.format(java.util.Locale.CHINA, "%.2f", bytes.toDouble() / 1024 / 1024 / 1024)

    companion object {
        private const val DOWNLOAD_MARGIN_BYTES = 320L * 1024 * 1024
        private const val STALE_DOWNLOAD_MS = 24L * 60 * 60 * 1000
        private const val QWEN_EMBEDDING_BASE_URL =
            "https://huggingface.co/onnx-community/Qwen3-Embedding-0.6B-ONNX/resolve/main/"
    }
}

private data class EmbeddingModelFile(
    val path: String,
    val size: Long,
    val sha256: String,
)
