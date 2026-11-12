package com.shiyu.chat.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

/**
 * Copies chat attachments into app-managed storage so a later gallery move or
 * deletion cannot break an existing conversation.
 */
internal class ManagedChatMedia(
    private val context: Context,
) {
    private val root = File(context.filesDir, "chat-media")

    fun persist(characterId: String, uri: Uri): Uri {
        val existing = uri.path?.let(::File)?.canonicalFile
        if (existing != null && existing.startsWith(root.canonicalFile) && existing.isFile) {
            return Uri.fromFile(existing)
        }
        root.mkdirs()
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        val extension = when (mime) {
            "image/png" -> "png"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val safeId = characterId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val output = File(root, "$safeId-${UUID.randomUUID()}.$extension")
        val temporary = File.createTempFile("chat-media-", ".tmp", root)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temporary.outputStream().buffered().use { target ->
                    var total = 0L
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 20L * 1024L * 1024L) {
                            "图片不能超过 20 MiB"
                        }
                        target.write(buffer, 0, count)
                    }
                }
            } ?: error("无法读取图片")
            check(temporary.renameTo(output)) { "聊天图片保存失败" }
            return Uri.fromFile(output)
        } finally {
            temporary.delete()
        }
    }

    fun delete(uri: String?) {
        uri ?: return
        runCatching {
            val file = File(Uri.parse(uri).path ?: return).canonicalFile
            if (file.startsWith(root.canonicalFile)) file.delete()
        }
    }

    fun deleteForMessages(messages: List<MessageEntity>) {
        messages.forEach { delete(it.imageUri) }
    }

    fun deleteForCharacter(characterId: String) {
        val safeId = characterId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        root.listFiles()
            ?.filter { it.isFile && it.name.startsWith("$safeId-") }
            ?.forEach(File::delete)
    }
}
