package com.aware.app.llm

import com.aware.app.alert.Category
import com.aware.app.alert.SoundType
import com.aware.app.alert.Trend

/** A bounded snapshot, newest first. Ages are measured when the snapshot is taken. */
class SituationContext(events: List<Event> = emptyList(), speech: List<SpeechLine> = emptyList(), maxAgeSeconds: Double = 30.0) {
    data class Event(
        val type: SoundType,
        val category: Category = type.category,
        val trend: Trend,
        val confidence: Float,
        val secondsAgo: Double,
    )

    data class SpeechLine(val text: String, val secondsAgo: Double, val translation: String? = null)

    val events = events.filter { it.secondsAgo in 0.0..maxAgeSeconds && it.type != SoundType.SPEECH }
        .sortedBy { it.secondsAgo }.take(8)
    val speech = speech.filter { it.secondsAgo in 0.0..maxAgeSeconds && it.text.isNotBlank() }
        .sortedBy { it.secondsAgo }.take(6)

    fun toJson(): String = "{\"events\":[" + events.joinToString(",") {
        "{\"sound\":${quote(it.type.name)},\"category\":${quote(it.category.name)}," +
            "\"trend\":${quote(it.trend.name)},\"confidence\":${if (it.confidence.isFinite()) it.confidence.coerceIn(0f, 1f) else 0f}," +
            "\"secondsAgo\":${it.secondsAgo}}"
    } + "],\"speech\":[" + speech.joinToString(",") {
        "{\"text\":${quote(it.text)},\"secondsAgo\":${it.secondsAgo}" +
            (it.translation?.let { translation -> ",\"translation\":${quote(translation)}" } ?: "") + "}"
    } + "]}"

    private fun quote(text: String): String = buildString {
        append('"')
        text.forEach { char ->
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char < ' ') append("\\u%04x".format(char.code)) else append(char)
            }
        }
        append('"')
    }
}
