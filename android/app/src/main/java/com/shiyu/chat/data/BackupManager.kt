package com.shiyu.chat.data

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.room.withTransaction
import com.shiyu.chat.MainActivity
import com.shiyu.chat.ShiyuApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal data class BackupOperationSummary(
    val bytes: Long,
    val entryCount: Int,
)

internal data class RestoreResult(
    val success: Boolean,
    val message: String,
)

internal class BackupManager(
    private val context: Context,
    private val database: AppDatabase,
) {
    suspend fun exportBackup(uri: Uri): BackupOperationSummary = withContext(Dispatchers.IO) {
        val work = createWorkDirectory("backup-export")
        try {
            val snapshot = File(work, "snapshot").apply { mkdirs() }
            database.openHelper.writableDatabase
                .query("PRAGMA wal_checkpoint(FULL)")
                .use { it.moveToFirst() }
            database.withTransaction {
                copyDatabaseFiles(
                    requireNotNull(
                        context.getDatabasePath(DATA_NOTES_DATABASE_NAME).parentFile,
                    ),
                    File(snapshot, "database"),
                )
            }
            validateDatabase(
                File(snapshot, BACKUP_DATABASE_PATH),
                maximumVersion = APP_DATABASE_VERSION,
            )
            File(snapshot, "database/$DATA_NOTES_DATABASE_NAME-journal").delete()
            copyDirectory(
                source = File(context.filesDir, "character-avatars"),
                target = File(snapshot, "files/character-avatars"),
                skipTemporary = true,
            )
            copyPreferencesForExport(File(snapshot, BACKUP_SETTINGS_PATH))

            val records = snapshot.walkTopDown()
                .filter { it.isFile }
                .map { file ->
                    val path = file.relativeTo(snapshot).invariantSeparatorsPath
                    BackupFileRecord(
                        path = path,
                        size = file.length(),
                        sha256 = sha256(file),
                    )
                }
                .filter { BackupPathPolicy.isAllowed(it.path) }
                .sortedBy { it.path }
                .toList()
            val packageInfo = packageInfo()
            val manifest = BackupManifest(
                formatVersion = BACKUP_FORMAT_VERSION,
                createdAt = System.currentTimeMillis(),
                appVersionName = packageInfo.first,
                appVersionCode = packageInfo.second,
                databaseVersion = APP_DATABASE_VERSION,
                included = listOf(
                    "角色、消息、长期记忆、事件、场景和 Trace",
                    "角色头像与聊天背景",
                    "非敏感应用设置",
                ),
                excluded = listOf(
                    "API Key 与加密密钥",
                    "MNN、Dream/QNN 和语音模型",
                    "缓存、临时文件和模型下载目录",
                ),
                files = records,
            ).also(BackupManifest::validate)
            File(snapshot, BACKUP_MANIFEST_NAME)
                .writeText(manifest.toJson().toString(2), StandardCharsets.UTF_8)

            val archive = File(work, DEFAULT_BACKUP_FILE_NAME)
            writeArchive(snapshot, archive)
            context.contentResolver.openOutputStream(uri)?.use { output ->
                archive.inputStream().buffered().use { input -> input.copyTo(output) }
                output.flush()
            } ?: error("无法创建备份文件")
            BackupOperationSummary(archive.length(), records.size + 1)
        } finally {
            work.deleteRecursively()
        }
    }

    suspend fun stageRestore(uri: Uri): BackupOperationSummary = withContext(Dispatchers.IO) {
        val work = createWorkDirectory("backup-restore")
        try {
            val extracted = File(work, "extracted").apply { mkdirs() }
            val actual = extractArchive(uri, extracted)
            val manifestFile = File(extracted, BACKUP_MANIFEST_NAME)
            require(manifestFile.isFile) { "备份缺少 manifest.json" }
            val manifest = BackupManifest.parse(
                manifestFile.readText(StandardCharsets.UTF_8),
            )
            require(manifest.databaseVersion <= APP_DATABASE_VERSION) {
                "备份来自更高版本的应用，请先升级当前应用"
            }
            compareExtractedFiles(
                manifest,
                actual.filterKeys { it != BACKUP_MANIFEST_NAME },
            )
            validateDatabase(
                File(extracted, BACKUP_DATABASE_PATH),
                maximumVersion = APP_DATABASE_VERSION,
            )

            val pending = File(context.filesDir, PENDING_RESTORE_DIRECTORY)
            if (pending.exists()) pending.deleteRecursively()
            if (!extracted.renameTo(pending)) {
                copyDirectory(extracted, pending, skipTemporary = false)
            }
            require(pending.isDirectory) { "无法准备恢复数据" }
            BackupOperationSummary(actual.values.sumOf { it.size }, actual.size)
        } finally {
            work.deleteRecursively()
        }
    }

    suspend fun exportDiagnostics(uri: Uri): BackupOperationSummary = withContext(Dispatchers.IO) {
        val packageInfo = packageInfo()
        val avatarDirectory = File(context.filesDir, "character-avatars")
        val diagnostic = JSONObject()
            .put("generatedAt", Instant.now().toString())
            .put("appVersionName", packageInfo.first)
            .put("appVersionCode", packageInfo.second)
            .put("databaseVersion", APP_DATABASE_VERSION)
            .put("androidSdk", Build.VERSION.SDK_INT)
            .put("androidRelease", Build.VERSION.RELEASE)
            .put("manufacturer", Build.MANUFACTURER)
            .put("model", Build.MODEL)
            .put("abis", JSONArray(Build.SUPPORTED_ABIS.toList()))
            .put("locale", Locale.getDefault().toLanguageTag())
            .put("counts", JSONObject()
                .put("characters", tableCount("characters"))
                .put("messages", tableCount("messages"))
                .put("legacyMemories", tableCount("memories"))
                .put("structuredMemories", tableCount("structured_memories"))
                .put("memoryEvents", tableCount("memory_events"))
                .put("turnTraces", tableCount("turn_traces")))
            .put("storage", JSONObject()
                .put("databaseBytes", databaseSize())
                .put("avatarFiles", avatarDirectory.walkTopDown().count { it.isFile })
                .put("avatarBytes", avatarDirectory.walkTopDown().filter { it.isFile }.sumOf { it.length() }))
            .put("refreshPendingRestore", File(context.filesDir, PENDING_RESTORE_DIRECTORY).exists())
        val text = DiagnosticRedaction.redact(diagnostic.toString(2))
        context.contentResolver.openOutputStream(uri)?.use { output ->
            output.write(text.toByteArray(StandardCharsets.UTF_8))
            output.flush()
        } ?: error("无法创建诊断文件")
        BackupOperationSummary(
            bytes = text.toByteArray(StandardCharsets.UTF_8).size.toLong(),
            entryCount = 1,
        )
    }

    fun consumeRestoreResult(): RestoreResult? {
        val file = File(context.filesDir, RESTORE_RESULT_FILE)
        if (!file.isFile) return null
        return runCatching {
            val root = JSONObject(file.readText(StandardCharsets.UTF_8))
            RestoreResult(
                success = root.optBoolean("success"),
                message = DiagnosticRedaction.redact(root.optString("message")),
            )
        }.getOrNull().also { file.delete() }
    }

    private suspend fun validateDatabase(
        databaseFile: File,
        maximumVersion: Int,
    ) {
        require(databaseFile.isFile) { "备份缺少聊天数据库" }
        require(databaseFile.length() >= SQLITE_HEADER_BYTES.size) {
            "备份数据库文件损坏"
        }
        databaseFile.inputStream().use { input ->
            val header = ByteArray(SQLITE_HEADER_BYTES.size)
            require(input.read(header) == header.size) { "备份数据库文件损坏" }
            require(header.contentEquals(SQLITE_HEADER_BYTES)) { "备份数据库文件损坏" }
        }
        SQLiteDatabase.openDatabase(
            databaseFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
        ).use { db ->
            require(db.version in 1..maximumVersion) {
                "备份数据库版本与当前应用不兼容"
            }
            val tables = mutableSetOf<String>()
            db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table'",
                emptyArray(),
            ).use { cursor ->
                while (cursor.moveToNext()) tables += cursor.getString(0)
            }
            require(tables.containsAll(REQUIRED_DATABASE_TABLES)) {
                "备份数据库缺少必要数据表"
            }
            db.rawQuery("PRAGMA quick_check", emptyArray()).use { cursor ->
                require(cursor.moveToFirst() && cursor.getString(0) == "ok") {
                    "备份数据库完整性校验失败"
                }
            }
        }
    }

    private fun writeArchive(source: File, outputFile: File) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(outputFile))).use { zip ->
            source.walkTopDown()
                .filter { it.isFile }
                .sortedBy { it.relativeTo(source).invariantSeparatorsPath }
                .forEach { file ->
                    val entryName = file.relativeTo(source).invariantSeparatorsPath
                    if (!BackupPathPolicy.isAllowed(entryName)) return@forEach
                    val entry = ZipEntry(entryName).apply {
                        time = file.lastModified()
                    }
                    zip.putNextEntry(entry)
                    file.inputStream().buffered().use { input -> input.copyTo(zip) }
                    zip.closeEntry()
                }
        }
    }

    private fun extractArchive(uri: Uri, target: File): Map<String, BackupFileRecord> {
        val extracted = linkedMapOf<String, BackupFileRecord>()
        var totalBytes = 0L
        var totalEntries = 0
        context.contentResolver.openInputStream(uri)?.use { raw ->
            ZipInputStream(BufferedInputStream(raw)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    totalEntries += 1
                    require(totalEntries <= MAX_BACKUP_ENTRIES) { "备份文件数量异常" }
                    val path = BackupPathPolicy.normalize(entry.name)
                    require(BackupPathPolicy.isAllowed(path)) {
                        "备份包含不支持的文件：$path"
                    }
                    if (!entry.isDirectory) {
                        require(path !in extracted) { "备份包含重复文件：$path" }
                        val destination = File(target, path)
                        val rootPath = target.canonicalPath + File.separator
                        require(destination.canonicalPath.startsWith(rootPath)) {
                            "备份文件路径越界"
                        }
                        destination.parentFile?.mkdirs()
                        val digest = MessageDigest.getInstance("SHA-256")
                        var size = 0L
                        BufferedOutputStream(FileOutputStream(destination)).use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val count = zip.read(buffer)
                                if (count < 0) break
                                size += count
                                totalBytes += count
                                require(size <= MAX_BACKUP_UNCOMPRESSED_BYTES) {
                                    "备份解压后体积超过限制"
                                }
                                require(totalBytes <= MAX_BACKUP_UNCOMPRESSED_BYTES) {
                                    "备份解压后体积超过限制"
                                }
                                digest.update(buffer, 0, count)
                                output.write(buffer, 0, count)
                            }
                        }
                        extracted[path] = BackupFileRecord(
                            path = path,
                            size = size,
                            sha256 = digest.digest().toHex(),
                        )
                    }
                    zip.closeEntry()
                }
            }
        } ?: error("无法读取备份文件")
        return extracted
    }

    private fun compareExtractedFiles(
        manifest: BackupManifest,
        actualWithoutManifest: Map<String, BackupFileRecord>,
    ) {
        val expected = manifest.files.associateBy { it.path }
        require(expected.keys == actualWithoutManifest.keys) {
            "备份清单与实际文件不一致"
        }
        expected.forEach { (path, record) ->
            val actual = actualWithoutManifest.getValue(path)
            require(actual.size == record.size && actual.sha256 == record.sha256) {
                "备份文件校验失败：$path"
            }
        }
    }

    private fun copyDatabaseFiles(sourceDirectory: File, targetDirectory: File) {
        targetDirectory.mkdirs()
        listOf("", "-wal", "-shm").forEach { suffix ->
            val source = File(sourceDirectory, "$DATA_NOTES_DATABASE_NAME$suffix")
            if (!source.isFile) return@forEach
            val target = File(targetDirectory, source.name)
            source.copyTo(target, overwrite = true)
        }
    }

    private fun copyPreferencesForExport(target: File) {
        val source = plainSettingsFile()
        if (!source.isFile) return
        target.parentFile?.mkdirs()
        source.copyTo(target, overwrite = true)
    }

    private fun plainSettingsFile(): File =
        File(context.applicationInfo.dataDir, "shared_prefs/shiyu_settings.xml")

    private fun packageInfo(): Pair<String, Long> {
        val manager = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            manager.getPackageInfo(context.packageName, 0)
        }
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        return (info.versionName ?: "unknown") to code
    }

    private fun databaseSize(): Long =
        listOf("", "-wal", "-shm").sumOf { suffix ->
            File(
                context.getDatabasePath(DATA_NOTES_DATABASE_NAME).parentFile,
                "$DATA_NOTES_DATABASE_NAME$suffix",
            ).takeIf(File::isFile)?.length() ?: 0L
        }

    private fun tableCount(table: String): Long =
        database.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM $table")
            .use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else 0L
            }

    private fun createWorkDirectory(prefix: String): File =
        File(context.cacheDir, "$prefix-${UUID.randomUUID()}").apply {
            check(mkdirs()) { "无法创建临时目录" }
        }

    companion object {
        private const val DATA_NOTES_DATABASE_NAME = "shiyu.db"
        private val SQLITE_HEADER_BYTES =
            "SQLite format 3\u0000".toByteArray(StandardCharsets.US_ASCII)
        private val REQUIRED_DATABASE_TABLES = setOf(
            "characters",
            "messages",
            "app_meta",
            "structured_memories",
        )
        const val DEFAULT_BACKUP_FILE_NAME = "shiyu-backup.zip"
    }
}

