package com.aware.app.data

import java.time.LocalDate

internal fun validProfile(name: String, dateOfBirth: String, today: LocalDate = LocalDate.now()): Boolean {
    val birth = runCatching { LocalDate.parse(dateOfBirth) }.getOrNull() ?: return false
    return name.trim().length in 1..60 && name.none { it.isISOControl() } && !birth.isAfter(today) && birth.year >= 1900
}
