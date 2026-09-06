package dev.autocomplete.domain

data class CompletionRequest(
    val prefix: String,
    val suffix: String,
    val language: String? = null,
)

data class CompletionResponse(val text: String) {
    val isEmpty: Boolean get() = text.isEmpty()
}

data class LlmPrompt(
    val text: String,
    val stopTokens: List<String>,
)
