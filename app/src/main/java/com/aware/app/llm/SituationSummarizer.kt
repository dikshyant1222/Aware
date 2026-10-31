package com.aware.app.llm

interface SituationSummarizer {
    suspend fun summarize(context: SituationContext): Summary?
}

data class Summary(val text: String, val source: Source, val urgency: Urgency? = null)
enum class Source { LLM, TEMPLATE }

sealed interface LlmStatus {
    data object NOT_INSTALLED : LlmStatus
    data object UNSUPPORTED : LlmStatus
    data object IDLE : LlmStatus
    data object LOADING : LlmStatus
    data object READY : LlmStatus
    data class FAILED(val message: String) : LlmStatus
    data object DISABLED : LlmStatus
}
