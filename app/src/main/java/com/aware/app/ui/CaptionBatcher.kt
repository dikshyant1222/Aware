package com.aware.app.ui

import com.aware.app.speech.TranscriptLine
import java.util.Locale

internal data class CaptionChunk(val id: String, val lineId: Long, val text: String, val start: Int, val end: Int)
internal data class SigningHighlight(val id: String, val lineId: Long, val start: Int, val end: Int)

/** Streams stable words while recognition is still partial; finals flush only the unsent tail. */
internal class CaptionBatcher(initialLines: List<TranscriptLine>) {
    private var historyId = initialLines.lastOrNull { it.isFinal }?.id ?: 0L
    private val consumed = mutableMapOf<Long, Int>()
    private val observed = mutableMapOf<Long, List<String>>()
    private val words = Regex("[A-Za-z0-9]+(?:['’\\-][A-Za-z0-9]+)*")

    fun next(lines: List<TranscriptLine>, enabled: Boolean, ready: Boolean, settled: Boolean = false): List<CaptionChunk> {
        if (!enabled) {
            historyId = maxOf(historyId, lines.lastOrNull { it.isFinal }?.id ?: 0L)
            lines.forEach { consumed[it.id] = words.findAll(it.text).count() }
            return emptyList()
        }
        if (!ready) return emptyList()
        consumed.keys.retainAll(lines.map { it.id }.toSet())
        observed.keys.retainAll(lines.map { it.id }.toSet())
        return buildList {
            for (line in lines) {
                if (line.id <= historyId) continue
                val tokens = words.findAll(line.text).toList()
                val normalized = tokens.map { it.value.uppercase(Locale.ROOT) }
                val previous = observed.put(line.id, normalized).orEmpty()
                val stable = previous.zip(normalized).takeWhile { (a, b) -> a == b }.size
                val limit = if (line.isFinal || settled) tokens.size else minOf(stable, (tokens.size - 1).coerceAtLeast(0))
                var cursor = consumed[line.id] ?: 0
                while (cursor < limit) {
                    var endWord = cursor + 1
                    if (tokens[cursor].value.uppercase(Locale.ROOT) == "THANK" &&
                        tokens.getOrNull(cursor + 1)?.value?.uppercase(Locale.ROOT) == "YOU") {
                        if (endWord >= limit) break
                        endWord++
                    }
                    val start = tokens[cursor].range.first
                    val end = tokens[endWord - 1].range.last + 1
                    add(CaptionChunk("${line.id}:$cursor", line.id, line.text.substring(start, end), start, end))
                    cursor = endWord
                }
                consumed[line.id] = cursor
            }
        }
    }
}
