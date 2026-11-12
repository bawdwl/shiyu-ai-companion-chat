package com.shiyu.chat.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DatabaseMigrationPolicyTest {
    @Test
    fun migrationsFormAContiguousChainToCurrentVersion() {
        var expectedStart = 2
        AppDatabase.ALL_MIGRATIONS.forEach { migration ->
            assertEquals(expectedStart, migration.startVersion)
            assertEquals(expectedStart + 1, migration.endVersion)
            expectedStart = migration.endVersion
        }
        assertEquals(APP_DATABASE_VERSION, expectedStart)
    }
}
