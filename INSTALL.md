# Installing Local LLM Autocomplete

The plugin ships as a zip you build locally, then load into any JetBrains IDE
via **Install Plugin from Disk**. No JetBrains Marketplace account required.

## Prerequisites

- **JDK 21+** for the build. IntelliJ Platform 2024.2 requires it. `./gradlew`
  is already pinned to a JDK 21 in `gradle.properties`; if that path is not
  valid on your machine, edit `org.gradle.java.home` in `gradle.properties` or
  set `JAVA_HOME` to a JDK 21 install and delete that line.
- **A JetBrains IDE, build 242 or later** (2024.2+). Works in IntelliJ IDEA
  Community/Ultimate, PyCharm Community/Professional, WebStorm, GoLand,
  RubyMine, CLion, Rider, DataGrip, and any other IDE built on the IntelliJ
  Platform, since the plugin only depends on `com.intellij.modules.platform`.
- **[Ollama](https://ollama.com)** running locally with the default model:
  ```sh
  ollama pull qwen2.5-coder:1.5b-base
  ollama serve   # if it isn't already running as a service
  ```
  Any Ollama-hosted FIM-capable model works — set it in Settings after
  installing.

## Build the zip

From the repo root:

```sh
./gradlew buildPlugin
```

The distributable lands at:

```
build/distributions/autocomplete-local-0.1.0.zip
```

The version in the filename tracks `version` in `build.gradle.kts`. Bump it
there if you rebuild after changes so you can tell versions apart in the IDE's
plugin list.

## Install it in your IDE

1. Open the target IDE.
2. **Settings / Preferences** → **Plugins**.
3. Click the gear icon next to the search box → **Install Plugin from Disk…**
4. Select `build/distributions/autocomplete-local-0.1.0.zip`.
5. Click **OK**, then restart the IDE when prompted.

## Configure it

**Settings / Preferences** → **Tools** → **Local LLM Autocomplete**.

| Field                       | Default                                | Notes                                                     |
|-----------------------------|----------------------------------------|-----------------------------------------------------------|
| Endpoint URL                | `http://localhost:11434/api/generate`  | Any Ollama-compatible endpoint.                           |
| Model                       | `qwen2.5-coder:1.5b-base`              | Must be pulled and served by the endpoint.                |
| Context chars               | `4000`                                 | Prefix/suffix character budget sent to the model.         |
| Max tokens                  | `128`                                  | Upper bound on generated tokens per suggestion.           |
| Max lines                   | `5`                                    | Suggestion is truncated to this many lines.               |
| Enabled language IDs        | *(blank = all)*                        | CSV of IntelliJ language IDs (e.g. `Python,Kotlin,JAVA`). |
| Track token usage           | on                                     | Off disables both the counter and the periodic log.       |
| Log token totals every N    | `25`                                   | 0 = never log (counter still accumulates).                |

Settings are stored per-IDE (application scope), not per-project.

## Verify it's working

1. Open any editor and start typing — greyed-out inline suggestions should
   appear after a ~150 ms pause.
2. Accept with **Tab**, dismiss with **Esc**.
3. **Help** → **Show Log in Files/Finder** and `grep autocomplete idea.log`
   should show one `autocomplete request` and one `autocomplete suggestion`
   line per completion, plus an occasional `autocomplete tokens: …` summary.

## Troubleshooting

- **No suggestions appear.** Confirm Ollama is up:
  `curl http://localhost:11434/api/tags`. Then check the IDE's `idea.log`
  for `autocomplete failed after …` lines — the message will name the cause
  (connection refused, wrong model name, timeout).
- **Slow suggestions.** The bundled default model is 1.5 B parameters, which
  is intentionally lightweight. Latency scales with prefix size and model
  size. A larger model gives better completions but pushes p90 latency well
  past what feels interactive.
- **Plugin not offered by "Install from Disk".** Make sure you selected the
  `.zip` from `build/distributions/`, not the exploded `build/idea-sandbox/`
  directory or a raw class-file jar.

## Uninstall

**Settings / Preferences** → **Plugins** → find **Local LLM Autocomplete** →
gear icon → **Uninstall** → restart.
