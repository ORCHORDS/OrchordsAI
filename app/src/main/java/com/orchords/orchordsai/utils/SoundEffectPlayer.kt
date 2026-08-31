package com.orchords.orchordsai.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import androidx.annotation.RawRes
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * SoundPool wrapper whose load-complete events are delivered on a dedicated
 * background looper. SoundPool fires [SoundPool.OnLoadCompleteListener] on the
 * looper of the thread that constructed it; constructing on a background
 * thread lets queued startup sounds play the moment decoding finishes, even
 * while the main thread is still busy with activity inflation and first
 * composition. Callers (main thread) are never blocked for more than the
 * short pool-construction window.
 */
class SoundEffectPlayer(private val context: Context) {
    private val loadThread = HandlerThread("SoundEffects", Process.THREAD_PRIORITY_AUDIO).apply {
        start()
    }
    private val poolReady = CountDownLatch(1)

    @Volatile
    private var soundPool: SoundPool? = null

    private val loadedSounds = mutableMapOf<Int, Int>()
    private val readySounds = mutableSetOf<Int>()
    private val pendingPlay = mutableMapOf<Int, Float>()
    private val lock = Any()

    init {
        Handler(loadThread.looper).post {
            val pool = SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .build()
            pool.setOnLoadCompleteListener { _, sampleId, status ->
                val pending: Float?
                synchronized(lock) {
                    if (status == 0) {
                        readySounds.add(sampleId)
                    }
                    pending = pendingPlay.remove(sampleId)
                }
                if (pending != null && status == 0) {
                    // Runs on the loader looper: playback starts even while
                    // the main thread is still busy with startup work.
                    pool.play(sampleId, pending, pending, 0, 0, 1f)
                }
            }
            soundPool = pool
            poolReady.countDown()
        }
    }

    private fun awaitPool(): SoundPool? {
        poolReady.await(2, TimeUnit.SECONDS)
        return soundPool
    }

    fun preload(@RawRes vararg resIds: Int) {
        val pool = awaitPool() ?: return
        synchronized(lock) {
            for (resId in resIds) {
                if (resId !in loadedSounds) {
                    loadedSounds[resId] = pool.load(context, resId, 1)
                }
            }
        }
    }

    fun play(@RawRes resId: Int, volume: Float = 1f) {
        val pool = awaitPool() ?: return
        val soundId: Int
        synchronized(lock) {
            soundId = loadedSounds[resId] ?: pool.load(context, resId, 1).also {
                loadedSounds[resId] = it
            }
            if (soundId in readySounds) {
                // Direct play: already decoded.
                pool.play(soundId, volume, volume, 0, 0, 1f)
                return
            }
            pendingPlay[soundId] = volume
        }
    }

    fun release() {
        poolReady.await(2, TimeUnit.SECONDS)
        soundPool?.release()
        soundPool = null
        synchronized(lock) {
            loadedSounds.clear()
            readySounds.clear()
            pendingPlay.clear()
        }
        loadThread.quitSafely()
    }
}
