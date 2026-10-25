package com.aware.app.llm

import com.aware.app.alert.SoundType
import com.aware.app.alert.Trend
import kotlin.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class TemplateSummarizerTest {
    private val summarizer = TemplateSummarizer({ it.displayName }) { "${it.type.displayName} ${it.trend.name.lowercase()}. Check now." }
    private val speech = SituationContext.SpeechLine("one two three four five six seven eight nine ten", 0.0)

    @Test fun emergencyWinsAndPreservesTrend() {
        assertEquals("Siren approaching. Check now.", summarize(SituationContext(listOf(event(SoundType.KNOCK), event(SoundType.SIREN, 1.0)), listOf(speech))).text)
    }
    @Test fun knockAndDoorbellQuoteSixWords() {
        listOf(SoundType.KNOCK, SoundType.DOORBELL).forEach {
            assertEquals("Someone at the door said: \"one two three four five six\"", summarize(SituationContext(listOf(event(it)), listOf(speech))).text)
        }
    }
    @Test fun doorRulePrecedesBaby() {
        assertEquals("Someone at the door said: \"one two three four five six\"",
            summarize(SituationContext(listOf(event(SoundType.BABY_CRY), event(SoundType.KNOCK)), listOf(speech))).text)
    }
    @Test fun babyPrecedesGenericSpeech() {
        assertEquals("Baby is crying. Check on the baby.", summarize(SituationContext(listOf(event(SoundType.BABY_CRY)), listOf(speech))).text)
    }
    @Test fun anyEventAndSpeechQuoteFiveWords() {
        assertEquals("Dog barking nearby; someone said: \"one two three four five\"", summarize(SituationContext(listOf(event(SoundType.DOG_BARK)), listOf(speech))).text)
    }
    @Test fun speechOnlyQuotesEightWords() {
        assertEquals("Someone nearby said: \"one two three four five six seven eight\"", summarize(SituationContext(speech = listOf(speech))).text)
    }
    @Test fun eventOnlyUsesLookup() {
        assertEquals("Knock approaching. Check now.", summarize(SituationContext(listOf(event(SoundType.KNOCK)))).text)
    }
    @Test fun quietFallback() {
        assertEquals("All quiet. No important sounds recently.", summarize(SituationContext()).text)
    }
    @Test fun allRulesStayWithinFifteenWordsAndAreDeterministic() {
        SoundType.entries.filter { it.alerts }.forEach { type ->
            listOf(emptyList(), listOf(speech)).forEach { lines ->
                val context = SituationContext(listOf(event(type)), lines)
                val result = summarize(context)
                assertTrue(result.text.split(Regex("\\s+")).size <= 15)
                assertEquals(Source.TEMPLATE, result.source)
                assertEquals(result, summarize(context))
            }
        }
    }
    @Test fun longLookupStillHasFifteenWordLimit() {
        val long = TemplateSummarizer({ it.displayName }) { (1..30).joinToString(" ") }
        assertEquals(15, immediate { long.summarize(SituationContext(listOf(event(SoundType.SIREN)))) }.text.split(" ").size)
    }
    private fun summarize(context: SituationContext) = immediate { summarizer.summarize(context) }
    private fun event(type: SoundType, age: Double = 0.0) = SituationContext.Event(type, trend = Trend.APPROACHING, confidence = .8f, secondsAgo = age)
}

private fun <T> immediate(block: suspend () -> T): T {
    var result: Result<T>? = null
    block.startCoroutine(object : Continuation<T> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(value: Result<T>) { result = value }
    })
    return checkNotNull(result).getOrThrow()
}
