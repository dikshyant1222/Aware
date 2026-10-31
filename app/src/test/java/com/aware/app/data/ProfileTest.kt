package com.aware.app.data

import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import java.time.LocalDate

class ProfileTest {
    @get:Rule val directory = TemporaryFolder()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val store by lazy { PreferenceDataStoreFactory.create(scope = scope) { directory.root.resolve("test.preferences_pb") } }
    @After fun release() = runBlocking { scope.coroutineContext[Job]!!.cancelAndJoin() }

    @Test fun profileRequiresNameAndValidPastDate() {
        val today = LocalDate.of(2026, 10, 25)
        assertTrue(validProfile("  José Smith  ", "2001-03-14", today))
        assertFalse(validProfile(" ", "2001-03-14", today))
        assertFalse(validProfile("Joe", "2026-10-26", today))
        assertFalse(validProfile("Joe", "2001-02-30", today))
        assertFalse(validProfile("Joe\nSmith", "2001-03-14", today))
    }
    @Test fun onboardingIsAtomicAndCannotReplaceAnExistingProfile() = runBlocking {
        val repository = SettingsRepository(store)
        repository.completeOnboarding("  Alex  ", "2000-01-02", "YNY")
        repository.completeOnboarding("Other", "1999-01-01", "NNN")
        val settings = SettingsRepository(store).settings.first()
        assertEquals("Alex", settings.userName)
        assertEquals("2000-01-02", settings.dateOfBirth)
        assertEquals("YNY", settings.soundCheckResponses)
        assertTrue(settings.onboardingComplete)
        assertTrue(settings.nameDetection)
    }
    @Test fun oldDisabledNamePreferenceCannotDisableNameDetection() = runBlocking {
        store.edit { it[booleanPreferencesKey("name_detection")] = false }
        assertTrue(SettingsRepository(store).settings.first().nameDetection)
    }
}
