package dev.autocomplete.adapters.intellij

import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.InlineCompletionProvider
import com.intellij.codeInsight.inline.completion.InlineCompletionProviderID
import com.intellij.codeInsight.inline.completion.InlineCompletionRequest
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionGrayTextElement
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSingleSuggestion
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestion
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import dev.autocomplete.adapters.ollama.OllamaLlmClient
import dev.autocomplete.application.CompletionService
import dev.autocomplete.domain.CompletionRequest
import kotlinx.coroutines.CancellationException

class OllamaInlineCompletionProvider : InlineCompletionProvider {

    override val id: InlineCompletionProviderID =
        InlineCompletionProviderID("dev.autocomplete.ollama")

    override fun isEnabled(event: InlineCompletionEvent): Boolean =
        event is InlineCompletionEvent.DocumentChange

    override suspend fun getSuggestion(request: InlineCompletionRequest): InlineCompletionSuggestion {
        val settings = service<SettingsState>()

        val (prefix, suffix, languageId) = readAction {
            val text = request.document.immutableCharSequence.toString()
            val offset = request.endOffset.coerceIn(0, text.length)
            val lang = request.file.language.id
            Triple(text.substring(0, offset), text.substring(offset), lang)
        }

        val service = CompletionService(
            llmClient = OllamaLlmClient(endpointUrl = settings.endpointUrl, model = settings.model),
            settings = settings,
        )

        val response = try {
            service.complete(CompletionRequest(prefix, suffix, languageId))
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            LOG.warn("Local LLM autocomplete failed: ${t.message}")
            return InlineCompletionSuggestion.Empty
        }

        if (response.isEmpty) return InlineCompletionSuggestion.Empty

        return InlineCompletionSingleSuggestion.build {
            emit(InlineCompletionGrayTextElement(response.text))
        }
    }

    private companion object {
        val LOG = logger<OllamaInlineCompletionProvider>()
    }
}
