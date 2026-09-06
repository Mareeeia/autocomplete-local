package dev.autocomplete.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class CompletionPostProcessorTest {

    private val proc = CompletionPostProcessor()

    @Test
    fun `passes through clean single-line output`() {
        val req = CompletionRequest(prefix = "return ", suffix = "\n")
        assertEquals("a + b", proc.clean("a + b", req, maxLines = 5))
    }

    @Test
    fun `strips leaked FIM sentinels`() {
        val req = CompletionRequest(prefix = "x", suffix = "y")
        assertEquals("hello", proc.clean("hello<|fim_middle|><|endoftext|>", req, maxLines = 5))
    }

    @Test
    fun `caps at maxLines newlines`() {
        val req = CompletionRequest(prefix = "", suffix = "")
        val raw = "a\nb\nc\nd\ne\nf\ng"
        assertEquals("a\nb\nc", proc.clean(raw, req, maxLines = 3))
    }

    @Test
    fun `trims trailing whitespace-only lines`() {
        val req = CompletionRequest(prefix = "", suffix = "")
        assertEquals("a\nb", proc.clean("a\nb\n   \n\t\n", req, maxLines = 10))
    }

    @Test
    fun `cuts a trailing chunk that duplicates the immediate suffix`() {
        // model regurgitated the next line of the file
        val req = CompletionRequest(prefix = "if (x) {\n    ", suffix = "return x\n}\n")
        // "return x\n" is the head of suffix; trailing indent-only line is trimmed as whitespace
        assertEquals("y = x + 1", proc.clean("y = x + 1\n    return x\n", req, maxLines = 10))
    }

    @Test
    fun `returns empty string when result is blank`() {
        val req = CompletionRequest(prefix = "", suffix = "")
        assertEquals("", proc.clean("   \n\t\n", req, maxLines = 5))
    }

    @Test
    fun `maxLines of one keeps only first line`() {
        val req = CompletionRequest(prefix = "", suffix = "")
        assertEquals("first", proc.clean("first\nsecond\nthird", req, maxLines = 1))
    }
}
