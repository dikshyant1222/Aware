package com.aware.app.llm

import com.aware.app.alert.Category
import com.aware.app.alert.SoundType
import com.aware.app.alert.Trend

enum class Urgency { LOW, NORMAL, HIGH, CRITICAL }

/**
 * The deterministic floor under the LLM's judgement. It runs instantly on every alert, so a slow or
 * missing model can never delay or weaken a safety escalation.
 */
object SituationRules {
    private val distress = Regex("(?i)\\b(help|fire|gun|call (?:911|999|112|an ambulance|the police)|intruder|burglar)\\b")

    fun floor(context: SituationContext): Urgency {
        val types = context.events.map { it.type }.toSet()
        val emergencies = types.filter { it.category == Category.EMERGENCY }
        val approaching = context.events.any { it.type == SoundType.VEHICLE && it.trend == Trend.APPROACHING }
        return when {
            emergencies.size >= 2 -> Urgency.CRITICAL
            emergencies.isNotEmpty() && context.speech.any { distress.containsMatchIn(it.text) } -> Urgency.CRITICAL
            emergencies.isNotEmpty() -> Urgency.HIGH
            SoundType.CAR_HORN in types && approaching -> Urgency.HIGH
            types.isEmpty() && context.speech.isEmpty() -> Urgency.LOW
            else -> Urgency.NORMAL
        }
    }

    /**
     * The model may raise urgency by one level over the floor and may lower it only when nothing
     * emergency-like is present, so a hallucination can't trigger or silence a life-safety alert.
     */
    fun resolve(floor: Urgency, model: Urgency?, hasEmergency: Boolean): Urgency {
        if (model == null) return floor
        val cap = Urgency.entries[minOf(floor.ordinal + 1, Urgency.CRITICAL.ordinal)]
        val bounded = if (model > cap) cap else model
        return if (hasEmergency || floor >= Urgency.HIGH) maxOf(floor, bounded) else bounded
    }
}
