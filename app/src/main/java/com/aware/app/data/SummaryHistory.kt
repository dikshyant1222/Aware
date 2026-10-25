package com.aware.app.data

import com.aware.app.llm.Summary

const val SUMMARY_HISTORY_MS = 10 * 60 * 1000L
data class SummaryRecord(val id: Long, val timeMs: Long, val summary: Summary)

internal fun recentSummaries(records: List<SummaryRecord>, nowMs: Long): List<SummaryRecord> = records
    .filter { nowMs - it.timeMs in 0 until SUMMARY_HISTORY_MS }.sortedByDescending { it.timeMs }

internal fun upsertSummary(records: List<SummaryRecord>, record: SummaryRecord, nowMs: Long): List<SummaryRecord> =
    recentSummaries(records.filterNot { it.id == record.id } + record, nowMs)
