package com.shiyu.chat

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.shiyu.chat.data.AppDatabase
import com.shiyu.chat.data.ChatRepository

class ShiyuApplication : Application() {
    val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "shiyu.db")
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .addCallback(AppDatabase.CREATE_SEARCH_INDEX_CALLBACK)
            .build()
    }

    val repository: ChatRepository by lazy {
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
        ChatRepository(this, database, secrets)
    }
}

val Context.shiyu: ShiyuApplication
    get() = applicationContext as ShiyuApplication
