package dev.autocomplete.adapters.intellij

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import java.util.concurrent.atomic.AtomicLong

/**
 * Application-scoped counter for LLM token usage this session.
 *
 * Orthogonal to the rest of the plugin: the domain and application layers know
 * nothing about it, and it is wired via a single callback passed to
 * [dev.autocomplete.adapters.ollama.OllamaLlmClient]. Delete this file plus the
 * `onUsage = { ... }` line in [CompletionRuntime] and the two settings fields
 * to remove the feature entirely.
 */
@Service(Service.Level.APP)
class TokenUsageTracker {

    private val calls = AtomicLong(0)
    private val promptTokens = AtomicLong(0)
    private val completionTokens = AtomicLong(0)

    fun record(promptTokens: Int, completionTokens: Int) {
        val settings = service<SettingsState>().state
        if (!settings.trackTokenUsage) return

        val nCalls = calls.incrementAndGet()
        val nPrompt = this.promptTokens.addAndGet(promptTokens.toLong())
        val nCompletion = this.completionTokens.addAndGet(completionTokens.toLong())

        val cadence = settings.tokenUsageReportEvery
        if (cadence > 0 && nCalls % cadence == 0L) {
            LOG.info(
                "autocomplete tokens: $nCalls calls, $nPrompt prompt + $nCompletion completion " +
                    "= ${nPrompt + nCompletion} total"
            )
        }
    }

    private companion object {
        val LOG = logger<TokenUsageTracker>()
    }
}
