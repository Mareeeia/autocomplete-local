package dev.autocomplete.domain

private const val FIM_PREFIX = "<|fim_prefix|>"
private const val FIM_SUFFIX = "<|fim_suffix|>"
private const val FIM_MIDDLE = "<|fim_middle|>"
private const val FIM_PAD = "<|fim_pad|>"
private const val END_OF_TEXT = "<|endoftext|>"

internal val FIM_SENTINELS = listOf(FIM_PREFIX, FIM_SUFFIX, FIM_MIDDLE, FIM_PAD, END_OF_TEXT)

class PromptBuilder(
    private val prefixShare: Double = 0.7,
) {
    fun build(req: CompletionRequest, contextChars: Int): LlmPrompt {
        require(contextChars >= 0) { "contextChars must be >= 0" }
        val prefixBudget = (contextChars * prefixShare).toInt()
        val suffixBudget = contextChars - prefixBudget

        val prefix = req.prefix.takeLast(prefixBudget)
        val suffix = req.suffix.take(suffixBudget)

        val text = FIM_PREFIX + prefix + FIM_SUFFIX + suffix + FIM_MIDDLE
        return LlmPrompt(text = text, stopTokens = FIM_SENTINELS)
    }
}