internal object PendingRestoreManager {
    private const val TAG = "PendingRestore"
    private const val APPLYING_MARKER = ".applying"
    private const val ROLLBACK_PREFIX = ".restore-rollback-"
    private const val DATABASE_NAME = "shiyu.db"

    fun applyIfPresent(context: Context) {
        val pending = File(context.filesDir, PENDING_RESTORE_DIRECTORY)
        if (!pending.isDirectory) return
        val marker = File(pending, APPLYING_MARKER)
        val rollback = if (marker.isFile) {
            marker.readText(StandardCharsets.UTF_8)
                .trim()
                .takeIf(String::isNotBlank)
                ?.let(::File)
        } else {
            null
        }
        if (rollback != null && rollback.isDirectory) {
            runCatching {
                restoreFromRollback(context, rollback)
                writeResult(context, false, "上次恢复在完成前中断，已自动还原旧数据")
            }.onFailure {
                Log.e(TAG, "Unable to roll back interrupted restore", it)
                writeResult(context, false, "恢复中断，自动还原失败")
            }.also {
                pending.deleteRecursively()
            }
            return
        }

        val newRollback = File(
            context.filesDir,
            "$ROLLBACK_PREFIX${System.currentTimeMillis()}",
        )
        try {
            backupCurrentData(context, newRollback)
            marker.writeText(newRollback.absolutePath, StandardCharsets.UTF_8)
            applyStagedData(context, pending)
            pending.deleteRecursively()
            writeResult(context, true, "备份已恢复")
            cleanupOldRollbacks(context)
        } catch (error: Exception) {
            Log.e(TAG, "Unable to apply staged restore", error)
            val rollbackMessage = if (newRollback.isDirectory) {
                runCatching { restoreFromRollback(context, newRollback) }
                    .fold(
                        onSuccess = { "已保留恢复前的数据" },
                        onFailure = { "旧数据自动回滚失败，请从外部备份重新恢复" },
                    )
            } else {
                "旧数据未改动"
            }
            pending.deleteRecursively()
            writeResult(context, false, "恢复失败：${error.message ?: "未知错误"}；$rollbackMessage")
        }
    }

