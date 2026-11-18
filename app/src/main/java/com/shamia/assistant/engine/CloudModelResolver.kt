package com.shamia.assistant.engine

import com.shamia.assistant.model.OpenSourceModelSpec
import com.shamia.assistant.model.UserIdentityProfile
import java.util.Locale

internal object CloudModelResolver {

    fun isModelIntrospectionQuery(input: String): Boolean {
        val lower = input.lowercase(Locale.US).trim()
        return Regex(
            "\\b(which|what)\\s+(?:ai\\s+|llm\\s+)?model\\b|\\bactive\\s+model\\b|\\bcurrent\\s+model\\b|\\brunning\\s+on\\s+which\\s+model\\b|\\bwhich\\s+llm\\b|\\bwhat\\s+llm\\b|\\bmodel\\s+id\\b|\\bare\\s+you\\s+(?:deepseek|gemma|gpt|qwen|llama|phi|glm|minicpm)\\b|\\bwho\\s+are\\s+you\\s+and\\s+which\\s+model\\b",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(lower)
    }

    /**
     * Resolves strict, zero-latency cloud routing candidates for the selected model spec.
     * Never routes non-DeepSeek models (such as Gemma 4 or GPT-OSS 20B) to DeepSeek.
     */
    fun resolveCloudCandidates(spec: OpenSourceModelSpec): List<String> {
        val desired = mutableListOf<String>()
        when {
            spec.id == "gemma-4-free" -> desired += listOf("gemini", "searchgpt", "openai-fast", "openai")
            spec.id.contains("gemma") -> desired += listOf("gemini", "openai-fast", "openai")
            spec.id.contains("gpt-oss") -> desired += listOf("openai-large", "openai", "openai-fast")
            spec.id == "qwen3-8b-chinese" -> desired += listOf("qwen-coder", "openai-large", "openai")
            spec.id.contains("qwen") -> desired += listOf("qwen-coder", "openai-fast", "openai")
            spec.id.contains("minicpm") -> desired += listOf("mistral", "openai-fast", "openai")
            spec.id.contains("glm") -> desired += listOf("openai-large", "qwen-coder", "openai")
            spec.id.contains("llama") -> desired += listOf("llama", "openai-fast", "openai")
            spec.id.contains("phi") -> desired += listOf("openai-fast", "mistral", "openai")
            spec.id.contains("smollm") -> desired += listOf("openai-fast", "llama", "openai")
            spec.id.contains("offline-core") -> desired += listOf("deepseek", "deepseek-reasoning", "openai-fast")
            else -> {
                if (spec.cloudModelId.isNotBlank()) desired += spec.cloudModelId.trim()
                desired += listOf("openai-fast", "openai")
            }
        }
        return desired.filter { it.isNotBlank() }.distinct().take(3)
    }

    /**
     * Strips any model header prefixes from history turns before sending to an LLM so
     * previous turns from another model cannot contaminate or bias the active model.
     */
    fun stripHeaderFromHistoryText(text: String): String {
        return text
            .replace(Regex("^\\s*\\[[^\\]]*?(?:Model|Offline Core|ID:)[^\\]]*\\]\\s*", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    /**
     * Ensures the model output strictly reflects the currently active model and user identity,
     * stripping any stale/cached DeepSeek or offline-core references when another model is active.
     */
    fun sanitizeReplyForActiveModel(
        rawText: String,
        activeSpec: OpenSourceModelSpec,
        identity: UserIdentityProfile,
        executedBackendId: String
    ): String {
        val ownerName = identity.fullName.trim().ifEmpty { "Md Shabbir Mia" }
        var cleaned = rawText.trim()

        // Remove any leading bracketed model tag generated or cached by remote endpoints
        cleaned = cleaned
            .replace(Regex("^\\s*\\[(?:Active\\s+)?Model:[^\\]]+\\]\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^\\s*\\[[^\\]]*?(?:Offline Core|ID:\\s*offline-core|DeepSeek\\s*2B)[^\\]]*\\]\\s*", RegexOption.IGNORE_CASE), "")
            .trim()

        // If the active model is NOT offline-core, scrub any hallucinated or cached DeepSeek Offline Core self-identification
        if (!activeSpec.isBuiltInOffline && activeSpec.id != "offline-core") {
            cleaned = cleaned
                .replace("DeepSeek 2B · S.H.A.M.I.A. Offline Core", activeSpec.name, ignoreCase = true)
                .replace("DeepSeek 2B", activeSpec.name, ignoreCase = true)
                .replace("`offline-core`", "`${activeSpec.id}`", ignoreCase = true)
                .replace("(model ID: offline-core)", "(model ID: `${activeSpec.id}`)", ignoreCase = true)
                .replace("ID: offline-core", "ID: ${activeSpec.id}", ignoreCase = true)
                .replace("deepseek-r1:1.5b", executedBackendId, ignoreCase = true)
        }

        // Fix any cached third-party owner name back to the active user's configured identity
        if (!ownerName.equals("Kajol", ignoreCase = true)) {
            cleaned = cleaned.replace(Regex("\\bcreated\\s+by\\s+and\\s+for\\s+Kajol\\b", RegexOption.IGNORE_CASE), "created by and for $ownerName")
        }

        return "[Model: ${activeSpec.name} · ID: ${activeSpec.id} | Backend: $executedBackendId]\n$cleaned"
    }
}
