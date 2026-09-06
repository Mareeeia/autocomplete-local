package dev.autocomplete.application

import dev.autocomplete.domain.LlmPrompt

data class LlmOptions(
    val maxTokens: Int,
    val stopTokens: List<String>,
)

interface LlmClient {
    /** Calls the underlying LLM and returns the raw completion text. */
    suspend fun complete(prompt: LlmPrompt, opts: LlmOptions): String
}

interface Settings {
    val endpointUrl: String
    val model: String
    val contextChars: Int
    val maxTokens: Int
    val maxLines: Int
    /** If empty, all languages are enabled. */
    val enabledLanguageIds: Set<String>
}
