package com.shiyu.chat

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.shiyu.chat.data.AppDatabase
import com.shiyu.chat.data.ChatRepository
import com.shiyu.chat.data.PendingRestoreManager

class ShiyuApplication : Application() {
    private val dataLock = Any()
    private var databaseReference: AppDatabase? = null
    private var repositoryReference: ChatRepository? = null

    override fun onCreate() {
        super.onCreate()
        PendingRestoreManager.applyIfPresent(this)
    }

    val database: AppDatabase
        get() = synchronized(dataLock) {
            databaseReference ?: buildDatabase().also { databaseReference = it }
        }

    val repository: ChatRepository
        get() = synchronized(dataLock) {
            repositoryReference ?: buildRepository(database).also {
                repositoryReference = it
            }
        }

    fun applyStagedRestoreNow() {
        synchronized(dataLock) {
            repositoryReference?.prepareForDataReplacement()
            databaseReference?.close()
            repositoryReference = null
            databaseReference = null
            PendingRestoreManager.applyIfPresent(this)
        }
    }

    private fun buildDatabase(): AppDatabase =
        Room.databaseBuilder(this, AppDatabase::class.java, "shiyu.db")
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .addCallback(AppDatabase.CREATE_SEARCH_INDEX_CALLBACK)
            .build()

    private fun buildRepository(database: AppDatabase): ChatRepository {
        val masterKey = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val secrets = EncryptedSharedPreferences.create(
            this,
            "shiyu_secrets",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        return ChatRepository(this, database, secrets)
    }
}

val Context.shiyu: ShiyuApplication
    get() = applicationContext as ShiyuApplication
