package com.shiyu.chat.data

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

class MnnModelManager(private val context: Context) {
    val modelId: String = MODEL_ID

    init {
        val root = context.getExternalFilesDir(null) ?: context.filesDir
        val cutoff = System.currentTimeMillis() - STALE_FILE_MS
        File(root, "models").listFiles()
            ?.filter { it.name.startsWith(".staging-") && it.lastModified() < cutoff }
            ?.forEach { it.deleteRecursively() }
    }

    fun modelDirectory(): File {
        val root = context.getExternalFilesDir(null) ?: context.filesDir
        val current = File(root, "models/$MODEL_ID")
        val legacy = File(root, "models/$LEGACY_MODEL_ID")
        if (!current.exists() && legacy.isDirectory) {
            runCatching {
                if (legacy.renameTo(current)) return@runCatching
                legacy.copyRecursively(current, overwrite = true)
                legacy.deleteRecursively()
            }
        }
        return current
    }

    fun configFile(): File = File(modelDirectory(), "config.json")

    fun isReady(): Boolean {
        val directory = modelDirectory()
        val missing = REQUIRED_FILES.filterNot { File(directory, it).isFile }
        Log.i(
            "MnnModelManager",
            "model_dir=${directory.absolutePath} ready=${missing.isEmpty()} missing=${missing.joinToString()}",
        )
        return missing.isEmpty()
    }

    fun installedBytes(): Long =
        modelDirectory().walkTopDown().filter(File::isFile).sumOf(File::length)

    fun modelName(): String = File(modelDirectory(), "display-name.txt")
        .takeIf(File::isFile)
        ?.readText()
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?: "Qwen3-1.7B MNN"

    suspend fun importZip(uri: Uri): File = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri)
            ?: error("无法读取所选模型 ZIP")
        installArchive(input)
    }

    suspend fun installDirectory(source: File): File = withContext(Dispatchers.IO) {
        require(source.isDirectory) { "下载的 MNN 模型目录不存在" }
        val sourceRoot = findModelRoot(source)
            ?: error("下载的模型目录中未找到 config.json")
        validateDirectory(sourceRoot)
        val destination = modelDirectory()
        if (destination.exists()) destination.deleteRecursively()
        destination.parentFile?.mkdirs()
        if (sourceRoot.renameTo(destination)) return@withContext destination

        val root = modelDirectory().parentFile ?: error("无法创建模型目录")
        val requiredCopyBytes = sourceRoot.walkTopDown()
            .filter(File::isFile)
            .sumOf(File::length)
        require(allocatableBytes(context, root) >= requiredCopyBytes + 64L * 1024 * 1024) {
            "复制 MNN 模型需要约 ${requiredCopyBytes / 1024 / 1024} MiB 额外空间"
        }
        val staging = File(root, ".staging-${System.currentTimeMillis()}")
        staging.mkdirs()
        try {
            val stagingRoot = staging.canonicalFile
            sourceRoot.walkTopDown().forEach { current ->
                val relative = current.relativeTo(sourceRoot).path
                val target = File(stagingRoot, relative).canonicalFile
                require(
                    target == stagingRoot ||
                        target.path.startsWith(stagingRoot.path + File.separator),
                ) {
                    "下载的模型目录包含非法路径"
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
            activateStaging(staging, "下载的模型")
        } finally {
            staging.deleteRecursively()
        }
    }

    suspend fun deleteModel() = withContext(Dispatchers.IO) {
        val destination = modelDirectory()
        if (destination.exists()) {
            check(destination.deleteRecursively()) { "无法删除本地 MNN 模型" }
        }
    }

    private fun installArchive(input: InputStream): File {
        val root = modelDirectory().parentFile ?: error("无法创建模型目录")
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
                            "模型 ZIP 包含非法路径"
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
                                        "模型解压后超过 3 GiB，已停止导入"
                                    }
                                }
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
            return activateStaging(staging, "模型 ZIP")
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun activateStaging(staging: File, sourceLabel: String): File {
        val extractedRoot = findModelRoot(staging)
            ?: error("$sourceLabel 中未找到包含 config.json 的模型目录")
        validateDirectory(extractedRoot)
        val destination = modelDirectory()
        if (destination.exists()) destination.deleteRecursively()
        destination.parentFile?.mkdirs()
        require(extractedRoot.renameTo(destination)) { "无法安装 MNN 模型" }
        return destination
    }

    private fun findModelRoot(root: File): File? {
        if (File(root, "config.json").isFile) return root
        return root.walkTopDown()
            .firstOrNull { it.isDirectory && File(it, "config.json").isFile }
    }

    private fun validateDirectory(directory: File) {
        val missing = REQUIRED_FILES.filterNot { File(directory, it).isFile }
        require(missing.isEmpty()) {
            "MNN 模型包缺少文件：${missing.joinToString()}"
        }
        val llmConfig = File(directory, "config.json").readText()
        require("\"llm_model\"" in llmConfig && "\"llm_weight\"" in llmConfig) {
            "config.json 不是有效的 MNN LLM 配置"
        }
    }

    companion object {
        const val MODEL_ID = "qwen3-1.7b-lora-mnn-q8-local"
        private const val MAX_EXTRACTED_BYTES = 3L * 1024 * 1024 * 1024
        private const val STALE_FILE_MS = 24L * 60 * 60 * 1000
        private const val LEGACY_MODEL_ID =
            "qwen3-1.7b-song-zhiyi-v2-half1-mnn-3.5.0-q8b64"
        private val REQUIRED_FILES = listOf(
            "config.json",
            "llm.mnn",
            "llm.mnn.weight",
            "tokenizer.mtok",
        )
    }
}
