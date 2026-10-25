package com.aware.app.ui

import com.aware.app.speech.TranscriptLine
import org.junit.Assert.*
import org.junit.Test

class CaptionBatcherTest {
    private fun line(id: Long, text: String, final: Boolean = true) = TranscriptLine(id, text, final, id)

    @Test fun waitsForAvatarAndSendsEveryNewWordInOrder() {
        val batcher = CaptionBatcher(listOf(line(1, "old history")))
        val lines = listOf(line(1, "old history"), line(2, "Hello thank you"), line(3, "Please"))
        assertTrue(batcher.next(lines, true, false).isEmpty())
        val chunks = batcher.next(lines, true, true)
        assertEquals(listOf("Hello", "thank you", "Please"), chunks.map { it.text })
        assertEquals(listOf("2:0", "2:1", "3:0"), chunks.map { it.id })
    }

    @Test fun stablePartialPrefixSignsBeforeFinalAndTailIsNotReplayed() {
        val batcher = CaptionBatcher(emptyList())
        assertTrue(batcher.next(listOf(line(1, "Hello than", false)), true, true).isEmpty())
        val prefix = batcher.next(listOf(line(1, "Hello thank you", false)), true, true)
        assertEquals(listOf("Hello"), prefix.map { it.text })
        val tail = batcher.next(listOf(line(1, "Hello thank you")), true, true)
        assertEquals(listOf("thank you"), tail.map { it.text })
        assertEquals(6, tail.single().start)
        assertEquals(15, tail.single().end)
        assertTrue(batcher.next(listOf(line(1, "Hello thank you")), true, true).isEmpty())
    }

    @Test fun singlePartialWordSignsAfterSettlingEvenWithoutFinalResults() {
        val batcher = CaptionBatcher(emptyList())
        val partial = listOf(line(1, "Hello", false))
        assertTrue(batcher.next(partial, true, true).isEmpty())
        assertEquals(listOf("Hello"), batcher.next(partial, true, true, settled = true).map { it.text })
        assertTrue(batcher.next(listOf(line(1, "Hello")), true, true).isEmpty())
    }

    @Test fun translationAndPartialCorrectionsDoNotReplayCommittedWords() {
        val batcher = CaptionBatcher(emptyList())
        batcher.next(listOf(line(1, "Hello war", false)), true, true)
        assertEquals(listOf("Hello"), batcher.next(listOf(line(1, "Hello world", false)), true, true).map { it.text })
        val final = line(1, "Hello world").copy(translation = "Bonjour monde")
        assertEquals(listOf("world"), batcher.next(listOf(final), true, true).map { it.text })
        assertTrue(batcher.next(listOf(final.copy(translation = "Updated")), true, true).isEmpty())
    }

    @Test fun stopAndClearDoNotReplayHistoryOnResume() {
        val batcher = CaptionBatcher(emptyList())
        batcher.next(listOf(line(1, "Hello")), true, true)
        assertTrue(batcher.next(listOf(line(1, "Hello"), line(2, "Help")), false, true).isEmpty())
        assertTrue(batcher.next(listOf(line(1, "Hello"), line(2, "Help")), true, true).isEmpty())
        assertTrue(batcher.next(emptyList(), true, true).isEmpty())
        assertEquals(listOf("Yes"), batcher.next(listOf(line(3, "Yes")), true, true).map { it.text })
    }

    @Test fun offsetsPreservePunctuationAndNumbersInDisplayedCaptions() {
        val batcher = CaptionBatcher(emptyList())
        val chunks = batcher.next(listOf(line(1, "Hello, I'm 21!")), true, true)
        assertEquals(listOf("Hello", "I'm", "21"), chunks.map { it.text })
        assertEquals(listOf(0, 7, 11), chunks.map { it.start })
        assertEquals(listOf(5, 10, 13), chunks.map { it.end })
    }
}
