package com.shiyu.chat.data

import android.content.Context
import android.net.Uri
import com.shiyu.chat.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.BufferedInputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

class KokoroPackManager(private val context: Context) {
    val bundled: Boolean = context.resources.getBoolean(R.bool.kokoro_bundled)

    private val speechRoot = File(context.filesDir, "speech")
    private val modelDirectory = File(speechRoot, "kokoro")
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    init {
        val cutoff = System.currentTimeMillis() - STALE_FILE_MS
        speechRoot.listFiles()
            ?.filter { it.name.startsWith(".kokoro-staging-") && it.lastModified() < cutoff }
            ?.forEach { it.deleteRecursively() }
        File(context.cacheDir, "kokoro-int8-multi-lang-v1_1.tar.bz2.part")
            .takeIf { it.isFile && it.lastModified() < cutoff }
            ?.delete()
    }

    fun isReady(): Boolean = bundled || requiredFiles().all { File(modelDirectory, it).isFile }

    fun modelDirectory(): File = modelDirectory

    fun installedBytes(): Long = if (!bundled && modelDirectory.isDirectory) {
        modelDirectory.walkTopDown().filter(File::isFile).sumOf(File::length)
    } else {
        0L
    }

    suspend fun downloadAndInstall(
        onProgress: (label: String, progress: Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        require(!bundled) { "完整版已内置 Kokoro 音色包" }
        val output = File(context.cacheDir, "kokoro-int8-multi-lang-v1_1.tar.bz2")
        try {
            download(output, onProgress)
            installArchive(output.inputStream(), "tar")
        } finally {
            output.delete()
            File(context.cacheDir, "$output.name.part").delete()
        }
    }

    suspend fun importUri(uri: Uri): File = withContext(Dispatchers.IO) {
        require(!bundled) { "完整版已内置 Kokoro 音色包" }
        val rawInput = context.contentResolver.openInputStream(uri)
            ?: error("无法读取语音包")
        val input = BufferedInputStream(rawInput)
        input.mark(4)
        val magic = ByteArray(3)
        val count = input.read(magic)
        input.reset()
        val archiveType = when {
            count >= 2 && magic[0] == 'P'.code.toByte() && magic[1] == 'K'.code.toByte() -> "zip"
            count >= 3 && magic[0] == 'B'.code.toByte() &&
                magic[1] == 'Z'.code.toByte() && magic[2] == 'h'.code.toByte() -> "tar"
            else -> error("无法识别语音包格式，请选择 ZIP 或 TAR.BZ2 文件")
        }
        installArchive(input, archiveType)
    }

    suspend fun deleteInstalled() = withContext(Dispatchers.IO) {
        require(!bundled) { "完整版内置音色包不能删除" }
        if (modelDirectory.exists()) {
            check(modelDirectory.deleteRecursively()) { "无法删除 Kokoro 音色包" }
        }
    }

    private fun download(output: File, onProgress: (String, Float) -> Unit) {
        val temporary = File(output.parentFile, "${output.name}.part")
        temporary.delete()
        val request = Request.Builder().url(PACK_URL).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("语音包下载失败（HTTP ${response.code}）")
            }
            val body = response.body ?: throw IOException("语音包下载内容为空")
            val total = body.contentLength()
            var downloaded = 0L
            var lastReported = -1f
            body.byteStream().use { input ->
                temporary.outputStream().buffered().use { stream ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        stream.write(buffer, 0, count)
                        downloaded += count
                        if (total > 0) {
                            val progress = downloaded.toFloat() / total
                            if (progress - lastReported >= 0.002f || downloaded == total) {
                                lastReported = progress
                                onProgress("下载 Kokoro 语音包", progress)
                            }
                        }
                    }
                }
            }
        }
        if (output.exists()) output.delete()
        check(temporary.renameTo(output)) { "无法保存 Kokoro 语音包" }
    }

    private fun installArchive(input: InputStream, archiveType: String): File {
        speechRoot.mkdirs()
        extractedBytes = 0L
        val staging = File(speechRoot, ".kokoro-staging-${System.currentTimeMillis()}")
        staging.mkdirs()
        try {
            input.use { source ->
                when (archiveType) {
                    "zip" -> extractZip(source, staging)
                    "tar" -> extractTarBz2(source, staging)
                    else -> error("未知语音包格式")
                }
            }
            val extracted = findModelRoot(staging)
                ?: error("语音包中未找到 model.int8.onnx")
            validate(extracted)
            if (modelDirectory.exists()) {
                check(modelDirectory.deleteRecursively()) { "无法替换旧 Kokoro 音色包" }
            }
            require(extracted.renameTo(modelDirectory)) { "无法启用 Kokoro 音色包" }
            return modelDirectory
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun extractZip(input: InputStream, staging: File) {
        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val target = safeTarget(staging, entry.name)
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().buffered().use { output ->
                        copyLimited(zip, output)
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    private fun extractTarBz2(input: InputStream, staging: File) {
        TarArchiveInputStream(BZip2CompressorInputStream(input.buffered())).use { tar ->
            var entry = tar.nextTarEntry
            while (entry != null) {
                require(!entry.isSymbolicLink && !entry.isLink) {
                    "语音包不能包含链接文件"
                }
                val target = safeTarget(staging, entry.name)
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().buffered().use { output ->
                        copyLimited(tar, output)
                    }
                }
                entry = tar.nextTarEntry
            }
        }
    }

    private fun copyLimited(input: InputStream, output: java.io.OutputStream) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
            extractedBytes += count
            require(extractedBytes <= MAX_EXTRACTED_BYTES) {
                "Kokoro 语音包解压后超过 400 MiB"
            }
        }
    }

    private var extractedBytes = 0L

    private fun safeTarget(root: File, entryName: String): File {
        val normalized = entryName.replace('\\', '/').trimStart('/')
        require(normalized.isNotBlank() && !normalized.split('/').contains("..")) {
            "语音包包含非法路径"
        }
        val rootPath = root.canonicalFile
        val target = File(rootPath, normalized).canonicalFile
        require(target.path == rootPath.path ||
            target.path.startsWith(rootPath.path + File.separator)) {
            "语音包路径越界"
        }
        return target
    }

    private fun findModelRoot(staging: File): File? {
        if (File(staging, "model.int8.onnx").isFile) return staging
        return staging.walkTopDown().firstOrNull {
            it.isDirectory && File(it, "model.int8.onnx").isFile
        }
    }

    private fun validate(directory: File) {
        val missingFiles = requiredFiles().filterNot { File(directory, it).isFile }
        require(missingFiles.isEmpty()) {
            "Kokoro 语音包缺少文件：${missingFiles.joinToString()}"
        }
        require(File(directory, "espeak-ng-data").isDirectory) {
            "Kokoro 语音包缺少 espeak-ng-data"
        }
        require(File(directory, "dict").isDirectory) {
            "Kokoro 语音包缺少 dict"
        }
    }

    companion object {
        const val PACK_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/" +
                "tts-models/kokoro-int8-multi-lang-v1_1.tar.bz2"
        private const val MAX_EXTRACTED_BYTES = 400L * 1024 * 1024
        private const val STALE_FILE_MS = 24L * 60 * 60 * 1000

        private fun requiredFiles() = listOf(
            "model.int8.onnx",
            "voices.bin",
            "tokens.txt",
            "lexicon-us-en.txt",
            "lexicon-zh.txt",
            "date-zh.fst",
            "number-zh.fst",
            "phone-zh.fst",
        )
    }
}
