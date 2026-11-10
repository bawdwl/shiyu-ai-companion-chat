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
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
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
            val expectedSize = context.assets.open(path).use { it.available().toLong() }
            if (!target.isFile || target.length() != expectedSize) {
                target.parentFile?.mkdirs()
                val tmp = File(target.parentFile, target.name + ".tmp")
                try {
                    context.assets.open(path).use { source -> tmp.outputStream().use { source.copyTo(it) } }
                    target.delete()
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

    fun isTtsReady(): Boolean = true

    fun speak(text: String, speaker: Int) {
        require(text.isNotBlank()) { "没有可播放的文字" }
        val root = File(context.filesDir, "speech/vits-zh-ll").also {
            copyAssets("speech/vits-zh-ll", it)
        }
        fun path(name: String) = File(root, name).absolutePath
        val engine = OfflineTts(config = OfflineTtsConfig(model = OfflineTtsModelConfig(
            vits = OfflineTtsVitsModelConfig(
                model = path("model.onnx"),
                lexicon = path("lexicon.txt"),
                tokens = path("tokens.txt"),
                dictDir = path("dict"),
            ),
            numThreads = 2,
        ), ruleFsts = listOf(path("date.fst"), path("number.fst"), path("phone.fst")).joinToString(",")))
        try {
            for (part in splitVoiceText(text)) {
                if (playbackStopped) break
                val audio = engine.generate(
                    part,
                    sid = speaker.coerceIn(0, TTS_VOICE_COUNT - 1),
                    speed = 1f,
                )
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
