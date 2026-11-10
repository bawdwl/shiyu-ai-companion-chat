package com.shiyu.chat.data

import android.content.Context

/**
 * Cloud builds expose the same UI contract but do not package local speech
 * models or sherpa-onnx native libraries.
 */
class LocalVoice(@Suppress("UNUSED_PARAMETER") private val context: Context) {
    @Volatile
    var recording = false

    fun recordAndRecognize(): String =
        error("云端版不提供本地语音识别，请使用文字输入")

    fun stopPlayback() = Unit

    fun preparePlayback() = Unit

    fun isTtsReady(): Boolean = false

    fun speak(text: String, speaker: Int): Unit =
        error("云端版不提供角色朗读")
}
