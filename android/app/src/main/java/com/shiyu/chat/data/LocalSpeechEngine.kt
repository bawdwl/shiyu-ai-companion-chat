package com.shiyu.chat.data

import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineParaformerModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import java.io.File

/** Recognizes 16 kHz mono PCM locally. The caller owns model installation and threading. */
class LocalSpeechEngine(private val modelDirectory: File) {
    fun recognize(samples: FloatArray): String {
        require(samples.isNotEmpty()) { "录音为空" }
        val model = File(modelDirectory, "model.int8.onnx")
        val tokens = File(modelDirectory, "tokens.txt")
        require(model.isFile && tokens.isFile) { "请先安装本地语音识别模型" }
        val recognizer = OfflineRecognizer(config = OfflineRecognizerConfig(
            modelConfig = OfflineModelConfig(
                paraformer = OfflineParaformerModelConfig(model = model.absolutePath),
                tokens = tokens.absolutePath,
                numThreads = 2,
            ),
        ))
        try {
            val stream = recognizer.createStream()
            try {
                stream.acceptWaveform(samples, 16000)
                recognizer.decode(stream)
                return recognizer.getResult(stream).text
            } finally {
                stream.release()
            }
        } finally {
            recognizer.release()
        }
    }
}
