package com.aware.app.llm

import com.aware.app.alert.SoundType
import com.aware.app.alert.Trend
import org.junit.Assert.*
import org.junit.Test

class SituationContextTest {
    @Test fun emptyJson() {
        assertEquals("{\"events\":[],\"speech\":[]}", SituationContext().toJson())
    }

    @Test fun escapesSpeechAndTranslation() {
        val json = SituationContext(speech = listOf(SituationContext.SpeechLine("Say \"hi\"\nC:\\door\r\t\u0001", 1.25, "\"hola\"\n"))).toJson()
        assertEquals("{\"events\":[],\"speech\":[{\"text\":\"Say \\\"hi\\\"\\nC:\\\\door\\r\\t\\u0001\",\"secondsAgo\":1.25,\"translation\":\"\\\"hola\\\"\\n\"}]}", json)
        assertFalse(json.contains('\n'))
    }

    @Test fun limitsAndSortsRecentItems() {
        val context = SituationContext((20 downTo 0).map { event(it.toDouble()) },
            (12 downTo 0).map { SituationContext.SpeechLine("line $it", it.toDouble()) })
        assertEquals(8, context.events.size)
        assertEquals((0..7).map { it.toDouble() }, context.events.map { it.secondsAgo })
        assertEquals(6, context.speech.size)
        assertEquals("line 0", context.speech.first().text)
        assertEquals(8, Regex("\"sound\":").findAll(context.toJson()).count())
        assertEquals(6, Regex("\"text\":").findAll(context.toJson()).count())
    }

    @Test fun agesExcludeFutureAndOlderThanThirtySeconds() {
        val ages = listOf(-0.001, 0.0, 29.999, 30.0, 30.001, Double.NaN)
        val context = SituationContext(ages.map(::event), ages.map { SituationContext.SpeechLine("hello there", it) })
        assertEquals(listOf(0.0, 29.999, 30.0), context.events.map { it.secondsAgo })
        assertEquals(listOf(0.0, 29.999, 30.0), context.speech.map { it.secondsAgo })
        assertTrue(context.toJson().contains("\"secondsAgo\":29.999"))
    }

    @Test fun serializesEventFieldsAndFiniteConfidence() {
        val json = SituationContext(listOf(event(2.5).copy(confidence = Float.NaN))).toJson()
        assertEquals("{\"events\":[{\"sound\":\"KNOCK\",\"category\":\"HOME\",\"trend\":\"APPROACHING\",\"confidence\":0.0,\"secondsAgo\":2.5}],\"speech\":[]}", json)
    }

    @Test fun ignoresSpeechDetectionsAndBlankLines() {
        assertTrue(SituationContext(listOf(event(0.0).copy(type = SoundType.SPEECH)),
            listOf(SituationContext.SpeechLine("  ", 0.0))).let { it.events.isEmpty() && it.speech.isEmpty() })
    }

    private fun event(age: Double) = SituationContext.Event(SoundType.KNOCK, trend = Trend.APPROACHING, confidence = .8f, secondsAgo = age)
}
