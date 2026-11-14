package com.shiyu.chat.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

class QwenEmbeddingModelManager(private val context: Context) {
    fun modelDirectory(): File {
        externalModelDirectory().takeIf(::isCompleteDirectory)?.let { return it }
        internalModelDirectory().takeIf(::isCompleteDirectory)?.let { return it }
        return externalModelDirectory()
    }

    private fun externalModelDirectory(): File {
        val root = context.getExternalFilesDir(null) ?: context.filesDir
        return File(root, "embedding-models/$MODEL_ID")
    }

    private fun internalModelDirectory(): File =
        File(context.filesDir, "embedding-models/$MODEL_ID")

    fun modelFile(): File = File(modelDirectory(), MODEL_FILE)

    fun tokenizerFile(): File = File(modelDirectory(), "tokenizer.json")

    fun configFile(): File = File(modelDirectory(), "config.json")

    fun tokenizerConfigFile(): File = File(modelDirectory(), "tokenizer_config.json")

    fun isReady(): Boolean {
        return isCompleteDirectory(externalModelDirectory()) ||
            isCompleteDirectory(internalModelDirectory())
    }

    fun installedBytes(): Long =
        modelDirectory().walkTopDown().filter(File::isFile).sumOf(File::length)

    fun modelName(): String = File(modelDirectory(), "display-name.txt")
        .takeIf(File::isFile)
        ?.readText()
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?: "Qwen3-Embedding 0.6B ONNX INT8"

    fun diagnostics(): String =
        listOf(externalModelDirectory(), internalModelDirectory())
            .joinToString("\n") { directory ->
                val files = REQUIRED_FILES.joinToString(", ") { name ->
                    "$name=${File(directory, name).length()}"
                }
                "${directory.absolutePath} ready=${isCompleteDirectory(directory)} $files"
            }

