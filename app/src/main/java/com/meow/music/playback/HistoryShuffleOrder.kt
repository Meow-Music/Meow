package com.meow.music.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ShuffleOrder

/**
 * The shuffle order, kept so that what's been heard stays behind the playing song and Previous goes back to the song
 * played last.
 */
@OptIn(UnstableApi::class)
class HistoryShuffleOrder(private val order: IntArray, private val current: () -> Int) : ShuffleOrder {
    private val positions = IntArray(order.size).also { for ((pos, i) in order.withIndex()) it[i] = pos }

    override fun getLength() = order.size
    override fun getNextIndex(index: Int) = positions[index].let { if (it + 1 < order.size) order[it + 1] else C.INDEX_UNSET }
    override fun getPreviousIndex(index: Int) = positions[index].let { if (it > 0) order[it - 1] else C.INDEX_UNSET }
    override fun getLastIndex() = if (order.isEmpty()) C.INDEX_UNSET else order.last()
    override fun getFirstIndex() = if (order.isEmpty()) C.INDEX_UNSET else order.first()

    override fun cloneAndInsert(insertionIndex: Int, insertionCount: Int): ShuffleOrder {
        val added = (insertionIndex until insertionIndex + insertionCount).toList()
        if (order.isEmpty()) return HistoryShuffleOrder(added.shuffled().toIntArray(), current)
        val shifted = order.map { if (it >= insertionIndex) it + insertionCount else it }.toMutableList()
        val cur = current().takeIf { it in order.indices }
        val before = insertionIndex - 1
        val at = when {
            cur == null -> shifted.size
            insertionIndex == cur + 1 -> positions[cur] + 1
            insertionIndex >= order.size -> shifted.size
            before >= 0 && positions[before] > positions[cur] -> positions[before] + 1
            else -> shifted.size
        }
        shifted.addAll(at, added)
        return HistoryShuffleOrder(shifted.toIntArray(), current)
    }

    override fun cloneAndRemove(indexFrom: Int, indexToExclusive: Int): ShuffleOrder {
        val count = indexToExclusive - indexFrom
        val kept = order.filter { it < indexFrom || it >= indexToExclusive }.map { if (it >= indexToExclusive) it - count else it }
        return HistoryShuffleOrder(kept.toIntArray(), current)
    }

    override fun cloneAndMove(indexFrom: Int, indexToExclusive: Int, newIndexFrom: Int): ShuffleOrder {
        val queue = order.indices.toMutableList()
        val moved = queue.subList(indexFrom, indexToExclusive).toList()
        queue.removeAll(moved)
        queue.addAll(newIndexFrom, moved)
        val newIndexOf = IntArray(order.size).also { for ((newIndex, old) in queue.withIndex()) it[old] = newIndex }
        return HistoryShuffleOrder(order.map { newIndexOf[it] }.toIntArray(), current)
    }

    override fun cloneAndSet(insertionCount: Int, startIndex: Int): ShuffleOrder = shuffled(insertionCount, startIndex, current)

    override fun cloneAndClear(): ShuffleOrder = HistoryShuffleOrder(IntArray(0), current)

    fun playingNext(from: Int, target: Int): HistoryShuffleOrder {
        if (from !in order.indices || target !in order.indices || from == target) return this
        val list = order.toMutableList()
        list.remove(target)
        list.add(list.indexOf(from) + 1, target)
        return HistoryShuffleOrder(list.toIntArray(), current)
    }

    companion object {
        fun shuffled(count: Int, first: Int, current: () -> Int): HistoryShuffleOrder {
            val rest = (0 until count).filter { it != first }.shuffled()
            return HistoryShuffleOrder((if (first in 0 until count) listOf(first) + rest else rest).toIntArray(), current)
        }
    }
}
