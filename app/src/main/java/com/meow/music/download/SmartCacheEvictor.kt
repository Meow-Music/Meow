package com.meow.music.download

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import java.util.IdentityHashMap
import java.util.TreeSet

/**
 * The song cache's half of Smart Downloads: songs you've streamed stay on the phone in whatever of Smart Downloads'
 * space its picks don't use.
 */
@OptIn(UnstableApi::class)
class SmartCacheEvictor(
    private val maxBytes: () -> Long,
    private val favoured: (String) -> Boolean,
) : CacheEvictor {
    private val rank = IdentityHashMap<CacheSpan, Long>()
    private val spans = TreeSet<CacheSpan> { a, b ->
        val d = (rank[a] ?: a.lastTouchTimestamp).compareTo(rank[b] ?: b.lastTouchTimestamp)
        if (d != 0) d else a.compareTo(b)
    }
    private var size = 0L

    override fun requiresCacheSpanTouches() = true

    override fun onCacheInitialized() {}

    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        if (length != C.LENGTH_UNSET.toLong()) evict(cache, length)
    }

    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        rank[span] = span.lastTouchTimestamp + if (favoured(span.key)) FAVOURED_BONUS_MS else 0L
        spans.add(span)
        size += span.length
        evict(cache, 0)
    }

    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        if (spans.remove(span)) size -= span.length
        rank.remove(span)
    }

    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        onSpanRemoved(cache, oldSpan)
        onSpanAdded(cache, newSpan)
    }

    fun trim(cache: Cache) = synchronized(cache) { evict(cache, 0) }

    private fun evict(cache: Cache, required: Long) {
        val max = maxBytes().coerceAtLeast(MIN_BYTES)
        while (size + required > max && spans.isNotEmpty()) {
            val first = spans.first()
            cache.removeSpan(first)
            if (spans.contains(first)) { spans.remove(first); rank.remove(first); size -= first.length }
        }
    }

    companion object {
        const val MIN_BYTES = 256L * 1024 * 1024
        const val FAVOURED_BONUS_MS = 30L * 86_400_000
    }
}
