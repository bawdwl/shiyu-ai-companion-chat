package com.shiyu.chat.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.storage.StorageManager
import java.io.File

internal fun allocatableBytes(context: Context, directory: File): Long {
    val storage = context.getSystemService(StorageManager::class.java)
    return runCatching {
        storage.getAllocatableBytes(storage.getUuidForPath(directory))
    }.getOrElse {
        directory.usableSpaceFallback()
    }
}

@SuppressLint("UsableSpace")
private fun File.usableSpaceFallback(): Long = usableSpace
