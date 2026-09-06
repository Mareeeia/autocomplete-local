package dev.autocomplete.adapters.ollama

import dev.autocomplete.application.LlmClient
import dev.autocomplete.application.LlmOptions
import dev.autocomplete.domain.LlmPrompt
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Adapter for Ollama's `POST /api/generate`. Non-streaming.
 *
 * Depends on nothing from the IntelliJ Platform — just JDK HTTP. That keeps
 * this file trivially testable against a real local Ollama.
 */
class OllamaLlmClient(
    private val endpointUrl: String,
    private val model: String,
    private val requestTimeout: Duration = Duration.ofSeconds(30),
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .build(),
    private val onUsage: (promptTokens: Int, completionTokens: Int) -> Unit = { _, _ -> },
) : LlmClient {

    override suspend fun complete(prompt: LlmPrompt, opts: LlmOptions): String {
        val body = buildRequestBody(prompt, opts)
        val request = HttpRequest.newBuilder(URI.create(endpointUrl))
            .timeout(requestTimeout)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val response = sendAsync(request)
        if (response.statusCode() !in 200..299) {
            throw OllamaException(
                "Ollama returned HTTP ${response.statusCode()}: ${response.body().take(200)}"
            )
        }
        val json = response.body()
        val promptTokens = extractIntField(json, "prompt_eval_count")
        val completionTokens = extractIntField(json, "eval_count")
        if (promptTokens > 0 || completionTokens > 0) {
            try {
                onUsage(promptTokens, completionTokens)
            } catch (_: Throwable) {
                // Usage callback must never break a completion.
            }
        }
        return extractResponseField(json)
    }

    private suspend fun sendAsync(request: HttpRequest): HttpResponse<String> =
        suspendCancellableCoroutine { cont: CancellableContinuation<HttpResponse<String>> ->
            val future = http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            cont.invokeOnCancellation { future.cancel(true) }
            future.whenComplete { resp, err ->
                if (err != null) cont.resumeWithException(err) else cont.resume(resp)
            }
        }

    private fun buildRequestBody(prompt: LlmPrompt, opts: LlmOptions): String {
        val stopArray = prompt.stopTokens.plus(opts.stopTokens).distinct()
            .joinToString(",") { jsonString(it) }
        return """
            {
              "model": ${jsonString(model)},
              "prompt": ${jsonString(prompt.text)},
              "stream": false,
              "raw": true,
              "options": {
                "num_predict": ${opts.maxTokens},
                "stop": [$stopArray]
              }
            }
        """.trimIndent()
    }
}

class OllamaException(message: String) : RuntimeException(message)

/**
 * Extract the value of the top-level `"response"` field from Ollama's JSON.
 * We hand-parse to avoid pulling in a JSON dependency — the field is
 * well-defined and the response is a single flat object.
 */
internal fun extractResponseField(json: String): String {
    val key = "\"response\""
    val keyIdx = json.indexOf(key)
    if (keyIdx < 0) return ""
    var i = keyIdx + key.length
    // skip whitespace and colon
    while (i < json.length && (json[i].isWhitespace() || json[i] == ':')) i++
    if (i >= json.length || json[i] != '"') return ""
    i++ // past opening quote
    val out = StringBuilder()
    while (i < json.length) {
        val c = json[i]
        if (c == '\\' && i + 1 < json.length) {
            when (val n = json[i + 1]) {
                '"' -> out.append('"')
                '\\' -> out.append('\\')
                '/' -> out.append('/')
                'n' -> out.append('\n')
                't' -> out.append('\t')
                'r' -> out.append('\r')
                'b' -> out.append('\b')
                'f' -> out.append('')
                'u' -> {
                    if (i + 5 < json.length) {
                        val hex = json.substring(i + 2, i + 6)
                        out.append(hex.toInt(16).toChar())
                        i += 4
                    }
                }
                else -> out.append(n)
            }
            i += 2
        } else if (c == '"') {
            return out.toString()
        } else {
            out.append(c)
            i++
        }
    }
    return out.toString()
}

/**
 * Extract an integer value for a top-level numeric field (e.g. `"eval_count": 42`).
 * Returns 0 if the field is missing or unparseable.
 */
internal fun extractIntField(json: String, fieldName: String): Int {
    val key = "\"$fieldName\""
    val keyIdx = json.indexOf(key)
    if (keyIdx < 0) return 0
    var i = keyIdx + key.length
    while (i < json.length && (json[i].isWhitespace() || json[i] == ':')) i++
    val start = i
    if (i < json.length && (json[i] == '-' || json[i] == '+')) i++
    while (i < json.length && json[i].isDigit()) i++
    return if (i > start) json.substring(start, i).toIntOrNull() ?: 0 else 0
}

/** Minimal JSON string encoder — quotes and escapes only what JSON requires. */
internal fun jsonString(s: String): String {
    val sb = StringBuilder(s.length + 2)
    sb.append('"')
    for (c in s) {
        when (c) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            '\b' -> sb.append("\\b")
            '' -> sb.append("\\f")
            else -> if (c.code < 0x20) sb.append("\\u%04x".format(c.code)) else sb.append(c)
        }
    }
    sb.append('"')
    return sb.toString()
}
