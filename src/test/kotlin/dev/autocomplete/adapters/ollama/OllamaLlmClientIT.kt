package dev.autocomplete.adapters.ollama

import dev.autocomplete.application.LlmOptions
import dev.autocomplete.domain.PromptBuilder
import dev.autocomplete.domain.CompletionRequest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Integration test — hits a real local Ollama instance. Skipped if unreachable
 * so the suite is safe to run on CI.
 */
class OllamaLlmClientIT {

    private val endpoint = "http://localhost:11434/api/generate"
    private val model = "qwen2.5-coder:1.5b-base"

    @Test
    fun `completes a tiny FIM prompt end-to-end`() = runBlocking {
        assumeTrue(ollamaReachable(), "Ollama not reachable at localhost:11434")

        val client = OllamaLlmClient(endpoint, model, requestTimeout = Duration.ofSeconds(20))
        val prompt = PromptBuilder().build(
            CompletionRequest(prefix = "def add(a, b):\n    return ", suffix = "\n"),
            contextChars = 4000,
        )
        val out = client.complete(prompt, LlmOptions(maxTokens = 16, stopTokens = emptyList()))

        assertTrue(out.isNotBlank(), "expected non-blank completion, got <$out>")
    }

    @Test
    fun `onUsage callback fires with non-zero token counts`() = runBlocking {
        assumeTrue(ollamaReachable(), "Ollama not reachable at localhost:11434")

        var seenPrompt = 0
        var seenCompletion = 0
        val client = OllamaLlmClient(
            endpointUrl = endpoint,
            model = model,
            requestTimeout = Duration.ofSeconds(20),
            onUsage = { p, c -> seenPrompt = p; seenCompletion = c },
        )
        val prompt = PromptBuilder().build(
            CompletionRequest(prefix = "def add(a, b):\n    return ", suffix = "\n"),
            contextChars = 4000,
        )
        client.complete(prompt, LlmOptions(maxTokens = 8, stopTokens = emptyList()))

        assertTrue(seenPrompt > 0, "expected prompt_eval_count > 0, got $seenPrompt")
        assertTrue(seenCompletion > 0, "expected eval_count > 0, got $seenCompletion")
    }

    private fun ollamaReachable(): Boolean = try {
        val client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(500)).build()
        val req = HttpRequest.newBuilder(URI.create("http://localhost:11434/api/tags"))
            .timeout(Duration.ofMillis(500)).GET().build()
        client.send(req, HttpResponse.BodyHandlers.discarding()).statusCode() in 200..299
    } catch (_: Exception) {
        false
    }
}
