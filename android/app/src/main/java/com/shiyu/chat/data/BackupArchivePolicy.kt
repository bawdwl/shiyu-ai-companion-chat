package com.shiyu.chat.data

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale

internal const val BACKUP_FORMAT_VERSION = 1
internal const val BACKUP_MANIFEST_NAME = "manifest.json"
internal const val BACKUP_DATABASE_PATH = "database/shiyu.db"
internal const val BACKUP_DATABASE_WAL_PATH = "database/shiyu.db-wal"
internal const val BACKUP_DATABASE_SHM_PATH = "database/shiyu.db-shm"
internal const val BACKUP_AVATAR_PREFIX = "files/character-avatars/"
internal const val BACKUP_CHAT_MEDIA_PREFIX = "files/chat-media/"
internal const val BACKUP_SETTINGS_PATH = "settings/shiyu_settings.xml"
internal const val MAX_BACKUP_ENTRIES = 4_096
internal const val MAX_BACKUP_UNCOMPRESSED_BYTES = 512L * 1024L * 1024L

internal data class BackupFileRecord(
    val path: String,
    val size: Long,
    val sha256: String,
)

internal data class BackupManifest(
    val formatVersion: Int,
    val createdAt: Long,
    val appVersionName: String,
    val appVersionCode: Long,
    val databaseVersion: Int,
    val included: List<String>,
    val excluded: List<String>,
    val files: List<BackupFileRecord>,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("formatVersion", formatVersion)
        .put("createdAt", createdAt)
        .put("appVersionName", appVersionName)
        .put("appVersionCode", appVersionCode)
        .put("databaseVersion", databaseVersion)
        .put("included", JSONArray(included))
        .put("excluded", JSONArray(excluded))
        .put(
            "files",
            JSONArray().apply {
                files.forEach { record ->
                    put(
                        JSONObject()
                            .put("path", record.path)
                            .put("size", record.size)
                            .put("sha256", record.sha256),
                    )
                }
            },
        )

    fun validate() {
        require(formatVersion == BACKUP_FORMAT_VERSION) {
            "备份格式版本不受支持"
        }
        require(createdAt > 0) { "备份时间无效" }
        require(databaseVersion in 1..10_000) { "备份数据库版本无效" }
        require(files.isNotEmpty()) { "备份中没有可恢复文件" }
        require(files.size <= MAX_BACKUP_ENTRIES) { "备份文件数量异常" }
        require(files.sumOf { it.size } <= MAX_BACKUP_UNCOMPRESSED_BYTES) {
            "备份解压后体积超过限制"
        }
        val paths = files.map { it.path }
        require(paths.size == paths.toSet().size) { "备份包含重复文件" }
        require(BACKUP_DATABASE_PATH in paths) { "备份缺少聊天数据库" }
        files.forEach { record ->
            require(BackupPathPolicy.isAllowed(record.path)) {
                "备份包含不支持的文件：${record.path}"
            }
            require(record.size >= 0) { "备份文件大小无效" }
            require(record.sha256.matches(Regex("[0-9a-f]{64}"))) {
                "备份文件校验值无效"
            }
        }
    }

    companion object {
        fun parse(raw: String): BackupManifest {
            val root = JSONObject(raw)
            val included = root.optJSONArray("included").toStringList()
            val excluded = root.optJSONArray("excluded").toStringList()
            val entries = root.optJSONArray("files") ?: JSONArray()
            val files = buildList {
                for (index in 0 until entries.length()) {
                    val entry = entries.getJSONObject(index)
                    add(
                        BackupFileRecord(
                            path = entry.getString("path"),
                            size = entry.getLong("size"),
                            sha256 = entry.getString("sha256").lowercase(Locale.ROOT),
                        ),
                    )
                }
            }
            return BackupManifest(
                formatVersion = root.getInt("formatVersion"),
                createdAt = root.getLong("createdAt"),
                appVersionName = root.optString("appVersionName"),
                appVersionCode = root.optLong("appVersionCode"),
                databaseVersion = root.getInt("databaseVersion"),
                included = included,
                excluded = excluded,
                files = files,
            ).also(BackupManifest::validate)
        }
    }
}

internal object BackupPathPolicy {
    private val simpleName = Regex("[^/\\\\]+")

    fun normalize(raw: String): String {
        require(raw.isNotBlank()) { "备份文件路径为空" }
        require('\u0000' !in raw) { "备份文件路径无效" }
        require(!raw.startsWith('/') && !raw.startsWith('\\')) {
            "备份文件不能使用绝对路径"
        }
        require(!raw.contains(':')) { "备份文件不能包含盘符" }
        val normalized = raw.replace('\\', '/')
        val parts = normalized.split('/')
        require(parts.all { it.isNotBlank() && it != "." && it != ".." }) {
            "备份文件路径包含目录穿越"
        }
        require(parts.joinToString("/") == normalized) { "备份文件路径未规范化" }
        return normalized
    }

    fun isAllowed(raw: String): Boolean {
        val path = runCatching { normalize(raw) }.getOrNull() ?: return false
        if (path == BACKUP_MANIFEST_NAME) return true
        if (path in setOf(
                BACKUP_DATABASE_PATH,
                BACKUP_DATABASE_WAL_PATH,
                BACKUP_DATABASE_SHM_PATH,
                BACKUP_SETTINGS_PATH,
            )
        ) {
            return true
        }
        val mediaPrefix = when {
            path.startsWith(BACKUP_AVATAR_PREFIX) -> BACKUP_AVATAR_PREFIX
            path.startsWith(BACKUP_CHAT_MEDIA_PREFIX) -> BACKUP_CHAT_MEDIA_PREFIX
            else -> return false
        }
        val relative = path.removePrefix(mediaPrefix)
        return simpleName.matches(relative) && !relative.endsWith(".tmp")
    }
}

internal object DiagnosticRedaction {
    private val bearer = Regex("(?i)(authorization\\s*:\\s*bearer\\s+)[^\\s\"']+")
    private val openCodeKey = Regex("\\boc_sk_[A-Za-z0-9._-]+")
    private val secretKey = Regex("\\bsk-[A-Za-z0-9._-]+")
    private val keyAssignment = Regex(
        "(?i)((?:api[_-]?key|token|secret)\\s*[=:]\\s*)[^\\s,;\"']+",
    )

    fun redact(text: String): String = text
        .replace(bearer, "$1[REDACTED]")
        .replace(openCodeKey, "[REDACTED]")
        .replace(secretKey, "[REDACTED]")
        .replace(keyAssignment, "$1[REDACTED]")
}

internal fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) }

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) add(getString(index))
    }
}
