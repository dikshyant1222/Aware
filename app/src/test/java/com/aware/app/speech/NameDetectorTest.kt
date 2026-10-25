package com.aware.app.speech

import org.junit.Assert.*
import org.junit.Test

class NameDetectorTest {
    private fun detector(name: String) = NameDetector { 0L }.apply { userName = name }

    @Test fun exactIgnoresCaseAndPunctuation() {
        assertTrue(detector("Alice").matches("Hey, ALICE!"))
    }
    @Test fun fuzzyAllowsOneInsertionDeletionOrSubstitution() {
        assertTrue(detector("Alice").matches("Alyce"))
        assertTrue(detector("Alice").matches("Allice"))
        assertTrue(detector("Alice").matches("Alic"))
        assertFalse(detector("Alice").matches("Alyse"))
    }
    @Test fun shortNamesRequireExactTokens() {
        assertTrue(detector("Ann").matches("Ann, come here"))
        assertFalse(detector("Ann").matches("Ana"))
        assertFalse(detector("Ann").matches("Anna"))
        assertFalse(detector("").matches("hello"))
    }
    @Test fun doesNotMatchInsideAnotherWord() {
        assertFalse(detector("Ann").matches("announcement"))
        assertFalse(detector("Alice").matches("malice"))
        assertFalse(detector("John").matches("Johnathan"))
    }
    @Test fun twoWordNameMustBeAdjacentAndWithinOneEditTotal() {
        assertTrue(detector("Mary Jane").matches("Hi, Mary Jane!"))
        assertTrue(detector("Mary Jane").matches("Mary Jame"))
        assertFalse(detector("Mary Jane").matches("Mary and Jane"))
        assertFalse(detector("Mary Jane").matches("Mery Jame"))
        assertFalse(detector("Jo Li").matches("Jo Lu"))
    }
    @Test fun partialAndFinalShareTenSecondCooldown() {
        var now = 100L
        val detector = NameDetector { now }.apply { userName = "Alice" }
        assertTrue(detector.matches("Alice"))
        assertFalse(detector.matches("Alice come here"))
        now += 9_999
        assertFalse(detector.matches("Alice"))
        now++
        assertTrue(detector.matches("Alice"))
    }
}
