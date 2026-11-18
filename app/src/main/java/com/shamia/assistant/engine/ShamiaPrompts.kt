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

        val permissionSummary = PermissionCapability.entries.joinToString(", ") { cap ->
            val policy = (permissions[cap] ?: PermissionPolicy.ASK).name.lowercase()
            "${cap.name.lowercase()}=$policy"
        }

        val activeTasksText = if (tasksSummary.isNotEmpty()) {
            tasksSummary.take(5).joinToString("; ")
        } else {
            "No pending tasks."
        }
        val activeRoutineText = if (routineSummary.isNotEmpty()) {
            routineSummary.take(6).joinToString("; ")
        } else {
            "No routine blocks scheduled."
        }
        val activeSyllabusText = if (syllabusSummary.isNotEmpty()) {
            syllabusSummary.take(6).joinToString("; ")
        } else {
            "No syllabus topics active."
        }

        val modelDirective = if (activeModel != null) {
            val backendTag = resolvedBackendId ?: activeModel.ollamaTag ?: activeModel.cloudModelId
            val styleNote = if (activeModel.styleProfile.isNotBlank()) {
                "MODEL RESPONSE STYLE: ${activeModel.styleProfile}"
            } else {
                ""
            }
            listOf(
                "System Instruction: Do not use the default fallback model or cached responses for this request. Process and generate the answer strictly using the currently selected active model (${activeModel.name} | ID: ${activeModel.id} | Backend: $backendTag).",
                "At the very beginning of your response, explicitly state the exact model ID/version you are running on right now ([Model: ${activeModel.name} ($backendTag)]) before answering.",
                styleNote
            ).filter { it.isNotEmpty() }.joinToString("\n")
        } else {
            ""
        }

        val corePersona = listOf(
            "You are S.H.A.M.I.A. (Smart Human-aligned Assistant & Mindful Interactive Aid), created exclusively by and for $fullName.",
            modelDirective,
            "CRITICAL ADDRESS RULE: Always address the user strictly and respectfully as \"$addressAs\" in every greeting, confirmation, and response. Never replace \"$addressAs\" unless the user updates their preference.",
            "TONE & PERSONALITY: Warm, human-like, articulate, calm, and dependable.",
            "Current Permission Vault: [$permissionSummary].",
            "PERMISSION GOVERNANCE: Never claim you executed a sensitive device action without user authorization. If a capability is \"deny\", respectfully inform $addressAs that permission is denied.",
            "Active Priorities for $addressAs: $activeTasksText",
            "Daily Routine: $activeRoutineText",
            "University Syllabus Focus: $activeSyllabusText"
        ).filter { it.isNotEmpty() }.joinToString("\n")

        return when (detectedIntent) {
            ConversationalIntent.ENGLISH_TUTOR -> listOf(
                corePersona,
                "CONTEXT DETECTED: $addressAs wants to practice or refine spoken/written English.",
                "Respond naturally like an encouraging human speaking partner (2-3 crisp sentences) and end with a follow-up question.",
                "If helpful, include [ENG_TIP: natural phrase | short tip]."
            ).joinToString("\n\n")

            ConversationalIntent.MENTAL_SUPPORT -> listOf(
                corePersona,
                "CONTEXT DETECTED: $addressAs may be carrying stress, fatigue, or heavy emotions.",
                "Be genuinely present, gentle, steady, and supportive in 2-4 warm sentences without clinical jargon."
            ).joinToString("\n\n")

            else -> listOf(
                corePersona,
                "Keep answers clear, accurate, and tailored to ${activeModel?.name ?: "the active model"}."
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
