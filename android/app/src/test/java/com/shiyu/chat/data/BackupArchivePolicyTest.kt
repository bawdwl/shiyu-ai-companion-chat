package com.shiyu.chat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class BackupArchivePolicyTest {
    @Test
    fun pathPolicyRejectsAbsoluteTraversalAndSensitiveLocations() {
        assertTrue(BackupPathPolicy.isAllowed("database/shiyu.db"))
        assertTrue(BackupPathPolicy.isAllowed("files/character-avatars/peijiming.webp"))
        assertTrue(BackupPathPolicy.isAllowed("files/chat-media/com.shiyu-1.jpg"))
        assertTrue(BackupPathPolicy.isAllowed("settings/shiyu_settings.xml"))
        assertFalse(BackupPathPolicy.isAllowed("../shiyu.db"))
        assertFalse(BackupPathPolicy.isAllowed("files/character-avatars/../shiyu.db"))
        assertFalse(BackupPathPolicy.isAllowed("shared_prefs/shiyu_secrets.xml"))
        assertFalse(BackupPathPolicy.isAllowed("files/dream-models/model.bin"))
        assertFalse(BackupPathPolicy.isAllowed("files/character-avatars/pending.tmp"))
        assertFalse(BackupPathPolicy.isAllowed("C:/shiyu.db"))
    }

    @Test
    fun manifestValidatesChecksumShape() {
        val databaseBytes = "SQLite format 3\u0000".toByteArray(StandardCharsets.US_ASCII)
        val manifest = BackupManifest(
            formatVersion = BACKUP_FORMAT_VERSION,
            createdAt = 1_700_000_000_000L,
            appVersionName = "0.19.2",
            appVersionCode = 61,
            databaseVersion = 17,
            included = listOf("database", "avatars"),
            excluded = listOf("secrets"),
            files = listOf(
                BackupFileRecord(
                    path = BACKUP_DATABASE_PATH,
                    size = databaseBytes.size.toLong(),
                    sha256 = sha256Hex(databaseBytes),
                ),
            ),
        ).also(BackupManifest::validate)

        assertEquals(BACKUP_DATABASE_PATH, manifest.files.single().path)
        assertTrue(manifest.files.single().sha256.matches(Regex("[0-9a-f]{64}")))
        assertTrue(BackupPathPolicy.isAllowed("manifest.json"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun manifestWithoutDatabaseIsRejected() {
        BackupManifest(
            formatVersion = BACKUP_FORMAT_VERSION,
            createdAt = 1L,
            appVersionName = "0.19.2",
            appVersionCode = 61,
            databaseVersion = 17,
            included = emptyList(),
            excluded = emptyList(),
            files = listOf(
                BackupFileRecord(
                    path = "files/character-avatars/a.webp",
                    size = 1,
                    sha256 = sha256Hex(byteArrayOf(1)),
                ),
            ),
        ).validate()
    }

    @Test
    fun diagnosticRedactionRemovesProviderKeysAndAuthorizationHeaders() {
        val input = """
            Authorization: Bearer abc.def.ghi
            apiKey=sk-live-secret
            token: "oc_sk_12345"
        """.trimIndent()

        val output = DiagnosticRedaction.redact(input)

        assertFalse(output.contains("abc.def.ghi"))
        assertFalse(output.contains("sk-live-secret"))
        assertFalse(output.contains("oc_sk_12345"))
        assertTrue(output.contains("[REDACTED]"))
    }
}
