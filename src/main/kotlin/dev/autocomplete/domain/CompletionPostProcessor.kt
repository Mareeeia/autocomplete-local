package dev.autocomplete.domain

class CompletionPostProcessor {

    fun clean(raw: String, req: CompletionRequest, maxLines: Int): String {
        require(maxLines >= 1) { "maxLines must be >= 1" }

        var text = stripSentinels(raw)
        text = cutIfDuplicatesSuffix(text, req.suffix)
        text = capLines(text, maxLines)
        text = trimTrailingBlankLines(text)

        return if (text.isBlank()) "" else text
    }

    private fun stripSentinels(text: String): String {
        var result = text
        for (sentinel in FIM_SENTINELS) {
            val idx = result.indexOf(sentinel)
            if (idx >= 0) result = result.substring(0, idx)
        }
        return result
    }

    private fun cutIfDuplicatesSuffix(text: String, suffix: String): String {
        if (suffix.isEmpty()) return text
        // Find the longest tail of `text` that is a prefix of `suffix` (of meaningful length).
        val maxOverlap = minOf(text.length, suffix.length)
        for (len in maxOverlap downTo MIN_SUFFIX_OVERLAP) {
            if (text.regionMatches(text.length - len, suffix, 0, len)) {
                return text.substring(0, text.length - len)
            }
        }
        return text
    }

    private fun capLines(text: String, maxLines: Int): String {
        val lines = text.split('\n')
        if (lines.size <= maxLines) return text
        return lines.subList(0, maxLines).joinToString("\n")
    }

    private fun trimTrailingBlankLines(text: String): String {
        val lines = text.split('\n').toMutableList()
        while (lines.isNotEmpty() && lines.last().isBlank()) {
            lines.removeAt(lines.size - 1)
        }
        return lines.joinToString("\n")
    }

    private companion object {
        const val MIN_SUFFIX_OVERLAP = 4
    }
}
