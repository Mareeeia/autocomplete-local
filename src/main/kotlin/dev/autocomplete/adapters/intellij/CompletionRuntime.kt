package dev.autocomplete.adapters.intellij

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import dev.autocomplete.adapters.ollama.OllamaLlmClient
import dev.autocomplete.application.CompletionService
import java.net.http.HttpClient
import java.time.Duration

/**
 * Application-scoped wiring: owns the one HttpClient and hands out fresh
 * CompletionService instances that read current settings per call.
 */
@Service(Service.Level.APP)
class CompletionRuntime : Disposable {

    private val http: HttpClient by lazy {
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build()
    }

    fun newService(): CompletionService {
        val settings = service<SettingsState>()
        val tracker = service<TokenUsageTracker>()
        val client = OllamaLlmClient(
            endpointUrl = settings.endpointUrl,
            model = settings.model,
            http = http,
            onUsage = { p, c -> tracker.record(p, c) },
        )
        return CompletionService(llmClient = client, settings = settings)
    }

    override fun dispose() {
        // HttpClient has no close() before JDK 21; the JVM cleans up its executor.
    }
}
