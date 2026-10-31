package com.aware.app.speech

import java.util.Locale

/** Whole tokens only; two-word names must be adjacent. The clock should be monotonic. */
class NameDetector(private val clock: () -> Long = { System.nanoTime() / 1_000_000 }) {
    var userName: String = ""
    private var lastMatchMs: Long? = null

    fun matches(text: String): Boolean {
        val name = tokens(userName)
        if (name.isEmpty() || name.size > 2) return false
        val words = tokens(text)
        val expected = name.joinToString(" ")
        val matched = words.windowed(name.size).any { candidate ->
            val actual = candidate.joinToString(" ")
            val embedded = name.indices.any {
                candidate[it].length > name[it].length && candidate[it].contains(name[it])
            }
            // Short components must still be exact (e.g. "Jo Li").
            !embedded && name.indices.all { name[it].length >= 4 || name[it] == candidate[it] } &&
                (actual == expected || (name.sumOf { it.length } >= 4 && distanceAtMostOne(expected, actual)))
        }
        if (!matched) return false
        val now = clock()
        if (lastMatchMs?.let { now - it < 10_000 } == true) return false
        lastMatchMs = now
        return true
    }

    private fun tokens(text: String): List<String> =
        Regex("[\\p{L}\\p{N}]+").findAll(text.lowercase(Locale.ROOT)).map { it.value }.toList()

    private fun distanceAtMostOne(a: String, b: String): Boolean {
        if (kotlin.math.abs(a.length - b.length) > 1) return false
        var i = 0
        var j = 0
        var edits = 0
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) { i++; j++; continue }
            if (++edits > 1) return false
            if (a.length >= b.length) i++
            if (b.length >= a.length) j++
        }
        return edits + (a.length - i) + (b.length - j) <= 1
    }
}
