package com.aware.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.aware.app.alert.Category
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

data class Settings(
    val enabledCategories: Set<Category> = Category.entries.toSet(),
    val userName: String = "",
    val sensitivity: Float = 0.5f,
    val translationTarget: String? = null,
    val nameDetection: Boolean = true,
    val dateOfBirth: String = "",
    val onboardingComplete: Boolean = false,
    val soundCheckResponses: String = "",
)

private val Context.settingsStore by preferencesDataStore("settings")
val Context.settingsRepository: SettingsRepository
    get() = SettingsRepository.get(applicationContext)

class SettingsRepository internal constructor(private val store: DataStore<Preferences>) {
    val settings: Flow<Settings> = store.data.catch {
        if (it is IOException) emit(emptyPreferences()) else throw it
    }.map { values ->
        Settings(
            enabledCategories = values[CATEGORIES]?.let { names ->
                Category.entries.filter { it.name in names }.toSet()
            } ?: Category.entries.toSet(),
            userName = values[NAME] ?: "",
            sensitivity = (values[SENSITIVITY] ?: 0.5f).let { if (it.isFinite()) it.coerceIn(0f, 1f) else 0.5f },
            translationTarget = values[TARGET],
            nameDetection = true,
            dateOfBirth = values[DOB] ?: "",
            onboardingComplete = values[ONBOARDED] ?: false,
            soundCheckResponses = values[SOUND_CHECK] ?: "",
        )
    }

    suspend fun setEnabledCategories(categories: Set<Category>) = store.edit {
        it[CATEGORIES] = categories.map { category -> category.name }.toSet()
    }.let { Unit }
    suspend fun setCategory(category: Category, enabled: Boolean) = store.edit { values ->
        val names = values[CATEGORIES] ?: Category.entries.map { it.name }.toSet()
        values[CATEGORIES] = if (enabled) names + category.name else names - category.name
    }.let { Unit }
    suspend fun completeOnboarding(name: String, dateOfBirth: String, responses: String) {
        require(validProfile(name, dateOfBirth))
        require(responses.length == 3 && responses.all { it == 'Y' || it == 'N' })
        store.edit {
            if (it[ONBOARDED] != true) {
                it[NAME] = name.trim()
                it[DOB] = dateOfBirth
                it[SOUND_CHECK] = responses
                it[DETECT] = true
                it[ONBOARDED] = true
            }
        }
    }
    suspend fun setSensitivity(value: Float) {
        require(value.isFinite())
        store.edit { it[SENSITIVITY] = value.coerceIn(0f, 1f) }
    }
    suspend fun setTranslationTarget(code: String?) = store.edit {
        if (code == null) it.remove(TARGET) else it[TARGET] = code
    }.let { Unit }

    companion object {
        private val CATEGORIES = stringSetPreferencesKey("categories")
        private val NAME = stringPreferencesKey("name")
        private val DOB = stringPreferencesKey("date_of_birth")
        private val ONBOARDED = booleanPreferencesKey("onboarding_complete")
        private val SOUND_CHECK = stringPreferencesKey("sound_check_responses")
        private val SENSITIVITY = floatPreferencesKey("sensitivity")
        private val TARGET = stringPreferencesKey("translation_target")
        private val DETECT = booleanPreferencesKey("name_detection")
        @Volatile private var instance: SettingsRepository? = null
        internal fun get(context: Context): SettingsRepository = instance ?: synchronized(this) {
            instance ?: SettingsRepository(context.settingsStore).also { instance = it }
        }
    }
}
