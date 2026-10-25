package com.aware.app.data

import com.aware.app.llm.Source
import com.aware.app.llm.Summary
import org.junit.Test
import org.junit.Assert.*

class SummaryHistoryTest {
    private fun entry(id: Long, time: Long, text: String = "Quiet") = SummaryRecord(id, time, Summary(text, Source.TEMPLATE))
    @Test fun expiresAtTenMinutesAndRejectsFutureEntries() {
        val now = 700_000L
        val records = listOf(entry(1, 100_000), entry(2, 100_001), entry(3, now), entry(4, now + 1))
        assertEquals(listOf(3L, 2L), recentSummaries(records, now).map { it.id })
    }
    @Test fun refinementReplacesTheSameCycleWithoutDuplicatingHistory() {
        val records = listOf(entry(1, 100), entry(2, 200))
        val updated = upsertSummary(records, entry(2, 250, "Refined"), 300)
        assertEquals(listOf(2L, 1L), updated.map { it.id })
        assertEquals("Refined", updated.first().summary.text)
    }
}
