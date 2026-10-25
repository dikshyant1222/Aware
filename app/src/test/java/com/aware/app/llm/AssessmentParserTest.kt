package com.aware.app.llm

import org.junit.Assert.*
import org.junit.Test

class AssessmentParserTest {
    @Test fun parsesUrgencyAndSummary() {
        val result = AssessmentParser.parse("{\"urgency\":\"critical\",\"summary\":\"Smoke alarm and screaming. Leave now.\"}")!!
        assertEquals(Urgency.CRITICAL, result.urgency)
        assertEquals("Smoke alarm and screaming. Leave now.", result.text)
        assertEquals(Source.LLM, result.source)
    }
    @Test fun toleratesCodeFencesAndEscapedQuotes() {
        val result = AssessmentParser.parse("```json\n{\"urgency\": \"HIGH\", \"summary\": \"Someone said \\\"help\\\" outside\"}\n```")!!
        assertEquals(Urgency.HIGH, result.urgency)
        assertEquals("Someone said help outside", result.text)
    }
    @Test fun plainSentenceFallsBackWithoutUrgency() {
        val result = AssessmentParser.parse("Someone is knocking at the door.")!!
        assertNull(result.urgency)
        assertEquals("Someone is knocking at the door.", result.text)
    }
    @Test fun rejectsUnknownUrgencyShapeAndBrokenJson() {
        assertNull(AssessmentParser.parse("{\"urgency\":\"HIGH\"}"))
        assertNull(AssessmentParser.parse("{\"urgency\":\"MEH\",\"summary\":\"Door knocking now\""))
        assertNull(AssessmentParser.parse(""))
    }
}
