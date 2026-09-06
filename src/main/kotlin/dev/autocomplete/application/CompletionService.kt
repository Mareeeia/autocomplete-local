package dev.autocomplete.application

import dev.autocomplete.domain.CompletionPostProcessor
import dev.autocomplete.domain.CompletionRequest
import dev.autocomplete.domain.CompletionResponse
import dev.autocomplete.domain.PromptBuilder

class CompletionService(
    private val llmClient: LlmClient,
    private val settings: Settings,
    private val promptBuilder: PromptBuilder = PromptBuilder(),
    private val postProcessor: CompletionPostProcessor = CompletionPostProcessor(),
) {
    /** Returns a suggestion for [req], or an empty response if none is warranted. */
    suspend fun complete(req: CompletionRequest): CompletionResponse {
        if (!isLanguageEnabled(req.language)) return CompletionResponse("")

        val prompt = promptBuilder.build(req, settings.contextChars)
        val opts = LlmOptions(maxTokens = settings.maxTokens, stopTokens = prompt.stopTokens)
        val raw = llmClient.complete(prompt, opts)
        val cleaned = postProcessor.clean(raw, req, settings.maxLines)
        return CompletionResponse(cleaned)
    }

    private fun isLanguageEnabled(language: String?): Boolean {
        val enabled = settings.enabledLanguageIds
        if (enabled.isEmpty()) return true
        return language != null && language in enabled
    }
}