    suspend fun importZip(uri: Uri): File = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri)
            ?: error("无法读取所选 embedding 模型 ZIP")
        installArchive(input)
    }

    suspend fun installDirectory(source: File): File = withContext(Dispatchers.IO) {
        require(source.isDirectory) { "下载的 embedding 模型目录不存在" }
        val sourceRoot = findModelRoot(source)
            ?: error("下载的模型目录中未找到 config.json 和 tokenizer.json")
        validateDirectory(sourceRoot)
        val destination = externalModelDirectory()
        internalModelDirectory().deleteRecursively()
        if (destination.exists()) destination.deleteRecursively()
        destination.parentFile?.mkdirs()
        if (sourceRoot.renameTo(destination)) return@withContext destination

        val root = destination.parentFile ?: error("无法创建模型目录")
        val requiredCopyBytes = sourceRoot.walkTopDown()
            .filter(File::isFile)
            .sumOf(File::length)
        require(allocatableBytes(context, root) >= requiredCopyBytes + COPY_MARGIN_BYTES) {
            "复制 embedding 模型需要约 ${requiredCopyBytes / 1024 / 1024} MiB 额外空间"
        }
        val staging = File(root, ".staging-${System.currentTimeMillis()}")
        staging.mkdirs()
        try {
            copyDirectory(sourceRoot, staging)
            activateStaging(staging, "下载的 embedding 模型")
        } finally {
            staging.deleteRecursively()
        }
    }

    suspend fun deleteModel() = withContext(Dispatchers.IO) {
        listOf(externalModelDirectory(), internalModelDirectory()).forEach { destination ->
            if (destination.exists()) {
                check(destination.deleteRecursively()) { "无法删除 Qwen embedding 模型" }
            }
        }
    }

    private fun installArchive(input: InputStream): File {
        val root = externalModelDirectory().parentFile ?: error("无法创建模型目录")
        root.mkdirs()
        val staging = File(root, ".staging-${System.currentTimeMillis()}")
        staging.mkdirs()
        try {
            input.use {
                ZipInputStream(it.buffered()).use { zip ->
                    var entry = zip.nextEntry
                    var extractedBytes = 0L
                    while (entry != null) {
                        val stagingRoot = staging.canonicalFile
                        val target = File(stagingRoot, entry.name).canonicalFile
                        val isRootDirectory = target == stagingRoot && entry.isDirectory
                        require(
                            isRootDirectory ||
                                target.path.startsWith(stagingRoot.path + File.separator),
                        ) {
                            "embedding 模型 ZIP 包含非法路径"
                        }
                        if (entry.isDirectory) {
                            target.mkdirs()
                        } else {
                            target.parentFile?.mkdirs()
                            target.outputStream().buffered().use { output ->
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                while (true) {
                                    val count = zip.read(buffer)
                                    if (count < 0) break
                                    output.write(buffer, 0, count)
                                    extractedBytes += count
                                    require(extractedBytes <= MAX_EXTRACTED_BYTES) {
                                        "embedding 模型解压后超过 2 GiB"
                                    }
                                }
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
            return activateStaging(staging, "embedding 模型 ZIP")
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun copyDirectory(sourceRoot: File, staging: File) {
        val stagingRoot = staging.canonicalFile
        sourceRoot.walkTopDown().forEach { current ->
            val relative = current.relativeTo(sourceRoot).path
            val target = File(stagingRoot, relative).canonicalFile
            require(
                target == stagingRoot ||
                    target.path.startsWith(stagingRoot.path + File.separator),
            ) {
                "embedding 模型目录包含非法路径"
            }
            if (current.isDirectory) {
                target.mkdirs()
            } else if (current.isFile) {
                target.parentFile?.mkdirs()
                current.inputStream().buffered().use { input ->
                    target.outputStream().buffered().use(input::copyTo)
                }
            }
        }
    }

    private fun activateStaging(staging: File, sourceLabel: String): File {
        val extractedRoot = findModelRoot(staging)
            ?: error("$sourceLabel 中未找到 config.json 和 tokenizer.json")
        validateDirectory(extractedRoot)
        val destination = externalModelDirectory()
        internalModelDirectory().deleteRecursively()
        if (destination.exists()) destination.deleteRecursively()
        destination.parentFile?.mkdirs()
        require(extractedRoot.renameTo(destination)) { "无法安装 Qwen embedding 模型" }
        return destination
    }

    private fun findModelRoot(root: File): File? {
        if (
            File(root, "config.json").isFile &&
            File(root, "tokenizer.json").isFile &&
            File(root, MODEL_FILE).isFile
        ) {
            return root
        }
        return root.walkTopDown().firstOrNull { directory ->
            directory.isDirectory &&
                File(directory, "config.json").isFile &&
                File(directory, "tokenizer.json").isFile &&
                File(directory, MODEL_FILE).isFile
        }
    }

    private fun validateDirectory(directory: File) {
        val missing = REQUIRED_FILES.filterNot { File(directory, it).isFile }
        require(missing.isEmpty()) {
            "Qwen embedding 模型包缺少文件：${missing.joinToString()}"
        }
        val config = JSONObject(File(directory, "config.json").readText())
        require(config.optString("model_type") == "qwen3") {
            "config.json 不是 Qwen3 embedding 配置"
        }
        require(config.optInt("hidden_size", 0) == EMBEDDING_DIMENSION) {
            "Qwen embedding hidden_size 必须为 $EMBEDDING_DIMENSION"
        }
    }

    private fun isCompleteDirectory(directory: File): Boolean {
        if (!REQUIRED_FILES.all { File(directory, it).isFile }) return false
        return File(directory, MODEL_FILE).length() >= MIN_MODEL_BYTES &&
            File(directory, "tokenizer.json").length() >= MIN_TOKENIZER_BYTES
    }

    companion object {
        const val MODEL_ID = "qwen3-embedding-0.6b-int8-v1"
        const val MODEL_VERSION =
            "Qwen/Qwen3-Embedding-0.6B|onnx-int8-v1|max=512|query-prompt-v1"
        const val EMBEDDING_DIMENSION = 1024
        const val MODEL_FILE = "onnx/model_int8.onnx"
        private const val MIN_MODEL_BYTES = 580_000_000L
        private const val MIN_TOKENIZER_BYTES = 10L * 1024 * 1024
        private const val MAX_EXTRACTED_BYTES = 2L * 1024 * 1024 * 1024
        private const val COPY_MARGIN_BYTES = 192L * 1024 * 1024
        private val REQUIRED_FILES = listOf(
            MODEL_FILE,
            "tokenizer.json",
            "tokenizer_config.json",
            "config.json",
        )
    }
}
