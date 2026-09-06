package dev.autocomplete.adapters.ollama

import kotlin.test.Test
import kotlin.test.assertEquals

class OllamaJsonTest {

    @Test
    fun `extractResponseField reads plain response`() {
        val json = """{"model":"x","response":"a + b","done":true}"""
        assertEquals("a + b", extractResponseField(json))
    }

    @Test
    fun `extractResponseField unescapes newlines and quotes`() {
        val json = """{"response":"line1\nline2 with \"quotes\""}"""
        assertEquals("line1\nline2 with \"quotes\"", extractResponseField(json))
    }

    @Test
    fun `extractResponseField returns empty when field missing`() {
        assertEquals("", extractResponseField("""{"other":"stuff"}"""))
    }

    @Test
    fun `jsonString escapes special characters`() {
        assertEquals("\"hi\\nthere\"", jsonString("hi\nthere"))
        assertEquals("\"a\\\"b\\\\c\"", jsonString("a\"b\\c"))
    }

    @Test
    fun `extractIntField reads prompt_eval_count`() {
        val json = """{"response":"x","prompt_eval_count":123,"eval_count":42,"done":true}"""
        assertEquals(123, extractIntField(json, "prompt_eval_count"))
        assertEquals(42, extractIntField(json, "eval_count"))
    }

    @Test
    fun `extractIntField returns 0 when field missing`() {
        assertEquals(0, extractIntField("""{"response":"x"}""", "eval_count"))
    }

    @Test
    fun `extractIntField tolerates whitespace and sign`() {
        assertEquals(7, extractIntField("""{ "eval_count" :   7 }""", "eval_count"))
    }
}
