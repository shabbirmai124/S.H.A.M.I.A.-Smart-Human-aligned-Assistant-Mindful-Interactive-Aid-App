package com.shamia.assistant.engine

import com.shamia.assistant.model.OpenSourceModelSpec
import com.shamia.assistant.model.UserIdentityProfile
import java.util.Locale

internal object CloudModelResolver {

    fun isModelIntrospectionQuery(input: String): Boolean {
        val lower = input.lowercase(Locale.US).trim()
        return Regex(
            "\\b(which|what)\\s+(?:ai\\s+|llm\\s+)?model\\b|\\bactive\\s+model\\b|\\bcurrent\\s+model\\b|\\brunning\\s+on\\s+which\\s+model\\b|\\bwhich\\s+llm\\b|\\bwhat\\s+llm\\b|\\bmodel\\s+id\\b|\\bare\\s+you\\s+(?:deepseek|gemma|gpt|qwen|llama|phi|glm|minicpm|nemotron|nvidia|smollm)\\b|\\bwho\\s+are\\s+you\\s+and\\s+which\\s+model\\b|\\bverify\\s+(?:the\\s+)?model\\b",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(lower)
    }

    /**
     * Strictly verifies whether an Ollama tag installed or returned by the server
     * matches the exact requested model tag (never allowing a different family or parameter size).
     * Examples:
     * - "nemotron-mini" matches "nemotron-mini" or "nemotron-mini:latest" (NOT "nemotron-nano" or "llama3.2:1b")
     * - "qwen2.5:1.5b" matches "qwen2.5:1.5b" or "qwen2.5:1.5b-instruct-q4_K_M" (NOT "qwen2.5:0.5b" or "qwen3:4b")
     */
    fun matchesExactOllamaTag(actualTag: String, requestedTag: String): Boolean {
        val actual = actualTag.trim().lowercase(Locale.US)
        val requested = requestedTag.trim().lowercase(Locale.US)
        if (actual.isEmpty() || requested.isEmpty()) return false
        if (actual == requested) return true

        val (reqName, reqVariant) = splitTag(requested)
        val (actName, actVariant) = splitTag(actual)
        if (actName != reqName) return false

        // If requested has no explicit variant (e.g. "nemotron-mini"), match "latest" or any quantization of that exact model
        if (reqVariant == null) {
            return actVariant == null || actVariant == "latest" || actVariant.startsWith("q") || actVariant.startsWith("fp")
        }
        // If requested specifies a parameter size/variant (e.g. "1.5b", "1b", "4b"), require exact variant match or quantization suffix
        return actVariant == reqVariant || (actVariant != null && actVariant.startsWith("$reqVariant-"))
    }

    private fun splitTag(tag: String): Pair<String, String?> {
        val idx = tag.indexOf(':')
        return if (idx < 0) tag to null
        else tag.substring(0, idx) to tag.substring(idx + 1).ifBlank { null }
    }

    fun stripHeaderFromHistoryText(text: String): String {
        return text
            .replace(Regex("^\\s*\\[[^\\]]*?(?:Model|Offline Core|ID:|Backend:)[^\\]]*\\]\\s*", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    /**
     * Cleans HTML entities and removes robotic debug prefixes without altering the authentic
     * substance of the exact model's response.
     */
    fun sanitizeReplyForActiveModel(
        rawText: String,
        activeSpec: OpenSourceModelSpec,
        identity: UserIdentityProfile,
        executedBackendId: String
    ): String {
        val ownerName = identity.fullName.trim().ifEmpty { "Md Shabbir Mia" }
        var cleaned = WebSearchClient.decodeHtmlEntities(rawText.trim(), collapseNewlines = false)

        cleaned = cleaned
            .replace(Regex("^\\s*\\[(?:Active\\s+)?Model:[^\\]]+\\]\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^\\s*\\[[^\\]]*?(?:Offline Core|ID:\\s*offline-core|DeepSeek\\s*2B|Web Grounded Fallback|Local Mode)[^\\]]*\\]\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("For full .*? inference offline in Termux.*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("Live Google & Web Search found for you[^:]*:\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("Here is what live open web search found[^:]*:\\s*", RegexOption.IGNORE_CASE), "")
            .trim()

        if (!ownerName.equals("Kajol", ignoreCase = true)) {
            cleaned = cleaned.replace(
                Regex("\\bcreated\\s+by\\s+and\\s+for\\s+Kajol\\b", RegexOption.IGNORE_CASE),
                "created by and for $ownerName"
            )
        }

        return cleaned.trim()
    }
}
