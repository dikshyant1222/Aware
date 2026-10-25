package com.aware.app.llm

/** Reads the model's one-line JSON reply without org.json so it also runs in JVM tests. */
object AssessmentParser {
    private val urgency = Regex("\"urgency\"\\s*:\\s*\"([A-Za-z]+)\"")
    private val summary = Regex("\"summary\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")

    fun parse(output: String): Summary? {
        val level = urgency.find(output)?.groupValues?.get(1)?.uppercase()
            ?.let { name -> Urgency.entries.firstOrNull { it.name == name } }
        val raw = summary.find(output)?.groupValues?.get(1)
        if (level == null || raw == null) {
            // A plain sentence is still a usable summary; stray JSON is rejected by the sanitizer.
            return OutputSanitizer.sanitize(output)?.let { Summary(it, Source.LLM) }
        }
        val text = OutputSanitizer.sanitize(raw.replace("\\n", " ").replace("\\\"", "\"").replace("\\\\", "\\")) ?: return null
        return Summary(text, Source.LLM, level)
    }
}
