package com.alibaba.mnnllm.android.llm

import android.util.Pair
import org.json.JSONObject
import java.io.File

/**
 * Minimal bridge to the Apache-2.0 MNN LLM Android runtime bundled by MNN Chat.
 * The JNI class name intentionally matches upstream MNN.
 */
class LlmSession(private val configPath: String) {
    @Volatile
    private var nativePtr: Long = 0L

    @Synchronized
    fun load() {
        check(nativePtr == 0L) { "MNN 模型已经加载" }
        val configFile = File(configPath)
        require(configFile.isFile) { "MNN config.json 不存在：${configFile.absolutePath}" }
        val configJson = configFile.readText(Charsets.UTF_8)
        val extraConfig = JSONObject()
            .put("mmap_dir", "")
            .put("is_r1", false)
            .put("keep_history", false)
            .toString()
        nativePtr = initNative(
            configFile.absolutePath,
            emptyList<String>(),
            configJson,
            extraConfig,
        )
        check(nativePtr != 0L) { "MNN 模型加载失败" }
    }

    @Synchronized
    fun generate(
        messages: List<Pair<String, String>>,
        listener: GenerateProgressListener,
    ): HashMap<String, Any> {
        check(nativePtr != 0L) { "请先加载 MNN 模型" }
        return submitFullHistoryNative(nativePtr, messages, listener)
    }

    @Synchronized
    fun updateMaxNewTokens(maxNewTokens: Int) {
        if (nativePtr != 0L) {
            updateMaxNewTokensNative(nativePtr, maxNewTokens.coerceIn(1, 1024))
        }
    }

    @Synchronized
    fun updateConfig(configJson: String) {
        if (nativePtr != 0L) updateConfigNative(nativePtr, configJson)
    }

    @Synchronized
    fun reset() {
        if (nativePtr != 0L) resetNative(nativePtr)
    }

    @Synchronized
    fun release() {
        if (nativePtr != 0L) {
            releaseNative(nativePtr)
            nativePtr = 0L
        }
    }

    private external fun initNative(
        configPath: String?,
        history: List<String>?,
        mergedConfigStr: String?,
        configJsonStr: String?,
    ): Long

    private external fun submitFullHistoryNative(
        instanceId: Long,
        history: List<Pair<String, String>>,
        listener: GenerateProgressListener,
    ): HashMap<String, Any>

    private external fun resetNative(instanceId: Long)

    private external fun updateMaxNewTokensNative(instanceId: Long, maxNewTokens: Int)

    private external fun updateConfigNative(instanceId: Long, configJson: String)

    private external fun releaseNative(instanceId: Long)

    companion object {
        init {
            System.loadLibrary("MNN")
            System.loadLibrary("mnnllmapp")
        }
    }
}
