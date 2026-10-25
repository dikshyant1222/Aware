package com.aware.app.llm

import com.aware.app.alert.Category
import com.aware.app.alert.SoundType

class TemplateSummarizer(
    private val name: (SoundType) -> String,
    private val message: (SituationContext.Event) -> String,
) : SituationSummarizer {
    override suspend fun summarize(context: SituationContext): Summary {
        val event = context.events.firstOrNull()
        val speech = context.speech.firstOrNull()
        val emergency = context.events.firstOrNull { it.category == Category.EMERGENCY }
        val door = context.events.any { it.type == SoundType.KNOCK || it.type == SoundType.DOORBELL }
        val text = when {
            emergency != null -> message(emergency)
            door && speech != null -> "Someone at the door said: \"${words(speech.text, 6)}\""
            context.events.any { it.type == SoundType.BABY_CRY } -> "Baby is crying. Check on the baby."
            event != null && speech != null -> "${name(event.type)} nearby; someone said: \"${words(speech.text, 5)}\""
            speech != null -> "Someone nearby said: \"${words(speech.text, 8)}\""
            event != null -> message(event)
            else -> "All quiet. No important sounds recently."
        }
        return Summary(words(text, 15), Source.TEMPLATE)
    }

    private fun words(text: String, limit: Int) = text.trim().split(Regex("\\s+")).take(limit).joinToString(" ")
}
