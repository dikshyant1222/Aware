package com.aware.app.ui

import com.aware.app.alert.SoundType
import com.aware.app.alert.Trend
import com.aware.app.service.FeedItem
import org.junit.Assert.*
import org.junit.Test

class HomePresentationTest {
    private fun event(type: SoundType, time: Long, name: Boolean = false) = FeedItem(time, type, .8f, Trend.STEADY, nameCalled = name)

    @Test fun emptyAndSingleFeedsDoNotGainFiller() {
        assertTrue(recentSurroundings(emptyList()).isEmpty())
        val single = listOf(event(SoundType.DOORBELL, 1))
        assertEquals(single, recentSurroundings(single))
    }
    @Test fun newestOccurrenceWinsWithoutChangingTheSourceFeed() {
        val feed = listOf(event(SoundType.DOORBELL, 1), event(SoundType.DOG_BARK, 2), event(SoundType.DOORBELL, 3))
        val copy = feed.toList()
        assertEquals(listOf(feed[2], feed[1]), recentSurroundings(feed))
        assertEquals(copy, feed)
    }
    @Test fun nameCalledRemainsDistinctFromOrdinarySpeech() {
        val feed = listOf(event(SoundType.SPEECH, 1), event(SoundType.SPEECH, 2, true))
        assertEquals(2, recentSurroundings(feed).size)
    }
    @Test fun expandedProjectionNeverExceedsFiveDistinctSounds() {
        val feed = SoundType.entries.mapIndexed { index, type -> event(type, index.toLong()) }
        val result = recentSurroundings(feed)
        assertEquals(5, result.size)
        assertEquals(feed.takeLast(5).reversed(), result)
        assertEquals(SoundType.entries.size, feed.size)
    }
}