    fun writeResult(context: Context, success: Boolean, message: String) {
        val result = JSONObject()
            .put("success", success)
            .put("message", DiagnosticRedaction.redact(message))
        File(context.filesDir, RESTORE_RESULT_FILE)
            .writeText(result.toString(), StandardCharsets.UTF_8)
    }

    private fun backupCurrentData(context: Context, root: File) {
        root.mkdirs()
        val databaseDirectory = context.getDatabasePath(DATABASE_NAME).parentFile
        val rollbackDatabase = File(root, "database").apply { mkdirs() }
        listOf("", "-wal", "-shm").forEach { suffix ->
            val source = File(databaseDirectory, "$DATABASE_NAME$suffix")
            if (source.isFile) source.copyTo(File(rollbackDatabase, source.name), overwrite = true)
        }
        copyDirectory(
            File(context.filesDir, "character-avatars"),
            File(root, "files/character-avatars"),
            skipTemporary = false,
        )
        val settings = File(context.applicationInfo.dataDir, "shared_prefs/shiyu_settings.xml")
        if (settings.isFile) {
            File(root, "settings").mkdirs()
            settings.copyTo(File(root, BACKUP_SETTINGS_PATH), overwrite = true)
        }
    }

    private fun applyStagedData(context: Context, pending: File) {
        val databaseDirectory = requireNotNull(
            context.getDatabasePath(DATABASE_NAME).parentFile,
        )
        databaseDirectory.mkdirs()
        listOf("", "-wal", "-shm").forEach { suffix ->
            val target = File(databaseDirectory, "$DATABASE_NAME$suffix")
            if (target.exists()) target.delete()
            val source = File(pending, "database/$DATABASE_NAME$suffix")
            if (source.isFile) {
                source.copyTo(File(databaseDirectory, source.name), overwrite = true)
            }
        }
        val avatarTarget = File(context.filesDir, "character-avatars")
        if (avatarTarget.exists()) avatarTarget.deleteRecursively()
        copyDirectory(
            File(pending, "files/character-avatars"),
            avatarTarget,
            skipTemporary = false,
        )
        val settingsSource = File(pending, BACKUP_SETTINGS_PATH)
        if (settingsSource.isFile) {
            val settingsTarget =
                File(context.applicationInfo.dataDir, "shared_prefs/shiyu_settings.xml")
            settingsTarget.parentFile?.mkdirs()
            settingsSource.copyTo(settingsTarget, overwrite = true)
        }
    }

