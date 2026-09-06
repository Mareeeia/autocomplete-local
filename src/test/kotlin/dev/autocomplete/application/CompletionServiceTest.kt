package dev.autocomplete.application

import dev.autocomplete.domain.CompletionRequest
import dev.autocomplete.domain.LlmPrompt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private class FakeLlm(var response: String = "hello", val delayCompletion: CompletableDeferred<Unit>? = null) : LlmClient {
    var lastPrompt: LlmPrompt? = null
    var lastOpts: LlmOptions? = null
    var callCount = 0
    override suspend fun complete(prompt: LlmPrompt, opts: LlmOptions): String {
        callCount++
        lastPrompt = prompt
        lastOpts = opts
        delayCompletion?.await()
        return response
    }
}

private fun testSettings(
    contextChars: Int = 1000,
    maxTokens: Int = 64,
    maxLines: Int = 5,
    enabled: Set<String> = emptySet(),
) = object : Settings {
    override val endpointUrl = "http://localhost:11434/api/generate"
    override val model = "test"
    override val contextChars = contextChars
    override val maxTokens = maxTokens
    override val maxLines = maxLines
    override val enabledLanguageIds = enabled
}

class CompletionServiceTest {

    @Test
    fun `pipes model output through the post-processor`() = runTest {
        val llm = FakeLlm(response = "a + b<|endoftext|>")
        val svc = CompletionService(llm, testSettings())

        val resp = svc.complete(CompletionRequest(prefix = "return ", suffix = "\n"))

        assertEquals("a + b", resp.text)
    }

    @Test
    fun `passes stop tokens and maxTokens from settings`() = runTest {
        val llm = FakeLlm()
        val svc = CompletionService(llm, testSettings(maxTokens = 42))

        svc.complete(CompletionRequest("a", "b"))

        assertEquals(42, llm.lastOpts?.maxTokens)
        assertTrue("<|fim_middle|>" in llm.lastOpts!!.stopTokens)
    }

    @Test
    fun `skips call entirely when language is not in the enabled set`() = runTest {
        val llm = FakeLlm()
        val svc = CompletionService(llm, testSettings(enabled = setOf("Python")))

        val resp = svc.complete(CompletionRequest("a", "b", language = "Kotlin"))

        assertEquals("", resp.text)
        assertEquals(0, llm.callCount)
    }

    @Test
    fun `runs for any language when enabled set is empty`() = runTest {
        val llm = FakeLlm(response = "x")
        val svc = CompletionService(llm, testSettings(enabled = emptySet()))

        val resp = svc.complete(CompletionRequest("a", "b", language = "Whatever"))

        assertEquals("x", resp.text)
    }

    @Test
    fun `propagates cancellation from the caller`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val llm = FakeLlm(delayCompletion = gate)
        val svc = CompletionService(llm, testSettings())

        val job = async { svc.complete(CompletionRequest("a", "b")) }
        job.cancel()
        assertFailsWith<CancellationException> { job.await() }
    }
}
