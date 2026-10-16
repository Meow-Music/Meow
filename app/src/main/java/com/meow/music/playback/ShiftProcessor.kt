package com.meow.music.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong

/**
 * Moves a player's audio in time by whole frames: skips frames to jump ahead, adds silence to hold back.
 */
@UnstableApi
class ShiftProcessor : BaseAudioProcessor() {
    private val requestedUs = AtomicLong()
    private var pendingFrames = 0L
    @Volatile private var appliedFrames = 0L
    @Volatile private var rate = 48_000
    private var frameBytes = 4

    /** Jumps [ms] ahead (negative: holds back that long). */
    fun shift(ms: Long) {
        requestedUs.addAndGet(ms * 1000)
    }

    /** How far the audio has been moved ahead in all (negative: held back). */
    val shiftedMs: Long get() = appliedFrames * 1000 / rate

    @Volatile private var quietFrames = 0L
    private var encodingFloat = false

    /**
     * How long the audio coming through has been near-silent (below about -50 dBFS), in ms.
     */
    val quietMs: Long get() = quietFrames * 1000 / rate

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val bytes = when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_FLOAT -> 4
            else -> return AudioProcessor.AudioFormat.NOT_SET
        }
        rate = inputAudioFormat.sampleRate
        frameBytes = bytes * inputAudioFormat.channelCount
        encodingFloat = bytes == 4
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val add = requestedUs.getAndSet(0)
        if (add != 0L) pendingFrames += add * rate / 1_000_000
        if (pendingFrames > 0) {
            val skip = minOf(pendingFrames, (inputBuffer.remaining() / frameBytes).toLong())
            inputBuffer.position(inputBuffer.position() + (skip * frameBytes).toInt())
            pendingFrames -= skip
            appliedFrames += skip
        }
        val silence = if (pendingFrames < 0) -pendingFrames else 0L
        pendingFrames += silence
        appliedFrames -= silence
        val silentBytes = (silence * frameBytes).toInt()
        if (silentBytes == 0 && !inputBuffer.hasRemaining()) return
        val out = replaceOutputBuffer(silentBytes + inputBuffer.remaining())
        repeat(silentBytes) { out.put(0) }
        out.put(inputBuffer)
        out.flip()
        meter(out)
    }

    /** Counts how long the audio has stayed quiet (the loudest sample in each buffer against [QUIET]). */
    private fun meter(out: ByteBuffer) {
        val sampleBytes = if (encodingFloat) 4 else 2
        var peak = 0f
        var i = out.position()
        val end = out.limit()
        while (i + sampleBytes <= end) {
            val v = if (encodingFloat) kotlin.math.abs(out.getFloat(i)) else kotlin.math.abs(out.getShort(i) / 32768f)
            if (v > peak) peak = v
            i += sampleBytes
        }
        val frames = (end - out.position()).toLong() / frameBytes
        quietFrames = if (peak < QUIET) quietFrames + frames else 0
    }

    override fun onFlush() {
        pendingFrames = 0
        appliedFrames = 0
        quietFrames = 0
        requestedUs.set(0)
    }

    private companion object {
        const val QUIET = 0.003f
    }
}
