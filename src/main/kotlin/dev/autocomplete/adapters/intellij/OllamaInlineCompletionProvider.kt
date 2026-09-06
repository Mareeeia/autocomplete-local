package dev.autocomplete.adapters.intellij

import com.intellij.codeInsight.inline.completion.DebouncedInlineCompletionProvider
import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.InlineCompletionProviderID
import com.intellij.codeInsight.inline.completion.InlineCompletionRequest
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionGrayTextElement
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSingleSuggestion
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestion
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import dev.autocomplete.domain.CompletionRequest
import kotlinx.coroutines.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

// Uses the platform's built-in DebouncedInlineCompletionProvider so rapid
// keystrokes replace the in-flight request instead of racing it.
class OllamaInlineCompletionProvider : DebouncedInlineCompletionProvider() {

    override val id: InlineCompletionProviderID =
        InlineCompletionProviderID("dev.autocomplete.ollama")

    override suspend fun getDebounceDelay(request: InlineCompletionRequest): Duration =
        150.milliseconds

    override fun isEnabled(event: InlineCompletionEvent): Boolean =
        event is InlineCompletionEvent.DocumentChange

    override suspend fun getSuggestionDebounced(request: InlineCompletionRequest): InlineCompletionSuggestion {
        val (prefix, suffix, languageId) = readAction {
            val text = request.document.immutableCharSequence.toString()
            val offset = request.endOffset.coerceIn(0, text.length)
            val lang = request.file?.language?.id
            Triple(text.substring(0, offset), text.substring(offset), lang)
        }

        val runtime = service<CompletionRuntime>()
        val settings = service<SettingsState>()
        val startNanos = System.nanoTime()
        LOG.info(
            "autocomplete request: lang=$languageId prefix=${prefix.length}c suffix=${suffix.length}c " +
                "model=${settings.model}"
        )

        val response = try {
            runtime.newService().complete(CompletionRequest(prefix, suffix, languageId))
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            LOG.warn("autocomplete failed after ${elapsedMs(startNanos)}ms: ${t.message}")
            return InlineCompletionSuggestion.Empty
        }

        val elapsed = elapsedMs(startNanos)
        if (response.isEmpty) {
            LOG.info("autocomplete produced no suggestion (${elapsed}ms)")
            return InlineCompletionSuggestion.Empty
        }

        LOG.info("autocomplete suggestion: ${response.text.length}c in ${elapsed}ms")
        return InlineCompletionSingleSuggestion.build {
            emit(InlineCompletionGrayTextElement(response.text))
        }
    }

    private fun elapsedMs(startNanos: Long): Long =
        (System.nanoTime() - startNanos) / 1_000_000

    private companion object {
        val LOG = logger<OllamaInlineCompletionProvider>()
    }
}
