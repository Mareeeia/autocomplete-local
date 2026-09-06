# Local LLM Autocomplete — Design

## Goal
A small JetBrains plugin that shows inline "ghost text" code completions produced
by a locally-running Ollama model (default: `qwen2.5-coder`). No network calls
outside `localhost`. Works in IntelliJ IDEA and PyCharm, and ports cleanly to
other JetBrains IDEs built on the IntelliJ Platform.

## Non-goals
- No cloud providers, no telemetry.
- No repository-wide context assembly (RAG). Prompt is built from the current
  file only — prefix before caret + suffix after caret.
- No chat, no multi-turn, no diff/apply flows.
- No IDE-language-specific parsing. We treat the buffer as text.

## Architecture — onion / hexagonal
Three concentric layers. Dependencies only ever point inward.

```
  +---------------------------------------------+
  |  adapters (IntelliJ, Ollama HTTP, config)   |   <- I/O, framework
  |    +-------------------------------------+  |
  |    |  application (use-case orchestration) |  <- pure Kotlin
  |    |    +-----------------------------+  |  |
  |    |    |  domain (types, prompt,     |  |  |   <- pure Kotlin
  |    |    |          post-processing)   |  |  |
  |    |    +-----------------------------+  |  |
  |    +-------------------------------------+  |
  +---------------------------------------------+
```

### Packages
- `dev.autocomplete.domain` — pure data + logic. No I/O, no coroutines, no
  IntelliJ imports. **All TDD lives here.**
  - `CompletionRequest(prefix: String, suffix: String, language: String?)`
  - `CompletionResponse(text: String)`
  - `PromptBuilder` — assembles FIM prompt (`<|fim_prefix|>…<|fim_suffix|>…<|fim_middle|>`)
    with a configurable context-window budget applied to prefix/suffix by
    character count (cheap, deterministic, no tokenizer needed).
  - `CompletionPostProcessor` — trims model output: strips FIM sentinels,
    collapses trailing whitespace, cuts on stop conditions (e.g. don't emit
    text that duplicates the existing suffix), enforces max-line count.
- `dev.autocomplete.application`
  - `CompletionService` — thin orchestrator that takes a `CompletionRequest`,
    calls `PromptBuilder`, invokes the `LlmClient` port, runs
    `CompletionPostProcessor`, returns a `CompletionResponse`.
  - `Ports`:
    - `LlmClient` — `suspend fun complete(prompt: LlmPrompt, opts: LlmOptions): String`
    - `Settings` — read-only accessor for endpoint URL, model, max tokens, etc.
- `dev.autocomplete.adapters.ollama`
  - `OllamaLlmClient` — implements `LlmClient` against `POST /api/generate`
    using `java.net.http.HttpClient` (JDK built-in, no extra dep). Non-streaming
    for the first cut; streaming is a follow-up.
- `dev.autocomplete.adapters.intellij`
  - `OllamaInlineCompletionProvider : InlineCompletionProvider` — the only
    IntelliJ Platform contact point. Reads the editor buffer, builds a
    `CompletionRequest`, calls `CompletionService`, emits an
    `InlineCompletionSingleSuggestion`.
  - `SettingsState` (`PersistentStateComponent`) + a small
    `Configurable` UI (endpoint, model, max tokens, enabled languages).

Only `adapters.intellij` depends on the IntelliJ Platform SDK. Everything else
is plain Kotlin and testable without an IDE fixture.

## The completion request path
1. User pauses typing; platform invokes `InlineCompletionProvider.getSuggestion`.
2. Adapter reads `editor.document.text`, caret offset, and PSI file's language id.
3. Adapter constructs `CompletionRequest(prefix, suffix, language)`.
4. `CompletionService.complete(req)`:
   - `PromptBuilder.build(req, settings.contextChars)` → `LlmPrompt` with FIM tokens.
   - `LlmClient.complete(prompt, LlmOptions(maxTokens, stopTokens))`.
   - `CompletionPostProcessor.clean(raw, req)` → final suggestion text.
