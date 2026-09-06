package dev.autocomplete.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PromptBuilderTest {

    private val builder = PromptBuilder()

    @Test
    fun `wraps prefix and suffix in FIM sentinels`() {
        val req = CompletionRequest(prefix = "def add(a, b):\n    return ", suffix = "\n\nprint(add(2,3))\n")
        val prompt = builder.build(req, contextChars = 4000)

        assertEquals(
            "<|fim_prefix|>def add(a, b):\n    return <|fim_suffix|>\n\nprint(add(2,3))\n<|fim_middle|>",
            prompt.text,
        )
    }

    @Test
    fun `stop tokens include all FIM sentinels`() {
        val prompt = builder.build(CompletionRequest("x", "y"), contextChars = 4000)
        assertTrue("<|fim_prefix|>" in prompt.stopTokens)
        assertTrue("<|fim_suffix|>" in prompt.stopTokens)
        assertTrue("<|fim_middle|>" in prompt.stopTokens)
        assertTrue("<|endoftext|>" in prompt.stopTokens)
    }

    @Test
    fun `truncates prefix from the far end and suffix from the far end`() {
        // contextChars = 10 -> prefix gets 7 chars (70%), suffix gets 3 (30%)
        val req = CompletionRequest(prefix = "ABCDEFGHIJKLMN", suffix = "opqrstuvwx")
        val prompt = builder.build(req, contextChars = 10)

        // last 7 of prefix, first 3 of suffix
        assertTrue("HIJKLMN" in prompt.text)
        assertTrue("opq" in prompt.text)
        assertTrue("ABCDEFG" !in prompt.text)
        assertTrue("rstuvwx" !in prompt.text)
    }

    @Test
    fun `no truncation when text fits budget`() {
        val req = CompletionRequest(prefix = "hi", suffix = "bye")
        val prompt = builder.build(req, contextChars = 4000)
        assertTrue("hi" in prompt.text)
        assertTrue("bye" in prompt.text)
    }

    @Test
    fun `contextChars of zero yields empty prefix and suffix but still valid prompt`() {
        val prompt = builder.build(CompletionRequest("abc", "def"), contextChars = 0)
        assertEquals("<|fim_prefix|><|fim_suffix|><|fim_middle|>", prompt.text)
    }
}
