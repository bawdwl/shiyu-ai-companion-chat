package com.alibaba.mnnllm.android.llm

fun interface GenerateProgressListener {
    /** Return true to request cancellation. */
    fun onProgress(progress: String?): Boolean
}
