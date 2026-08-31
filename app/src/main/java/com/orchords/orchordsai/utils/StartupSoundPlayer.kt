package com.orchords.orchordsai.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import androidx.annotation.RawRes
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

private const val TAG = "StartupSoundPlayer"

/** Plays the uncompressed startup cue without SoundPool's asynchronous decode step. */
class StartupSoundPlayer(
    private val context: Context,
    private val trackFactory: (PcmWave) -> AudioTrack = ::createAudioTrack,
) {
    private val started = AtomicBoolean(false)

    fun playOnce(@RawRes resourceId: Int) {
        if (!started.compareAndSet(false, true)) return

        thread(name = "StartupSound", isDaemon = true, priority = Thread.MAX_PRIORITY) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            runCatching {
                val wave = context.resources.openRawResource(resourceId).use { PcmWave.read(it.readBytes()) }
                val track = trackFactory(wave)
                try {
                    check(track.write(wave.samples, 0, wave.samples.size) == wave.samples.size) {
                        "Startup PCM could not be written completely"
                    }
                    track.play()
                    Log.i(TAG, "Startup cue playback started")
                    Thread.sleep(wave.durationMillis)
                } finally {
                    track.release()
                }
            }.onFailure { error ->
                Log.w(TAG, "Startup cue playback unavailable", error)
            }
        }
    }
}

data class PcmWave(
    val samples: ByteArray,
    val sampleRate: Int,
    val channelCount: Int,
    val bitsPerSample: Int,
) {
    val durationMillis: Long =
        samples.size * 1_000L / (sampleRate * channelCount * (bitsPerSample / 8))

    companion object {
        fun read(bytes: ByteArray): PcmWave {
            require(bytes.size >= 44) { "WAV header is incomplete" }
            require(bytes.copyOfRange(0, 4).decodeToString() == "RIFF") { "Not a RIFF file" }
            require(bytes.copyOfRange(8, 12).decodeToString() == "WAVE") { "Not a WAVE file" }

            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            var offset = 12
            var sampleRate = 0
            var channelCount = 0
            var bitsPerSample = 0
            var samples: ByteArray? = null
            while (offset + 8 <= bytes.size) {
                val chunkId = bytes.copyOfRange(offset, offset + 4).decodeToString()
                val chunkSize = buffer.getInt(offset + 4)
                require(chunkSize >= 0 && offset + 8L + chunkSize <= bytes.size) { "Invalid WAV chunk" }
                when (chunkId) {
                    "fmt " -> {
                        require(chunkSize >= 16) { "WAV format chunk is incomplete" }
                        require(buffer.getShort(offset + 8).toInt() == 1) { "Only PCM WAV is supported" }
                        channelCount = buffer.getShort(offset + 10).toInt()
                        sampleRate = buffer.getInt(offset + 12)
                        bitsPerSample = buffer.getShort(offset + 22).toInt()
                    }
                    "data" -> samples = bytes.copyOfRange(offset + 8, offset + 8 + chunkSize)
                }
                offset += 8 + chunkSize + (chunkSize and 1)
            }
            require(sampleRate > 0 && channelCount in 1..2 && bitsPerSample == 16 && samples != null) {
                "Unsupported or incomplete PCM WAV"
            }
            return PcmWave(samples, sampleRate, channelCount, bitsPerSample)
        }
    }
}

private fun createAudioTrack(wave: PcmWave): AudioTrack = AudioTrack.Builder()
    .setAudioAttributes(
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build(),
    )
    .setAudioFormat(
        AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(wave.sampleRate)
            .setChannelMask(
                if (wave.channelCount == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO,
            )
            .build(),
    )
    .setTransferMode(AudioTrack.MODE_STATIC)
    .setBufferSizeInBytes(wave.samples.size)
    .build()
