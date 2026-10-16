package com.meow.music.playback

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import com.meow.music.data.model.Song
import com.meow.music.data.stream.StreamResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * What Mix knows about a song's rhythm and edges, all in ms from the song's start.
 */
data class Tempo(
    val bpm: Double,
    val confidence: Double,
    val firstSoundMs: Long,
    val lastSoundMs: Long,
    val firstBeatMs: Long,
    val outroBeatMs: Long,
    val fadeOutMs: Long = -1,
    val quietIntroMs: Long = -1,
) {
    val periodMs: Double get() = 60_000.0 / bpm
    val steady: Boolean get() = confidence >= MIN_CONFIDENCE

    fun encode() = "$VERSION|$bpm|$confidence|$firstSoundMs|$lastSoundMs|$firstBeatMs|$outroBeatMs|$fadeOutMs|$quietIntroMs"

    val edgesUnknown: Boolean get() = fadeOutMs < 0 || quietIntroMs < 0

    companion object {
        const val MIN_CONFIDENCE = 3.0
        private const val VERSION = 3

        fun decode(s: String): Tempo? = runCatching {
            val p = s.split('|')
            when (p[0].toInt()) {
                VERSION -> Tempo(p[1].toDouble(), p[2].toDouble(), p[3].toLong(), p[4].toLong(), p[5].toLong(), p[6].toLong(), p[7].toLong(), p[8].toLong())
                2 -> Tempo(p[1].toDouble(), p[2].toDouble(), p[3].toLong(), p[4].toLong(), p[5].toLong(), p[6].toLong())
                else -> null
            }
        }.getOrNull()

        fun isOld(s: String) = s.startsWith("2|")

        fun analyze(bands: Array<FloatArray>, frameMs: Double, offsetMs: Double = 0.0): Tempo? {
            val frames = bands.firstOrNull()?.size ?: return null
            if (frames < 3000 / frameMs) return null
            val fps = 1000.0 / frameMs

            val loud = FloatArray(frames) { t ->
                var m = Float.NEGATIVE_INFINITY
                for (b in bands) m = max(m, b[t])
                var s = 0.0
                for (b in bands) s += exp((b[t] - m).toDouble())
                (m + ln(s)).toFloat()
            }
            val sorted = loud.sortedArray()
            val peak = sorted[(sorted.size * 0.95).toInt().coerceAtMost(sorted.lastIndex)]
            val floor = peak - SILENCE_BELOW_PEAK
            val smooth = 5
            fun audible(t: Int): Boolean {
                var n = 0
                for (i in t until min(frames, t + smooth)) if (loud[i] > floor) n++
                return n >= smooth - 1
            }
            val first = (0 until frames).firstOrNull(::audible) ?: return null
            val last = (frames - 1 downTo 0).firstOrNull(::audible) ?: return null
            if (last - first < 10 * fps) return null

            val flux = FloatArray(frames)
            for (t in 1 until frames) {
                var s = 0f
                for (b in bands) { val d = b[t] - b[t - 1]; if (d > 0) s += d }
                flux[t] = s
            }
            val half = (0.2 * fps).toInt().coerceAtLeast(1)
            val pre = DoubleArray(frames + 1).also { for (i in 0 until frames) it[i + 1] = it[i] + flux[i] }
            val od = FloatArray(frames) { t ->
                val lo = max(0, t - half); val hi = min(frames, t + half + 1)
                max(0.0, flux[t] - (pre[hi] - pre[lo]) / (hi - lo)).toFloat()
            }

            val span = min(last - first, (120 * fps).toInt())
            val from = first + ((last - first) - span) / 2
            val to = from + span
            val lagMin = (fps * 60 / 200).toInt()
            val lagMax = (fps * 60 / 60).toInt() + 1
            val acfMax = lagMax * 4
            if (to - from <= acfMax * 2) return null
            val acf = DoubleArray(acfMax + 1)
            for (lag in lagMin..acfMax) {
                var s = 0.0
                for (t in from until to - lag) s += od[t].toDouble() * od[t + lag]
                acf[lag] = s / (to - from - lag)
            }
            fun weighted(lag: Int): Double {
                val bpm = 60 * fps / lag
                val prior = exp(-0.5 * sq(ln(bpm / 120.0) / ln(2.0) / 0.9))
                return (acf[lag] + 0.5 * acf[2 * lag] + 0.25 * acf[4 * lag]) * prior
            }
            val scores = (lagMin..lagMax).map { weighted(it) }
            val bestIdx = scores.indices.maxBy { scores[it] }
            val best = lagMin + bestIdx
            val mean = scores.average()
            val sd = sqrt(scores.sumOf { sq(it - mean) } / scores.size).takeIf { it > 1e-12 } ?: return null
            val confidence = (scores[bestIdx] - mean) / sd
            var lag = if (best in lagMin + 1 until lagMax) {
                val a = acf[best - 1]; val b = acf[best]; val c = acf[best + 1]
                val d = a - 2 * b + c
                best + (if (d < 0) (0.5 * (a - c) / d).coerceIn(-0.5, 0.5) else 0.0)
            } else best.toDouble()

            fun comb(start: Double, b: Int, period: Double): Double {
                var s = 0.0
                var t = start
                while (t < b - 1) { s += interp(od, t); t += period }
                return s
            }

            fun phase(a: Int, b: Int, period: Double = lag): Double {
                var bestPhase = 0.0; var bestScore = -1.0
                var ph = 0.0
                while (ph < period) {
                    val s = comb(a + ph, b, period)
                    if (s > bestScore) { bestScore = s; bestPhase = ph }
                    ph += 0.5
                }
                return a + bestPhase
            }

            if (60 * fps / lag > 140 && 60 * fps / (2 * lag) >= 70) {
                val strong = phase(from, to, 2 * lag)
                val a = comb(strong, to, 2 * lag)
                val b = comb(strong + lag, to, 2 * lag)
                if (a > 1.25 * b) lag *= 2
            }
            run {
                var bestP = lag; var bestS = -1.0
                var p = lag * 0.985
                while (p <= lag * 1.015) {
                    var ph = 0.0
                    while (ph < p) {
                        val s = comb(from + ph, to, p)
                        if (s > bestS) { bestS = s; bestP = p }
                        ph += 1.0
                    }
                    p += lag * 0.0005
                }
                lag = bestP
            }
            val bpm = 60 * fps / lag

            val window = (20 * fps).toInt()
            val introAnchor = phase(first, min(last, first + window))
            var firstBeat = introAnchor
            while (firstBeat - lag >= first - 3) firstBeat -= lag
            while (firstBeat < first - 3) firstBeat += lag
            val outroFrom = max(first, last - window)
            val outroAnchor = phase(outroFrom, last + 1)
            var outroBeat = outroAnchor
            while (outroBeat + lag <= last) outroBeat += lag

            val (fadeOut, quietIntro) = edges(loud, first, last, fps)
            return Tempo(
                bpm = bpm,
                confidence = confidence,
                firstSoundMs = (first * frameMs + offsetMs).toLong(),
                lastSoundMs = (last * frameMs + offsetMs).toLong(),
                firstBeatMs = (firstBeat * frameMs + offsetMs).toLong(),
                outroBeatMs = (outroBeat * frameMs + offsetMs).toLong(),
                fadeOutMs = (fadeOut * frameMs).toLong(),
                quietIntroMs = (quietIntro * frameMs).toLong(),
            )
        }

        private fun edges(loud: FloatArray, first: Int, last: Int, fps: Double): Pair<Int, Int> {
            val ref = loud.max()
            val half = (fps / 2).toInt().coerceAtLeast(1)
            val sums = DoubleArray(loud.size + 1).also { for (i in loud.indices) it[i + 1] = it[i] + exp((loud[i] - ref).toDouble()) }
            fun level(t: Int): Double {
                val lo = max(first, t - half); val hi = min(last + 1, t + half + 1)
                return ln(((sums[hi] - sums[lo]) / (hi - lo)).coerceAtLeast(1e-30)) + ref
            }
            val seconds = (first..last step half).map(::level).sorted()
            val typical = seconds[seconds.size / 2]
            val reach = (EDGE_MAX_S * fps).toInt()
            var fadeFrom = last
            while (fadeFrom > max(first, last - reach) && level(fadeFrom) < typical - FADE_BELOW) fadeFrom--
            var fullFrom = first
            while (fullFrom < min(last, first + reach) && level(fullFrom) < typical - INTRO_BELOW) fullFrom++
            return (last - fadeFrom) to (fullFrom - first)
        }

        private const val EDGE_MAX_S = 20.0
        private const val FADE_BELOW = 1.04
        private const val INTRO_BELOW = 1.38
        private const val SILENCE_BELOW_PEAK = 9.2f

        private fun sq(x: Double) = x * x

        private fun interp(x: FloatArray, t: Double): Double {
            val i = kotlin.math.floor(t).toInt()
            if (i < 0 || i + 1 >= x.size) return 0.0
            val f = t - i
            return x[i] * (1 - f) + x[i + 1] * f
        }
    }
}

