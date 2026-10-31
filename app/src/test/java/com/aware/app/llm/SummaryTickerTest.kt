package com.aware.app.llm

import com.aware.app.alert.SoundType
import com.aware.app.alert.Trend
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.*

class SummaryTickerTest {
    @Test fun tickerWaitsFortySecondsBeforeEachSummaryAndStopsWithItsScope() = runBlocking {
        val waits = mutableListOf<Long>()
        var ticks = 0
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            runSummaryTicker(wait = { ms ->
                waits += ms
                if (waits.size == 3) throw CancellationException()
            }, onTick = { ticks++ })
        }
        job.join()
        assertEquals(listOf(40_000L, 40_000L, 40_000L), waits)
        assertEquals(2, ticks)
    }
    @Test fun fortySecondContextCoversTheWholeAutomaticInterval() {
        val context = SituationContext(events = listOf(
            SituationContext.Event(SoundType.DOORBELL, trend = Trend.STEADY, confidence = .9f, secondsAgo = 39.0),
            SituationContext.Event(SoundType.DOG_BARK, trend = Trend.STEADY, confidence = .9f, secondsAgo = 41.0)), maxAgeSeconds = 40.0)
        assertEquals(listOf(SoundType.DOORBELL), context.events.map { it.type })
    }
}