    private fun restoreFromRollback(context: Context, root: File) {
        val databaseDirectory = requireNotNull(
            context.getDatabasePath(DATABASE_NAME).parentFile,
        )
        databaseDirectory.mkdirs()
        listOf("", "-wal", "-shm").forEach { suffix ->
            val target = File(databaseDirectory, "$DATABASE_NAME$suffix")
            if (target.exists()) target.delete()
            val source = File(root, "database/$DATABASE_NAME$suffix")
            if (source.isFile) {
                source.copyTo(File(databaseDirectory, source.name), overwrite = true)
            }
        }
        val avatarTarget = File(context.filesDir, "character-avatars")
        if (avatarTarget.exists()) avatarTarget.deleteRecursively()
        copyDirectory(
            File(root, "files/character-avatars"),
            avatarTarget,
            skipTemporary = false,
        )
        val settingsSource = File(root, BACKUP_SETTINGS_PATH)
        val settingsTarget =
            File(context.applicationInfo.dataDir, "shared_prefs/shiyu_settings.xml")
        if (settingsSource.isFile) {
            settingsTarget.parentFile?.mkdirs()
            settingsSource.copyTo(settingsTarget, overwrite = true)
        } else if (settingsTarget.exists()) {
            settingsTarget.delete()
        }
    }

