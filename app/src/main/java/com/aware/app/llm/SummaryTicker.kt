package com.aware.app.llm

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

const val SUMMARY_INTERVAL_MS = 40_000L
internal suspend fun runSummaryTicker(wait: suspend (Long) -> Unit = { delay(it) }, onTick: () -> Unit) {
    while (currentCoroutineContext().isActive) {
        wait(SUMMARY_INTERVAL_MS)
        onTick()
    }
}
