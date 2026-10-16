package com.meow.music.playback

import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.meow.music.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sin

/**
 * Crossfade (after Spotify's): when a song runs into the next one, the ending song fades out while the next one fades
 * in, both at their own speed, instead of one stopping and the next starting.
 */
@OptIn(UnstableApi::class)
class MixEngine(
    private val main: ExoPlayer,
    private val scope: CoroutineScope,
    private val tempo: MixTempo,
    private val mixMs: () -> Long?,
    private val auto: () -> Boolean,
    private val beatMatch: () -> Boolean,
    private val allowed: (from: MediaItem, to: MediaItem) -> Boolean,
    private val scanFromPhone: (String) -> Unit,
    private val buildTail: (ShiftProcessor) -> ExoPlayer,
) {
    private class Plan(
        val fromId: String,
        val toId: String,
        val toIndex: Int,
        val startMs: Long,
        val inMs: Long,
        val lengthMs: Long,
        val ratio: Float,
        val beats: Boolean,
        val beatMs: Double = 0.0,
    )

    private enum class Phase { IDLE, PREPARED, SYNCING, BLENDING, SETTLING }

    private var phase = Phase.IDLE
    private var plan: Plan? = null
    private var tail: ExoPlayer? = null
    private var shifter: ShiftProcessor? = null
    private var blendStartedAt = 0L
    private var settleFrom = 1f
    private var settleStartedAt = 0L
    private var noPlanFor: String? = null
    private var lastSyncLog = 0L
    private var syncActionAt = 0L
    private var catchUpUntil = 0L
    private var mainLead = Long.MIN_VALUE
    private var tailLead = Long.MIN_VALUE
    private var windowFrom = 0L
    private var offset: Long? = null
    private var clockLead = Long.MIN_VALUE
    private var handoverMs = 0L
    private var loop: Job? = null

    fun start() {
        if (loop?.isActive == true) return
        loop = scope.launch {
            while (isActive) {
                val wait = runCatching { tick() }.getOrElse { e ->
                    Log.w(TAG, "mix step failed", e)
                    abort("error")
                    500L
                }
                delay(wait)
            }
        }
    }

    fun release() {
        loop?.cancel()
        abort("released")
    }

    private var scanAskedFor: String? = null
    private var rushedAt = 0L
    private var rushFrom = 0f

    private fun tick(): Long {
        val length = mixMs()
        if (length == null) {
            if (phase != Phase.IDLE) abort("Mix turned off")
            return 1000
        }
        return when (phase) {
            Phase.IDLE -> idle(length)
            Phase.PREPARED -> prepared()
            Phase.SYNCING -> syncing()
            Phase.BLENDING -> blending()
            Phase.SETTLING -> settling()
        }
    }

    private fun idle(chosen: Long): Long {
        val length = if (auto()) AUTO_MAX_MS else chosen
        if (!main.isPlaying) return 500
        val item = main.currentMediaItem ?: return 500
        val dur = main.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: return 500
        val musicEnd = tempo.cached(item.mediaId)?.lastSoundMs?.takeIf { it in dur / 2..dur } ?: dur
        val left = musicEnd - main.currentPosition
        if (dur - main.currentPosition < length + 60_000 && scanAskedFor != item.mediaId && tempo.cached(item.mediaId)?.edgesUnknown != false) {
            scanAskedFor = item.mediaId
            scanFromPhone(item.mediaId)
        }
        val planAhead = length + LEAD_MS + PREPARE_MS + 5_000
        if (left > planAhead) return minOf(2000, left - planAhead).coerceAtLeast(250)
        if (noPlanFor == item.mediaId) return 1000
        val p = makePlan(item, dur, length) ?: run { noPlanFor = item.mediaId; return 1000 }
        if (main.currentPosition > p.startMs - LEAD_MS - 1000) {
            noPlanFor = item.mediaId
            return 1000
        }
        if (main.currentPosition < p.startMs - LEAD_MS - PREPARE_MS - 3000) return 250
        val shift = ShiftProcessor()
        val t = buildTail(shift)
        shifter = shift
        t.volume = 0f
        t.setMediaItem(item.buildUpon().build())
        t.seekTo(p.startMs - LEAD_MS)
        t.playWhenReady = false
        t.prepare()
        tail = t
        plan = p
        phase = Phase.PREPARED
        Log.i(TAG, "planned ${p.fromId} -> ${p.toId}: blend at ${p.startMs} ms for ${p.lengthMs} ms, next from ${p.inMs} ms" +
            if (p.beats) ", tempo ×%.3f, on the beat".format(p.ratio) else "")
        return 50
    }

    private fun makePlan(item: MediaItem, dur: Long, length: Long): Plan? {
        val toIndex = main.nextMediaItemIndex.takeIf { it != C.INDEX_UNSET } ?: return null
        if (main.repeatMode == Player.REPEAT_MODE_ONE) return null
        val next = main.getMediaItemAt(toIndex)
        if (next.mediaId.isBlank() || next.mediaId == item.mediaId || !allowed(item, next)) return null
        val a = tempo.cached(item.mediaId)
        val b = tempo.cached(next.mediaId)
        val nextDur = (next.mediaMetadata.durationMs ?: 0L).takeIf { it > 0 } ?: Long.MAX_VALUE
        val len = minOf(if (auto()) autoLengthMs(a, b) else length, dur / 3, nextDur / 3)
        if (len < 1000 || dur < 30_000) return null
        val end = (a?.lastSoundMs?.takeIf { it in dur / 2..dur } ?: dur)
        var start = end - len
        var inMs = (b?.firstSoundMs ?: 0L).coerceIn(0, 10_000)
        var ratio = 1f
        var beats = false
        var beatMs = 0.0
        if (beatMatch() && a != null && b != null && a.steady && b.steady) {
            val bBpm = listOf(b.bpm, b.bpm * 2, b.bpm / 2).minBy { abs(ln(it / a.bpm)) }
            val r = a.bpm / bBpm
            if (abs(r - 1) <= MAX_STRETCH && b.firstBeatMs <= 4000) {
                val beat = a.periodMs
                val k = floor((start - a.outroBeatMs) / beat)
                start = (a.outroBeatMs + k * beat).toLong()
                inMs = b.firstBeatMs
                ratio = r.toFloat()
                beats = true
                beatMs = beat * r
            }
        }
        if (start < dur / 2) return null
        return Plan(item.mediaId, next.mediaId, toIndex, start, inMs, len, ratio, beats, beatMs)
    }

    private fun prepared(): Long {
        val p = plan ?: return abort("no plan")
        val t = tail ?: return abort("no tail")
        if (main.currentMediaItem?.mediaId != p.fromId) return abort("song changed")
        val pos = main.currentPosition
        if (pos > p.startMs + 200 || pos < p.startMs - LEAD_MS - PREPARE_MS - 15_000) return abort("seeked away")
        if (pos < p.startMs - LEAD_MS) return if (p.startMs - LEAD_MS - pos > 400) 100 else 10
        if (t.playbackState != Player.STATE_READY) {
            if (pos < p.startMs - MIN_SYNC_MS) return 50
            return abort("tail not ready (the song's ending didn't load in time)")
        }
        if (abs(pos - t.currentPosition) > 1000) t.seekTo(pos)
        t.playWhenReady = main.playWhenReady
        mainLead = Long.MIN_VALUE; tailLead = Long.MIN_VALUE; windowFrom = 0L
        offset = null
        clockLead = Long.MIN_VALUE
        handoverMs = 0
        syncActionAt = SystemClock.elapsedRealtime()
        phase = Phase.SYNCING
        return 20
    }

    private fun tailPosition(t: ExoPlayer): Long = t.currentPosition + (shifter?.shiftedMs ?: 0L)

    private fun syncing(): Long {
        val p = plan ?: return abort("no plan")
        val t = tail ?: return abort("no tail")
        if (main.currentMediaItem?.mediaId != p.fromId) return abort("song changed")
        t.playWhenReady = main.playWhenReady
        val pos = main.currentPosition
        if (pos > p.startMs + 500 || pos < p.startMs - LEAD_MS - 2000) return abort("seeked away")
        val now = SystemClock.elapsedRealtime()
        val drift = tailPosition(t) - pos
        if (t.isPlaying && main.isPlaying && now - syncActionAt > SETTLE_READINGS_MS) {
            mainLead = maxOf(mainLead, pos - now)
            tailLead = maxOf(tailLead, tailPosition(t) - now)
            if (windowFrom == 0L) windowFrom = now
            if (now - windowFrom >= WINDOW_MS) {
                val off = tailLead - mainLead
                offset = off
                clockLead = mainLead
                mainLead = Long.MIN_VALUE; tailLead = Long.MIN_VALUE; windowFrom = 0L
                Log.d(TAG, "tail $off ms off (shifted ${shifter?.shiftedMs} ms), ${p.startMs - pos} ms to the blend")
                if (abs(off) > IN_STEP_MS && p.startMs - pos > SETTLE_READINGS_MS + WINDOW_MS + LONG_HANDOVER_MS + 100) {
                    shifter?.shift(-off)
                    offset = null
                    syncActionAt = now
                }
            }
        }
        if (BuildConfig.DEBUG && now - lastSyncLog > 100) {
            lastSyncLog = now
            Log.v(TAG, "in step? tail $drift ms off as read, ${p.startMs - pos} ms to the blend")
        }
        val at = if (main.isPlaying && clockLead != Long.MIN_VALUE) maxOf(pos, now + clockLead) else pos
        if (at < p.startMs - LONG_HANDOVER_MS) return 10
        val d = offset
        if (handoverMs == 0L) {
            handoverMs = when {
                d == null || !t.isPlaying || abs(d) > 250 -> {
                    Log.w(TAG, "tail out of step by ${d ?: "an unknown amount"} ms at the blend")
                    return abort("out of step")
                }
                abs(d) > 45 -> LONG_HANDOVER_MS
                else -> HANDOVER_MS
            }
        }
        if (at < p.startMs) {
            val x = ((at - (p.startMs - handoverMs)).toFloat() / handoverMs).coerceIn(0f, 1f)
            t.volume = x
            main.volume = 1f - x
            return 10
        }
        t.volume = 1f
        main.volume = 0f
        main.seekTo(p.toIndex, p.inMs)
        main.playbackParameters = PlaybackParameters(p.ratio, 1f)
        blendStartedAt = now
        syncActionAt = now
        windowFrom = 0L
        catchUpUntil = 0L
        rushedAt = 0L
        phase = Phase.BLENDING
        Log.i(TAG, "blend ${p.fromId} -> ${p.toId} started (tail $d ms off)")
        return 20
    }

    private fun blending(): Long {
        val p = plan ?: return abort("no plan")
        val t = tail ?: return abort("no tail")
        if (main.currentMediaItem?.mediaId != p.toId) return abort("skipped during the blend")
        t.playWhenReady = main.playWhenReady
        val tailProgress = (tailPosition(t) - p.startMs).toFloat() / p.lengthMs
        val mainProgress = if (main.isPlaying) (main.currentPosition - p.inMs) / p.ratio / p.lengthMs else 0f
        var progress = minOf(tailProgress, mainProgress).coerceIn(0f, 1f)
        val now = SystemClock.elapsedRealtime()
        if (rushedAt == 0L && main.isPlaying && (shifter?.quietMs ?: 0L) >= QUIET_HANDOFF_MS && progress < 0.9f) {
            rushedAt = now
            rushFrom = progress
            Log.i(TAG, "${p.fromId} went quiet ${(tailPosition(t) - p.startMs)} ms into the blend: bringing ${p.toId} in now")
        }
        if (rushedAt != 0L) progress = maxOf(progress, (rushFrom + (now - rushedAt).toFloat() / RUSH_MS * (1 - rushFrom)).coerceAtMost(1f))
        val ended = t.playbackState == Player.STATE_ENDED
        main.volume = sin(progress * PI / 2).toFloat()
        t.volume = cos(progress * PI / 2).toFloat()
        if (p.beats && t.isPlaying) alignBeats(p, t, progress)
        if (progress >= 1f || ended) {
            t.release()
            tail = null
            shifter = null
            main.volume = 1f
            Log.i(TAG, "blend ${p.fromId} -> ${p.toId} done in ${SystemClock.elapsedRealtime() - blendStartedAt} ms")
            if (main.playbackParameters.speed != 1f) {
                settleFrom = main.playbackParameters.speed
                settleStartedAt = SystemClock.elapsedRealtime()
                phase = Phase.SETTLING
            } else finish()
        }
        return 30
    }

    private fun alignBeats(p: Plan, t: ExoPlayer, progress: Float) {
        val now = SystemClock.elapsedRealtime()
        if (!main.isPlaying) { syncActionAt = now; windowFrom = 0L; return }
        if (catchUpUntil != 0L) {
            if (now < catchUpUntil) return
            main.playbackParameters = PlaybackParameters(p.ratio, 1f)
            catchUpUntil = 0L
            syncActionAt = now
            return
        }
        if (now - syncActionAt < SETTLE_READINGS_MS) return
        if (windowFrom == 0L) { windowFrom = now; mainLead = Long.MIN_VALUE; tailLead = Long.MIN_VALUE }
        val dt = now - windowFrom
        mainLead = maxOf(mainLead, main.currentPosition - (dt * p.ratio).toLong())
        tailLead = maxOf(tailLead, tailPosition(t) - dt)
        if (dt < WINDOW_MS) return
        windowFrom = 0L
        val raw = mainLead - (p.inMs + (tailLead - p.startMs) * p.ratio).toLong()
        val err = raw - (Math.round(raw / p.beatMs) * p.beatMs).toLong()
        Log.d(TAG, "beats ${err} ms apart (${raw} ms in all)")
        when {
            abs(err) <= BEATS_IN_STEP_MS -> syncActionAt = now
            abs(err) > 60 && progress < 0.2f -> {
                main.seekTo(main.currentPosition - err)
                syncActionAt = now
            }
            else -> {
                val faster = err < 0
                main.playbackParameters = PlaybackParameters(p.ratio * (if (faster) 1 + CATCH_UP else 1 - CATCH_UP), 1f)
                catchUpUntil = now + (abs(err) / (CATCH_UP * p.ratio)).toLong()
            }
        }
    }

    private fun settling(): Long {
        val p = plan
        if (p != null && main.currentMediaItem?.mediaId != p.toId) {
            main.playbackParameters = PlaybackParameters.DEFAULT
            finish(); return 250
        }
        val f = ((SystemClock.elapsedRealtime() - settleStartedAt) / SETTLE_MS.toFloat()).coerceIn(0f, 1f)
        main.playbackParameters = PlaybackParameters(settleFrom + (1f - settleFrom) * f, 1f)
        if (f >= 1f) { main.playbackParameters = PlaybackParameters.DEFAULT; finish() }
        return 100
    }

    private fun finish() {
        catchUpUntil = 0L
        phase = Phase.IDLE
        plan = null
        noPlanFor = null
    }

    private fun abort(why: String): Long {
        if (phase != Phase.IDLE) Log.i(TAG, "blend stopped: $why")
        tail?.let { runCatching { it.release() } }
        tail = null
        shifter = null
        if (main.volume != 1f) main.volume = 1f
        if (main.playbackParameters.speed != 1f) main.playbackParameters = PlaybackParameters.DEFAULT
        if (phase == Phase.PREPARED || phase == Phase.SYNCING) plan?.let { noPlanFor = it.fromId }
        catchUpUntil = 0L
        phase = Phase.IDLE
        plan = null
        return 500
    }

    companion object {
        private const val TAG = "MeowMix"

        fun autoLengthMs(ending: Tempo?, next: Tempo?): Long {
            val fade = ending?.fadeOutMs?.takeIf { it >= 0 } ?: return AUTO_UNKNOWN_MS
            val intro = next?.quietIntroMs?.takeIf { it >= 0 } ?: 0L
            return when {
                fade >= AUTO_SHORT_MS -> fade
                intro >= AUTO_SHORT_MS -> minOf(intro, AUTO_INTRO_MAX_MS)
                else -> AUTO_SHORT_MS
            }.coerceIn(AUTO_SHORT_MS, AUTO_MAX_MS)
        }

        const val AUTO_MAX_MS = 12_000L
        private const val AUTO_SHORT_MS = 1_500L
        private const val AUTO_INTRO_MAX_MS = 4_000L
        private const val AUTO_UNKNOWN_MS = 6_000L
        private const val LEAD_MS = 5000L
        private const val HANDOVER_MS = 120L
        private const val LONG_HANDOVER_MS = 350L
        private const val SETTLE_READINGS_MS = 800L
        private const val BEATS_IN_STEP_MS = 15L
        private const val CATCH_UP = 0.05f
        private const val WINDOW_MS = 450L
        private const val IN_STEP_MS = 8L
        private const val PREPARE_MS = 6000L
        private const val MIN_SYNC_MS = 2500L
        private const val QUIET_HANDOFF_MS = 400L
        private const val RUSH_MS = 700L
        private const val MAX_STRETCH = 0.06
        private const val SETTLE_MS = 4000L
    }
}