5. Adapter yields the suggestion; platform renders ghost text.

Cancellation: the coroutine attached to `InlineCompletionRequest` is cancelled
when the user types or moves the caret; we forward cancellation to the HTTP
call by using `HttpClient.sendAsync` and cancelling the returned `CompletableFuture`.

## Prompt (FIM) format
`qwen2.5-coder` was trained with fill-in-the-middle sentinels:

```
<|fim_prefix|>{prefix}<|fim_suffix|>{suffix}<|fim_middle|>
```

Stop tokens sent to Ollama: the three FIM sentinels plus `<|endoftext|>`,
`<|fim_pad|>`. Model output past a stop token is truncated server-side.

Budget: `prefix` gets ~70% of `contextChars`, `suffix` gets ~30%, both
truncated from the far end (keep text nearest the caret).

## Post-processing rules (pure, unit-tested)
- Strip any leaked FIM sentinel substrings.
- If the model repeats the first N chars of the existing suffix, cut them.
- Cap at `settings.maxLines` newlines (default 5).
- Trim trailing whitespace-only lines.
- Return empty string if the cleaned result is blank; adapter then emits no
  suggestion (nothing shown to the user).

## Settings (persisted per IDE, not per project)
| Key            | Default                             |
|----------------|-------------------------------------|
| endpointUrl    | `http://localhost:11434/api/generate` |
| model          | `qwen2.5-coder:1.5b-base`           |
| contextChars   | `4000`                              |
| maxTokens      | `128`                               |
| maxLines       | `5`                                 |
| enabledLangIds | *empty = all*                       |

## IDE compatibility
- Built with **IntelliJ Platform Gradle Plugin 2.x**.
- `platformType = IC` (IntelliJ Community). Every other JetBrains IDE
  (PyCharm, WebStorm, GoLand, Rider, RubyMine, CLion, …) includes the
  `com.intellij.modules.platform` module we depend on, so the same plugin
  jar installs and runs in all of them.
- `sinceBuild = 242` (2024.2). No `untilBuild` — keep forward-compatible.
- `plugin.xml` declares only `com.intellij.modules.platform` — no
  language-specific module dependencies — so PyCharm/WebStorm/etc. load it.

## Testing strategy (TDD)
- **Domain** — `PromptBuilder`, `CompletionPostProcessor`: plain JUnit 5 in
  `src/test/kotlin`. Fast, no fixture. This is where we write tests first.
- **Application** — `CompletionService` with a fake `LlmClient` that returns
  scripted strings. Verifies orchestration, cancellation, and empty-result
  handling.
- **Ollama adapter** — one integration test guarded by
  `Assumptions.assumeTrue(ollamaReachable())` so CI without Ollama skips it.
  Hits `localhost:11434` with a tiny prompt.
- **IntelliJ adapter** — deferred. Manual smoke test in the sandbox IDE
  (`./gradlew runIde`). Platform test fixtures for `InlineCompletionProvider`
  are heavyweight; not worth it for a small plugin.

## Build layout
Single Gradle project (Kotlin JVM + IntelliJ Platform plugin). Splitting into
subprojects buys us nothing at this size — package discipline is enough to keep
the onion intact, and it will be verified by the fact that the non-adapter
tests don't need the IntelliJ Platform on the test classpath.

`build.gradle.kts` will:
- Apply `org.jetbrains.intellij.platform` plugin.
- Kotlin 2.0, JVM target 17 (IntelliJ 2024.2+ requires JDK 17).
- Depend on `intellijIdeaCommunity("2024.2")`.
- JUnit 5 for tests.

## Open questions / follow-ups (not in v1)
- Streaming completions (would need `HttpClient` line-reader + coroutine `Flow`).
- Debounce / request coalescing beyond what the platform already provides.
- Multi-suggestion carousel.
- Per-project settings and per-language enable/disable UI.
- A tiny status-bar widget showing "Ollama: reachable / model X".