/**
 * Scans songs for Mix tempo and loudness.
 */
class MixTempo(
    private val context: Context,
    private val http: OkHttpClient,
    private val streams: StreamResolver,
    private val local: (id: String, scratch: File) -> android.net.Uri? = { _, _ -> null },
) {
    private val prefs = context.getSharedPreferences("mix_tempo", Context.MODE_PRIVATE)
    private val loudPrefs = context.getSharedPreferences("song_loudness", Context.MODE_PRIVATE)
    private val failed = ConcurrentHashMap.newKeySet<String>()
    private val lock = Mutex()

    fun cached(id: String): Tempo? = prefs.getString(id, null)?.let(Tempo::decode)

    fun loudness(id: String): Float? = loudPrefs.getFloat(id, Float.NaN).takeIf { !it.isNaN() }

    private fun scanned(id: String) = loudPrefs.contains(id)

    private fun old(id: String) = prefs.getString(id, null)?.let(Tempo::isOld) == true

    private class Scan(val tempo: Tempo?, val lufs: Float?)

    suspend fun measure(id: String, network: Boolean = true, edges: Boolean = true): Tempo? {
        if ((scanned(id) && !(edges && old(id))) || id in failed) return cached(id)
        return lock.withLock {
            if (scanned(id) && !(edges && old(id))) return@withLock cached(id)
            withContext(Dispatchers.IO) {
                val started = System.nanoTime()
                val before = android.os.Process.getThreadPriority(android.os.Process.myTid())
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
                val r = try {
                    runCatching { scan(id, network) }.onFailure { Log.w(TAG, "scan of $id failed: $it") }
                } finally {
                    android.os.Process.setThreadPriority(before)
                }
                val s = r.getOrNull()
                when {
                    r.isFailure -> { failed += id; cached(id) }
                    s == null -> cached(id)
                    else -> {
                        prefs.edit().putString(id, s.tempo?.encode() ?: "none").apply()
                        loudPrefs.edit().putFloat(id, s.lufs ?: Float.NaN).apply()
                        val ms = (System.nanoTime() - started) / 1_000_000
                        val t = s.tempo
                        Log.i(TAG, "scanned $id in $ms ms: loudness ${s.lufs?.let { "%.1f LUFS".format(it) } ?: "unknown"}, " +
                            (if (t == null) "no clear music" else "%.1f BPM (confidence %.1f), music %d–%d ms, first beat %d ms"
                                .format(t.bpm, t.confidence, t.firstSoundMs, t.lastSoundMs, t.firstBeatMs)))
                        t
                    }
                }
            }
        }
    }

    private fun scan(id: String, network: Boolean): Scan? {
        val file = File(context.cacheDir, "mix_$id.part")
        try {
            val uri = local(id, file) ?: run {
                if (!network || id.startsWith(Song.LOCAL_PREFIX)) return null
                Log.d(TAG, "downloading $id to scan it")
                val a = streams.analysisAudio(id)
                http.newCall(Request.Builder().url(a.url).apply {
                    if (a.contentLength > 0) header("Range", "bytes=0-${a.contentLength - 1}")
                }.build()).execute().use { r ->
                    if (!r.isSuccessful) throw java.io.IOException("audio returned HTTP ${r.code}")
                    file.outputStream().use { out -> r.body.byteStream().copyTo(out) }
                }
                android.net.Uri.fromFile(file)
            }
            val (pcm, rate) = decodeAll(uri) ?: throw java.io.IOException("couldn't decode its audio")
            val lufs = integratedLoudness(pcm, rate)?.let { it + MONO_TO_STEREO_DB }
            val bands = VideoSync.bandFeatures(pcm, rate)
            val frameMs = (rate / 100) * 1000.0 / rate
            return Scan(Tempo.analyze(bands, frameMs, offsetMs = VideoSync.WINDOW / 2 * 1000.0 / rate), lufs)
        } finally {
            file.delete()
        }
    }

    private fun decodeAll(uri: android.net.Uri): Pair<FloatArray, Int>? {
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            ex.setDataSource(context, uri, null)
            val track = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?: return null
            ex.selectTrack(track)
            val fmt = ex.getTrackFormat(track)
            val dec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!).also { codec = it }
            dec.configure(fmt, null, null, 0)
            dec.start()
            var rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var isFloat = false
            var factor = max(1, rate / 11025)
            var out = FloatArray(1 shl 20)
            var count = 0
            var acc = 0f
            var n = 0
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            while (true) {
                if (!inputDone) {
                    val i = dec.dequeueInputBuffer(5_000)
                    if (i >= 0) {
                        val size = runCatching { ex.readSampleData(dec.getInputBuffer(i)!!, 0) }.getOrDefault(-1)
                        if (size < 0) {
                            dec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            dec.queueInputBuffer(i, 0, size, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val o = dec.dequeueOutputBuffer(info, 5_000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val f = dec.outputFormat
                    rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    isFloat = f.containsKey(MediaFormat.KEY_PCM_ENCODING) && f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    if (count == 0) factor = max(1, rate / 11025)
                } else if (o >= 0) {
                    val buf = dec.getOutputBuffer(o)!!.order(ByteOrder.nativeOrder())
                    buf.position(info.offset)
                    buf.limit(info.offset + info.size)
                    val frames = info.size / (channels * (if (isFloat) 4 else 2))
                    for (f in 0 until frames) {
                        var m = 0f
                        for (c in 0 until channels) m += if (isFloat) buf.float else buf.short / 32768f
                        acc += m / channels
                        if (++n == factor) {
                            if (count == out.size) out = out.copyOf(out.size * 2)
                            out[count++] = acc / factor
                            acc = 0f; n = 0
                        }
                    }
                    dec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    if (count > MAX_SECONDS * (rate / factor)) break
                } else if (inputDone && o == MediaCodec.INFO_TRY_AGAIN_LATER && count > 0) {
                    break
                }
            }
            val sr = rate / factor
            if (count < sr * 20) return null
            return out.copyOf(count) to sr
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            ex.release()
        }
    }

    companion object {
        private const val TAG = "MeowMix"
        private const val MAX_SECONDS = 15 * 60
        private const val MONO_TO_STEREO_DB = 3.0f

        fun integratedLoudness(pcm: FloatArray, rate: Int): Float? {
            val block = (0.4 * rate).toInt()
            val hop = (0.1 * rate).toInt().coerceAtLeast(1)
            if (rate <= 0 || pcm.size < block + hop) return null
            val shelf = biquadHighShelf(rate, 1681.974450955533, 3.999843853973347, 0.7071752369554196)
            val pass = biquadHighPass(rate, 38.13547087602444, 0.5003270373238773)
            val sq = DoubleArray(pcm.size + 1)
            var s1 = 0.0; var s2 = 0.0; var p1 = 0.0; var p2 = 0.0
            for (i in pcm.indices) {
                val x = pcm[i].toDouble()
                val y1 = shelf[0] * x + s1
                s1 = shelf[1] * x - shelf[3] * y1 + s2
                s2 = shelf[2] * x - shelf[4] * y1
                val y2 = pass[0] * y1 + p1
                p1 = pass[1] * y1 - pass[3] * y2 + p2
                p2 = pass[2] * y1 - pass[4] * y2
                sq[i + 1] = sq[i] + y2 * y2
            }
            val powers = ArrayList<Double>()
            var start = 0
            while (start + block <= pcm.size) {
                powers += (sq[start + block] - sq[start]) / block
                start += hop
            }
            fun lufs(power: Double) = -0.691 + 10 * kotlin.math.log10(power)
            val loud = powers.filter { it > 0 && lufs(it) > -70 }
            if (loud.isEmpty()) return null
            val relative = lufs(loud.average()) - 10
            val gated = loud.filter { lufs(it) > relative }
            return lufs(gated.ifEmpty { loud }.average()).toFloat()
        }

        private fun biquadHighShelf(rate: Int, fc: Double, gainDb: Double, q: Double): DoubleArray {
            val a = Math.pow(10.0, gainDb / 40)
            val w = 2 * Math.PI * fc / rate
            val cw = kotlin.math.cos(w)
            val alpha = kotlin.math.sin(w) / (2 * q)
            val sa = 2 * kotlin.math.sqrt(a) * alpha
            val b0 = a * ((a + 1) + (a - 1) * cw + sa)
            val b1 = -2 * a * ((a - 1) + (a + 1) * cw)
            val b2 = a * ((a + 1) + (a - 1) * cw - sa)
            val a0 = (a + 1) - (a - 1) * cw + sa
            val a1 = 2 * ((a - 1) - (a + 1) * cw)
            val a2 = (a + 1) - (a - 1) * cw - sa
            return doubleArrayOf(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
        }

        private fun biquadHighPass(rate: Int, fc: Double, q: Double): DoubleArray {
            val w = 2 * Math.PI * fc / rate
            val cw = kotlin.math.cos(w)
            val alpha = kotlin.math.sin(w) / (2 * q)
            val a0 = 1 + alpha
            return doubleArrayOf((1 + cw) / 2 / a0, -(1 + cw) / a0, (1 + cw) / 2 / a0, -2 * cw / a0, (1 - alpha) / a0)
        }
    }
}
