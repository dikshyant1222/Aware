package com.aware.app.llm

object OutputSanitizer {
    fun sanitize(output: String): String? {
        var text = output.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: return null
        if (text.contains('{') || text.contains('}') || text.contains("as an AI", ignoreCase = true)) return null
        text = text.replace(Regex("[#*0-9]\\uFE0F?\\u20E3"), "")
            .replace(Regex("[\\[\\]`*_#>\"“”]"), "")
            .replace(Regex("!?\\((?:https?://)[^)]*\\)"), "")
        text = buildString {
            text.codePoints().forEach { code ->
                // Emoji, variation selectors, skin tones, joined emoji and keycap marks.
                if (Character.getType(code) != Character.OTHER_SYMBOL.toInt() &&
                    code !in 0x1F000..0x1FAFF && code !in 0x2194..0x2199 && code !in 0x21A9..0x21AA &&
                    code !in 0x2600..0x27BF && code !in 0x2934..0x2935 && code !in 0x2B05..0x2B07 &&
                    code != 0x203C && code != 0x2049 && code != 0x3030 &&
                    code !in 0xFE00..0xFE0F && code != 0x200D && code != 0x20E3 && code != 0x00A9 && code != 0x00AE) {
                    appendCodePoint(code)
                }
            }
        }.trim().trim('\'', '‘', '’')
        repeat(2) {
            text = text.replace(Regex("^(?:[-+•]|\\d+[.)])\\s*"), "")
                .replace(Regex("(?i)^(summary|situation|answer|response)\\s*[:：-]\\s*"), "")
                .trim().trim('\'', '‘', '’')
        }
        text = text.replace(Regex("\\s+"), " ")
        if (text.contains("as an AI", ignoreCase = true)) return null
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(15)
        return if (words.size < 2) null else words.joinToString(" ")
    }
}
