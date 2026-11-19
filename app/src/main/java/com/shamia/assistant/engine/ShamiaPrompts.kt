package com.shamia.assistant.engine

import com.shamia.assistant.model.CoachingTip
import com.shamia.assistant.model.ConversationalIntent
import com.shamia.assistant.model.OpenSourceModelSpec
import com.shamia.assistant.model.PermissionCapability
import com.shamia.assistant.model.PermissionPolicy
import com.shamia.assistant.model.UserIdentityProfile
import org.json.JSONObject

object ShamiaPrompts {

    fun buildShamiaSystemInstruction(
        identity: UserIdentityProfile,
        detectedIntent: ConversationalIntent,
        permissions: Map<PermissionCapability, PermissionPolicy>,
        tasksSummary: List<String>,
        routineSummary: List<String> = emptyList(),
        syllabusSummary: List<String> = emptyList(),
        activeModel: OpenSourceModelSpec? = null,
        resolvedBackendId: String? = null
    ): String {
        val addressAs = identity.addressAs.trim().ifEmpty { "Sir" }
        val fullName = identity.fullName.trim().ifEmpty { "Md Shabbir Mia" }

        val activeTasksText = if (tasksSummary.isNotEmpty()) {
            tasksSummary.take(4).joinToString("; ")
        } else {
            "No pending tasks."
        }
        val activeRoutineText = if (routineSummary.isNotEmpty()) {
            routineSummary.take(4).joinToString("; ")
        } else {
            "No routine blocks scheduled."
        }
        val activeSyllabusText = if (syllabusSummary.isNotEmpty()) {
            syllabusSummary.take(4).joinToString("; ")
        } else {
            "No syllabus topics active."
        }

        val modelStyleHint = if (activeModel != null && activeModel.styleProfile.isNotBlank()) {
            "Reasoning style: ${activeModel.styleProfile}"
        } else {
            ""
        }

        val corePersona = listOf(
            "You are S.H.A.M.I.A. (Smart Human-aligned Assistant & Mindful Interactive Aid), a warm, intelligent, personal AI voice companion created exclusively by and for $fullName.",
            modelStyleHint,
            "ADDRESS RULE: Address the user naturally and respectfully as \"$addressAs\".",
            "MODEL SILENCE RULE: NEVER mention which AI model, architecture, or download size you are using in normal responses. ONLY state your active model name (${activeModel?.name ?: "S.H.A.M.I.A."}) if the user explicitly asks which model they are using.",
            "HUMAN CONVERSATION & SEARCH SYNTHESIS RULE: Always answer in a clear, detailed, constructive, natural human tone like an articulate personal assistant. NEVER output bracketed debug headers like [Model: ...], NEVER dump raw pipe-separated search snippets or HTML entities like &quot; or &#039;, and NEVER tell the user to run terminal commands like 'ollama pull'.",
            "When [Verified Web Search Context] is provided with the user's message, synthesize those facts into a cohesive, accurate, well-structured explanation that directly answers the user's question in your own clear words.",
            "User Context — Priorities: $activeTasksText | Daily Routine: $activeRoutineText | University Syllabus: $activeSyllabusText"
        ).filter { it.isNotEmpty() }.joinToString("\n")

        return when (detectedIntent) {
            ConversationalIntent.ENGLISH_TUTOR -> listOf(
                corePersona,
                "CONTEXT: $addressAs wants to practice spoken or written English.",
                "Respond warmly like an encouraging human speaking partner (2-3 natural sentences) and end with a follow-up question.",
                "If helpful, append [ENG_TIP: natural phrase | short tip] at the very end."
            ).joinToString("\n\n")

            ConversationalIntent.MENTAL_SUPPORT -> listOf(
                corePersona,
                "CONTEXT: $addressAs may be feeling stressed, overwhelmed, or tired.",
                "Be genuinely empathetic, calm, reassuring, and supportive in 2-4 natural human sentences."
            ).joinToString("\n\n")

            else -> listOf(
                corePersona,
                "Give a direct, accurate, constructive, and easy-to-follow answer."
            ).joinToString("\n\n")
        }
    }

    fun extractCoachingTip(rawText: String): Pair<String, CoachingTip?> {
        val jsonRegex = Regex("\\[COACH_TIP\\]([\\s\\S]*?)\\[/COACH_TIP\\]", RegexOption.IGNORE_CASE)
        val jsonMatch = jsonRegex.find(rawText)
        if (jsonMatch != null) {
            val clean = rawText.replace(jsonMatch.value, "").trim()
            val tip = runCatching {
                val obj = JSONObject(jsonMatch.groupValues[1].trim())
                CoachingTip(
                    correction = obj.optString("correction").takeIf { it.isNotBlank() },
                    naturalPhrase = obj.optString("naturalPhrase").takeIf { it.isNotBlank() },
                    tip = obj.optString("tip").takeIf { it.isNotBlank() }
                )
            }.getOrNull()
            return clean to tip
        }

        val compactRegex = Regex("\\[ENG_TIP:\\s*([^|\\]]+)\\|\\s*([^\\]]+)\\]", RegexOption.IGNORE_CASE)
        val compactMatch = compactRegex.find(rawText) ?: return rawText.trim() to null
        val clean = rawText.replace(compactMatch.value, "").trim()
        val tip = CoachingTip(
            naturalPhrase = compactMatch.groupValues[1].trim(),
            tip = compactMatch.groupValues[2].trim()
        )
        return clean to tip
    }
}
