package com.aware.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.aware.app.llm.Source
import com.aware.app.llm.Summary
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

private val Context.summaryStore by preferencesDataStore("recent_summaries")
class SummaryHistoryRepository(context: Context) {
    private val store = context.applicationContext.summaryStore
    val history = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { decode(it[HISTORY]) }

    suspend fun publish(record: SummaryRecord) {
        store.edit { values -> values[HISTORY] = encode(upsertSummary(decode(values[HISTORY]), record, System.currentTimeMillis())) }
    }
    suspend fun prune() {
        store.edit { values -> values[HISTORY] = encode(recentSummaries(decode(values[HISTORY]), System.currentTimeMillis())) }
    }
    private fun decode(value: String?): List<SummaryRecord> = runCatching {
        val array = JSONArray(value ?: "[]")
        (0 until array.length()).map { i -> array.getJSONObject(i).let {
            SummaryRecord(it.getLong("id"), it.getLong("time"), Summary(it.getString("text"), Source.valueOf(it.getString("source"))))
        } }
    }.getOrDefault(emptyList())
    private fun encode(records: List<SummaryRecord>) = JSONArray().apply {
        records.forEach { record -> put(JSONObject().put("id", record.id).put("time", record.timeMs)
            .put("text", record.summary.text).put("source", record.summary.source.name)) }
    }.toString()
    companion object { private val HISTORY = stringPreferencesKey("entries") }
}
