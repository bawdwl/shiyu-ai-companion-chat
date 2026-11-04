package com.shiyu.chat.data

import android.content.Context
import android.media.AudioFormat
import android.media.AudioAttributes
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import java.io.File
import java.io.ByteArrayOutputStream

/** Blocking functions must be called from an IO worker. */
class LocalVoice(private val context: Context) {
    @Volatile var recording = false
    @Volatile private var activeTrack: AudioTrack? = null
    @Volatile private var playbackStopped = false

    private fun copyAssets(path: String, target: File) {
        val children = context.assets.list(path).orEmpty()
        if (children.isEmpty()) {
            if (!target.isFile) {
                target.parentFile?.mkdirs()
                val tmp = File(target.parentFile, target.name + ".tmp")
                try {
                    context.assets.open(path).use { source -> tmp.outputStream().use { source.copyTo(it) } }
                    check(tmp.renameTo(target)) { "语音模型保存失败" }
                } finally { tmp.delete() }
            }
        } else {
            target.mkdirs()
            children.forEach { copyAssets("$path/$it", File(target, it)) }
        }
    }

    fun recordAndRecognize(): String {
        val bufferSize = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(bufferSize > 0) { "手机不支持录音" }
        @Suppress("MissingPermission")
        val input = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize * 2)
        check(input.state == AudioRecord.STATE_INITIALIZED) { "麦克风初始化失败" }
        val data = ByteArrayOutputStream()
        try {
            input.startRecording()
            val chunk = ByteArray(4096)
            while (recording && data.size() < 16000 * 2 * 60) {
                val count = input.read(chunk, 0, chunk.size)
                if (count < 0) error("录音失败：$count")
                data.write(chunk, 0, count)
            }
        } finally { recording = false; runCatching { input.stop() }; input.release() }
        val bytes = data.toByteArray()
        require(bytes.size >= 3200) { "录音太短，请重试" }
        val samples = FloatArray(bytes.size / 2) { i ->
            (((bytes[i * 2 + 1].toInt() shl 8) or (bytes[i * 2].toInt() and 255)).toShort()).toFloat() / 32768f
        }
        val folder = File(context.filesDir, "speech/asr")
        copyAssets("speech/asr", folder)
        return LocalSpeechEngine(folder).recognize(samples)
    }

    fun stopPlayback() { playbackStopped = true; activeTrack?.let { runCatching { it.pause(); it.flush(); it.stop() } } }
    fun preparePlayback() { playbackStopped = false }

    fun speak(text: String, speaker: Int) {
        require(text.isNotBlank()) { "没有可播放的文字" }
        val root = File(context.filesDir, "speech/kokoro")
        copyAssets("speech/kokoro", root)
        fun path(name: String) = File(root, name).absolutePath
        val engine = OfflineTts(config = OfflineTtsConfig(model = OfflineTtsModelConfig(
            kokoro = OfflineTtsKokoroModelConfig(model = path("model.int8.onnx"), voices = path("voices.bin"),
                tokens = path("tokens.txt"), dataDir = path("espeak-ng-data"), dictDir = path("dict"),
                lexicon = listOf(path("lexicon-us-en.txt"), path("lexicon-zh.txt")).joinToString(",")), numThreads = 2),
            ruleFsts = listOf(path("date-zh.fst"), path("number-zh.fst"), path("phone-zh.fst")).joinToString(",")))
        try {
            for (part in splitVoiceText(text)) {
                if (playbackStopped) break
                val audio = engine.generate(part, sid = speaker.coerceIn(3, 102), speed = 1f)
                if (playbackStopped) break
                val pcm = ShortArray(audio.samples.size) { (audio.samples[it].coerceIn(-1f, 1f) * 32767).toInt().toShort() }
                val min = AudioTrack.getMinBufferSize(audio.sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(audio.sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setBufferSizeInBytes(maxOf(min, 4096))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                activeTrack = track
                try {
                    if (playbackStopped) break
                    track.play()
                    var offset = 0
                    while (offset < pcm.size && !playbackStopped) {
                        val written = track.write(pcm, offset, pcm.size - offset)
                        if (written <= 0) { if (!playbackStopped) error("音频播放失败：$written"); break }
                        offset += written
                    }
                    while (!playbackStopped && track.playState == AudioTrack.PLAYSTATE_PLAYING && track.playbackHeadPosition < pcm.size) Thread.sleep(50)
                } finally { activeTrack = null; runCatching { track.stop() }; track.release() }
            }
        } finally { engine.release() }
    }
}

internal fun splitVoiceText(text: String, limit: Int = 180): List<String> {
    require(limit > 0)
    val chunks = mutableListOf<String>()
    var start = 0
    while (start < text.length) {
        var end = minOf(start + limit, text.length)
        if (end < text.length) {
            val boundary = (end - 1 downTo start).firstOrNull { text[it] in "。！？!?；;\n" }
            if (boundary != null && boundary > start) end = boundary + 1
            if (end < text.length && Character.isHighSurrogate(text[end - 1]) && Character.isLowSurrogate(text[end])) end--
        }
        if (end <= start) end = minOf(start + 2, text.length)
        val part = text.substring(start, end)
        if (part.isNotBlank()) chunks.add(part)
        start = end
    }
    return chunks
}
