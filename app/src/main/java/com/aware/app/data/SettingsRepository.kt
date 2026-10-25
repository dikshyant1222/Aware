package com.aware.app.data

import android.content.Context
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
    val smartSummaries: Boolean = true,
)

private val Context.settingsStore by preferencesDataStore("settings")
val Context.settingsRepository: SettingsRepository
    get() = SettingsRepository.get(applicationContext)

class SettingsRepository private constructor(context: Context) {
    private val store = context.settingsStore
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
            nameDetection = values[DETECT] ?: true,
            smartSummaries = values[SMART_SUMMARIES] ?: true,
        )
    }

    suspend fun setEnabledCategories(categories: Set<Category>) = store.edit {
        it[CATEGORIES] = categories.map { category -> category.name }.toSet()
    }.let { Unit }
    suspend fun setCategory(category: Category, enabled: Boolean) = store.edit { values ->
        val names = values[CATEGORIES] ?: Category.entries.map { it.name }.toSet()
        values[CATEGORIES] = if (enabled) names + category.name else names - category.name
    }.let { Unit }
    suspend fun setUserName(name: String) = store.edit { it[NAME] = name }.let { Unit }
    suspend fun setSensitivity(value: Float) {
        require(value.isFinite())
        store.edit { it[SENSITIVITY] = value.coerceIn(0f, 1f) }
    }
    suspend fun setTranslationTarget(code: String?) = store.edit {
        if (code == null) it.remove(TARGET) else it[TARGET] = code
    }.let { Unit }
    suspend fun setNameDetection(enabled: Boolean) = store.edit { it[DETECT] = enabled }.let { Unit }
    suspend fun setSmartSummaries(enabled: Boolean) = store.edit { it[SMART_SUMMARIES] = enabled }.let { Unit }

    companion object {
        private val CATEGORIES = stringSetPreferencesKey("categories")
        private val NAME = stringPreferencesKey("name")
        private val SENSITIVITY = floatPreferencesKey("sensitivity")
        private val TARGET = stringPreferencesKey("translation_target")
        private val DETECT = booleanPreferencesKey("name_detection")
        private val SMART_SUMMARIES = booleanPreferencesKey("smart_summaries")
        @Volatile private var instance: SettingsRepository? = null
        internal fun get(context: Context): SettingsRepository = instance ?: synchronized(this) {
            instance ?: SettingsRepository(context).also { instance = it }
        }
    }
}
