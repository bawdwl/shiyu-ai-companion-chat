package com.shiyu.chat.data

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Qwen3-Embedding-0.6B ONNX INT8 runner. Model files are downloaded by the
 * user; the APK only carries the Java ONNX Runtime API and reuses sherpa's
 * matching native ONNX Runtime library.
 */
class QwenEmbeddingRuntime(
    private val modelManager: QwenEmbeddingModelManager,
) : AutoCloseable {
    private val lock = Any()
    private var environment: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var tokenizer: QwenBpeTokenizer? = null

    fun isReady(): Boolean = modelManager.isReady()

    fun embedQuery(text: String): FloatArray =
        embed("Instruct: $QUERY_INSTRUCTION\nQuery:$text")

    fun embedDocument(text: String): FloatArray =
        embed(text)

    fun selfTest(): String {
        val startedAt = System.currentTimeMillis()
        val first = embedQuery("我上次和你约定的事情")
        val second = embedDocument("用户与角色约定周末一起去看展览")
        val unrelated = embedDocument("这是一段与约定无关的天气说明")
        val relatedScore = cosine(first, second)
        val unrelatedScore = cosine(first, unrelated)
        require(relatedScore.isFinite() && unrelatedScore.isFinite()) {
            "Qwen embedding 自检返回了无效相似度"
        }
        val result = "向量维度 ${first.size}，相关语义 ${formatScore(relatedScore)}，" +
            "无关语义 ${formatScore(unrelatedScore)}，" +
            "耗时 ${System.currentTimeMillis() - startedAt} ms"
        Log.i("QwenEmbedding", result)
        return result
    }

    private fun embed(text: String): FloatArray = synchronized(lock) {
        ensureLoaded()
        val runtimeSession = session ?: error("Qwen embedding session 未初始化")
        val runtimeTokenizer = tokenizer ?: error("Qwen embedding tokenizer 未初始化")
        val ids = runtimeTokenizer.encode(text.trim().take(MAX_INPUT_CHARS), MAX_TOKENS)
        val sequenceLength = ids.size.toLong()
        val shape = longArrayOf(1, sequenceLength)
        val attention = LongArray(ids.size) { 1L }
        val positions = LongArray(ids.size) { it.toLong() }
        val inputs = linkedMapOf<String, OnnxTensor>()
        try {
            runtimeSession.inputNames.forEach { name ->
                    val env = requireNotNull(environment)
                    inputs[name] = when {
                        name == "input_ids" -> OnnxTensor.createTensor(
                            env,
                            java.nio.LongBuffer.wrap(ids),
                            shape,
                        )
                        name == "attention_mask" -> OnnxTensor.createTensor(
                            env,
                            java.nio.LongBuffer.wrap(attention),
                            shape,
                        )
                        name == "position_ids" -> OnnxTensor.createTensor(
                            env,
                            java.nio.LongBuffer.wrap(positions),
                            shape,
                        )
                        name == "token_type_ids" -> OnnxTensor.createTensor(
                            env,
                            java.nio.LongBuffer.wrap(LongArray(ids.size)),
                            shape,
                        )
                        name.startsWith("past_key_values.") &&
                            (name.endsWith(".key") || name.endsWith(".value")) ->
                            OnnxTensor.createTensor(
                                env,
                                FloatBuffer.allocate(0),
                                longArrayOf(
                                    1,
                                    KEY_VALUE_HEADS.toLong(),
                                    0,
                                    HEAD_DIMENSION.toLong(),
                                ),
                            )
                        else -> null
                    } ?: return@forEach
                }
            require("input_ids" in inputs && "attention_mask" in inputs) {
                "Qwen ONNX 模型缺少 input_ids 或 attention_mask 输入"
            }
            runtimeSession.run(inputs).use { result ->
                val output = result.iterator().asSequence()
                    .map { it.value }
                    .filterIsInstance<OnnxTensor>()
                    .firstOrNull { tensor ->
                        val tensorShape = tensor.info.shape
                        tensorShape.size in setOf(2, 3) &&
                            tensorShape.lastOrNull() == EMBEDDING_DIMENSION.toLong()
                    }
                    ?: error("Qwen ONNX 输出中没有 1024 维 embedding")
                val outputShape = output.info.shape
                val buffer = output.getFloatBuffer()
                val vector = if (outputShape.size == 3) {
                    val sequence = outputShape[1].toInt()
                    readLastToken(buffer, sequence, EMBEDDING_DIMENSION)
                } else {
                    readFirstVector(buffer, EMBEDDING_DIMENSION)
                }
                normalize(vector)
            }
        } finally {
            inputs.values.forEach { runCatching { it.close() } }
        }
    }

    private fun ensureLoaded() {
        if (session != null && tokenizer != null) return
        require(modelManager.isReady()) {
            "请先在设置中下载或导入 Qwen3-Embedding 0.6B ONNX INT8 模型"
        }
        val env = OrtEnvironment.getEnvironment()
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(
                max(1, Runtime.getRuntime().availableProcessors().coerceAtMost(4)),
            )
            setInterOpNumThreads(1)
        }
        val loadedSession = env.createSession(
            modelManager.modelFile().absolutePath,
            options,
        )
        Log.i(
            "QwenEmbedding",
            loadedSession.inputInfo.entries.joinToString(
                prefix = "inputs=",
            ) { (name, info) ->
                "$name${(info.info as ai.onnxruntime.TensorInfo).getShape().contentToString()}"
            },
        )
        Log.i(
            "QwenEmbedding",
            loadedSession.outputInfo.entries.joinToString(
                prefix = "outputs=",
            ) { (name, info) ->
                "$name${(info.info as ai.onnxruntime.TensorInfo).getShape().contentToString()}"
            },
        )
        val loadedTokenizer = QwenBpeTokenizer.fromJson(
            modelManager.tokenizerFile().readText(),
        )
        environment = env
        session = loadedSession
        tokenizer = loadedTokenizer
    }

    private fun readLastToken(
        buffer: FloatBuffer,
        sequenceLength: Int,
        dimension: Int,
    ): FloatArray {
        val start = (sequenceLength - 1).coerceAtLeast(0) * dimension
        require(buffer.limit() >= start + dimension) {
            "Qwen ONNX 输出形状与向量数据不一致"
        }
        return FloatArray(dimension) { index ->
            buffer.get(start + index)
        }
    }

    private fun readFirstVector(
        buffer: FloatBuffer,
        dimension: Int,
    ): FloatArray {
        require(buffer.limit() >= dimension) {
            "Qwen ONNX 输出形状与向量数据不一致"
        }
        return FloatArray(dimension) { index -> buffer.get(index) }
    }

    private fun normalize(vector: FloatArray): FloatArray {
        val norm = sqrt(vector.sumOf { it.toDouble() * it.toDouble() })
            .toFloat()
        require(norm.isFinite() && norm > 0f) {
            "Qwen embedding 返回无效零向量"
        }
        return FloatArray(vector.size) { index -> vector[index] / norm }
    }

    private fun cosine(first: FloatArray, second: FloatArray): Double {
        if (first.size != second.size) return Double.NaN
        var dot = 0.0
        var firstNorm = 0.0
        var secondNorm = 0.0
        for (index in first.indices) {
            dot += first[index] * second[index]
            firstNorm += first[index] * first[index]
            secondNorm += second[index] * second[index]
        }
        val denominator = sqrt(firstNorm) * sqrt(secondNorm)
        return if (denominator > 0.0) dot / denominator else Double.NaN
    }

    private fun formatScore(score: Double): String =
        String.format(java.util.Locale.CHINA, "%.3f", score)

    override fun close() {
        synchronized(lock) {
            runCatching { session?.close() }
            session = null
            tokenizer = null
            environment = null
        }
    }

    companion object {
        const val MAX_TOKENS = 512
        const val EMBEDDING_DIMENSION = QwenEmbeddingModelManager.EMBEDDING_DIMENSION
        private const val MAX_INPUT_CHARS = 2_400
        private const val KEY_VALUE_HEADS = 8
        private const val HEAD_DIMENSION = 128
        private const val QUERY_INSTRUCTION =
            "Given a conversation context, retrieve relevant passages and memories"
    }
}