    private fun cleanupOldRollbacks(context: Context) {
        context.filesDir.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith(ROLLBACK_PREFIX) }
            ?.sortedByDescending(File::lastModified)
            ?.drop(3)
            ?.forEach { it.deleteRecursively() }
    }
}

internal fun restartAfterRestore(context: Context) {
    (context.applicationContext as ShiyuApplication).applyStagedRestoreNow()
    val intent = Intent(context, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
    val activity = context.findActivity()
    (activity ?: context).startActivity(intent)
    activity?.finish()
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().toHex()
}

private fun ByteArray.toHex(): String =
    joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) }

private fun copyDirectory(
    source: File,
    target: File,
    skipTemporary: Boolean,
) {
    if (!source.isDirectory) {
        target.mkdirs()
        return
    }
    val sourceRoot = source.canonicalPath + File.separator
    source.walkTopDown().forEach { item ->
        val relative = item.relativeTo(source).invariantSeparatorsPath
        if (skipTemporary && relative.endsWith(".tmp")) return@forEach
        val output = File(target, relative)
        if (item.isDirectory) {
            output.mkdirs()
        } else if (item.isFile) {
            require(item.canonicalPath.startsWith(sourceRoot)) {
                "源目录包含越界文件"
            }
            output.parentFile?.mkdirs()
            item.copyTo(output, overwrite = true)
        }
    }
    target.mkdirs()
}

internal const val PENDING_RESTORE_DIRECTORY = ".pending-restore"
internal const val RESTORE_RESULT_FILE = ".restore-result.json"
