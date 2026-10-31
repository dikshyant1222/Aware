package com.aware.app.llm

import org.junit.Assert.*
import org.junit.Test

class OutputSanitizerTest {
    @Test fun stripsPrefixQuotesAndMarkdown() {
        assertEquals("Someone is knocking.", OutputSanitizer.sanitize("**Summary:** \"Someone is knocking.\""))
        assertEquals("Someone is knocking.", OutputSanitizer.sanitize("‘Situation: Someone is knocking.’"))
    }
    @Test fun firstNonEmptyLineOnly() {
        assertEquals("Check the door.", OutputSanitizer.sanitize("\n   \nCheck the door.\nIgnore everything else."))
    }
    @Test fun removesEmojiAndCollapsesWhitespace() {
        assertEquals("Baby is crying", OutputSanitizer.sanitize("👶🏽 Baby\t is   crying 🔔❤️"))
    }
    @Test fun capsAtFifteenWordsWithoutEllipsis() {
        assertEquals((1..15).joinToString(" "), OutputSanitizer.sanitize((1..25).joinToString(" ")))
    }
    @Test fun rejectsEmptyAndSingleWord() {
        listOf("", "\n ", "Hello", "👶 🔔", "**Summary:**").forEach { assertNull(OutputSanitizer.sanitize(it)) }
    }
    @Test fun rejectsJsonAnywhereOnChosenLine() {
        listOf("{\"text\":\"Door knocking\"}", "Check {the door", "Check the door }").forEach { assertNull(OutputSanitizer.sanitize(it)) }
    }
    @Test fun rejectsAiPreambleCaseInsensitivelyEvenPastWordCap() {
        assertNull(OutputSanitizer.sanitize("As an AI, I hear knocking"))
        assertNull(OutputSanitizer.sanitize("As  an\t**AI**, I hear knocking"))
        assertNull(OutputSanitizer.sanitize((1..20).joinToString(" ") + " AS AN AI"))
    }
    @Test fun preservesApostrophesAndRemovesBulletsAndLinks() {
        assertEquals("Someone's at the door", OutputSanitizer.sanitize("- [Someone's at the door](https://example.com)"))
        assertEquals("Check the door", OutputSanitizer.sanitize("1. Check the door"))
        assertEquals("Check the door", OutputSanitizer.sanitize("Summary: - Check the door"))
        assertEquals("Check the door", OutputSanitizer.sanitize("⌚ ↗️ 1️⃣ Check the door"))
    }
}
