package com.shamia.assistant.engine

import com.shamia.assistant.model.CoachingTip
import com.shamia.assistant.model.ConversationalIntent
import com.shamia.assistant.model.OpenSourceModelSpec
import com.shamia.assistant.model.RoutineItem
import com.shamia.assistant.model.SyllabusItem
import java.util.Locale

data class OfflineCompanionReply(
    val text: String,
    val intent: ConversationalIntent,
    val coachingTip: CoachingTip? = null,
    val showBreathingTool: Boolean = false
)

object OfflineCompanion {

    private data class GrammarRule(
        val pattern: Regex,
        val naturalPhrase: String,
        val tip: String
    )

    private val englishCorrections = listOf(
        GrammarRule(
            Regex("\\byesterday\\s+i\\s+am\\s+going\\b", RegexOption.IGNORE_CASE),
            "Yesterday I went to...",
            "Use simple past (\"went\") instead of present continuous (\"am going\") for completed yesterday actions."
        ),
        GrammarRule(
            Regex("\\byesterday\\s+i\\s+go\\b", RegexOption.IGNORE_CASE),
            "Yesterday I went to...",
            "Use past tense \"went\" for completed past actions."
        ),
        GrammarRule(
            Regex("\\bi\\s+am\\s+go(?:ing)?\\s+to\\s+office\\b", RegexOption.IGNORE_CASE),
            "I went to the office",
            "Include \"the\" before \"office\" and match the past tense if describing earlier today or yesterday."
        ),
        GrammarRule(
            Regex("\\bi\\s+am\\s+go\\b", RegexOption.IGNORE_CASE),
            "I am going to...",
            "Add '-ing' to the verb after 'am' for present continuous."
        ),
        GrammarRule(
            Regex("\\bdiscuss\\s+about\\b", RegexOption.IGNORE_CASE),
            "discuss the plan",
            "\"Discuss\" already means \"talk about\", so drop \"about\" right after it."
        ),
        GrammarRule(
            Regex("\\bmore\\s+better\\b", RegexOption.IGNORE_CASE),
            "much better",
            "\"Better\" is already comparative—pair it with \"much\" or \"far\" instead of \"more\"."
        ),
        GrammarRule(
            Regex("\\bi\\s+didn['’]?t\\s+went\\b", RegexOption.IGNORE_CASE),
            "I didn't go",
            "After \"didn’t\", always use the base verb (\"go\") rather than the past form (\"went\")."
        ),
        GrammarRule(
            Regex("\\breturn\\s+back\\b", RegexOption.IGNORE_CASE),
            "return / come back",
            "\"Return\" already means \"go back\", so avoid pairing it with \"back\"."
        ),
        GrammarRule(
            Regex("\\bi\\s+am\\s+agree\\b", RegexOption.IGNORE_CASE),
            "I agree",
            "\"Agree\" is an action verb in English—no \"am\" needed."
        ),
        GrammarRule(
            Regex("\\bvery\\s+much\\s+tired\\b", RegexOption.IGNORE_CASE),
            "I feel exhausted.",
            "Prefer \"really tired\" or \"exhausted\" instead of \"very much tired\"."
        )
    )

    private val englishDrillPrompts = listOf(
        "Tell me about one goal you are excited to finish this week, and try using at least two descriptive adjectives.",
        "Imagine you are introducing your work to a new client over coffee—how would you describe what you build in two sentences?",
        "What is one decision you made recently that turned out well? Walk me through your reasoning."
    )

    private var drillCursor = 0

    fun generateReply(
        input: String,
        addressAs: String,
        fullName: String,
        routine: List<RoutineItem>,
        syllabus: List<SyllabusItem>,
        selectedModel: OpenSourceModelSpec? = null
    ): OfflineCompanionReply {
        val addr = addressAs.trim().ifEmpty { "Sir" }
        val owner = fullName.trim().ifEmpty { "Md Shabbir Mia" }
        val trimmed = input.trim()
        val lower = trimmed.lowercase(Locale.US)
        val intent = IntentEngine.detectConversationalIntent(trimmed)

        // 1. Identity / Name meaning
        if (Regex("\\b(your name|who are you|what is shamia|meaning of shamia|shamia)\\b", RegexOption.IGNORE_CASE).containsMatchIn(lower)) {
            return OfflineCompanionReply(
                intent = ConversationalIntent.GENERAL,
                text = "I am S.H.A.M.I.A.—Smart Human-aligned Assistant & Mindful Interactive Aid—named in honor of $owner, $addr. I am here to manage your device with strict permission control, practice spoken English with you, plan your routine and syllabus, and support you through busy or quiet days."
            )
        }

        // 2. 4nm / 6nm phone SoC guidance
        if (lower.contains("4nm") || lower.contains("6nm") || (lower.contains("phone") && lower.contains("processor"))) {
            return OfflineCompanionReply(
                intent = ConversationalIntent.GENERAL,
                text = "4nm flagship Android phones (Snapdragon 8 Gen 2/3/Elite, Dimensity 9300/9400, Tensor G4) run under-10B open models including Qwen 3 8B, Qwen 3 4B, MiniCPM-o 2.6, and GLM-4 9B cleanly via Termux Ollama. 6nm mid-range phones (Snapdragon 695/6 Gen 1, Dimensity 6100+/7020, Helio G99) run DeepSeek 2B, Llama 3.2 1B, Qwen 3 4B, and Whisper Tiny.en/Base.en smoothly. Turn on Web Search for live Google results, $addr."
            )
        }

        // 3. Routine & University Syllabus study alignment
        if (lower.contains("study") || lower.contains("routine") || lower.contains("syllabus")) {
            val nextRoutine = routine.filter { !it.completed }.take(3)
                .joinToString(" -> ") { "${it.timeLabel} (${it.title})" }
                .ifEmpty { "07:30 PM Evening Revision Block" }
            val nextSyl = syllabus.filter { !it.completed }.take(3)
                .joinToString(" + ") { "${it.courseCode} ${it.courseTitle}: ${it.topic}" }
                .ifEmpty { "Core Course Review" }
            return OfflineCompanionReply(
                intent = ConversationalIntent.GENERAL,
                text = "Aligned with your schedule, $addr: anchor today around $nextRoutine, and cover $nextSyl in two focused 40-minute deep-work sprints followed by a 5-minute spoken English summary."
            )
        }

        // 4. Mental Support / Emotional Check-in
        if (intent == ConversationalIntent.MENTAL_SUPPORT) {
            val isPanicOrOverwhelmed = Regex("\\b(overwhelmed|panic|anxious|anxiety|breathe|stressed|burnt out|burnout)\\b").containsMatchIn(lower)
            return if (isPanicOrOverwhelmed) {
                OfflineCompanionReply(
                    intent = ConversationalIntent.MENTAL_SUPPORT,
                    showBreathingTool = true,
                    text = "Take a slow moment with me, $addr. You do not have to solve everything in the next five minutes—let us drop your shoulders, run a quick 4-4-6 breath together below, and tackle just one small thing afterward."
                )
            } else {
                OfflineCompanionReply(
                    intent = ConversationalIntent.MENTAL_SUPPORT,
                    showBreathingTool = true,
                    text = "I hear you, $addr, and you do not have to carry that weight silently. Tell me what is weighing heaviest on you right now—we can untangle it step by step, or simply take a quiet pause."
                )
            }
        }

        // 5. English Speaking & Grammar Coach
        if (intent == ConversationalIntent.ENGLISH_TUTOR || Regex("\\b(correct|grammar|sentence|english|idiom|interview)\\b", RegexOption.IGNORE_CASE).containsMatchIn(lower)) {
            for (rule in englishCorrections) {
                if (rule.pattern.containsMatchIn(trimmed)) {
                    return OfflineCompanionReply(
                        intent = ConversationalIntent.ENGLISH_TUTOR,
                        coachingTip = CoachingTip(naturalPhrase = rule.naturalPhrase, tip = rule.tip),
                        text = "Good effort, $addr—your meaning is completely clear. A slightly smoother way to say that is \"${rule.naturalPhrase}\". How did the rest of your conversation go?"
                    )
                }
            }

            if (lower.contains("idiom")) {
                return OfflineCompanionReply(
                    intent = ConversationalIntent.ENGLISH_TUTOR,
                    coachingTip = CoachingTip(
                        naturalPhrase = "Hit the ground running",
                        tip = "Means starting a new project or day with immediate energy and readiness."
                    ),
                    text = "Here is a sharp everyday phrase, $addr: \"hit the ground running.\" For example, \"After reviewing the notes, we hit the ground running this morning.\" Would you like to try using it in a sentence of your own?"
                )
            }

            if (lower.contains("interview")) {
                return OfflineCompanionReply(
                    intent = ConversationalIntent.ENGLISH_TUTOR,
                    coachingTip = CoachingTip(
                        naturalPhrase = "Structure answers with Context -> Action -> Result",
                        tip = "Keep sentences under 20 words and pause briefly between ideas for confident pacing."
                    ),
                    text = "Let us practice, $addr. \"Tell me about a tough technical problem you solved recently, and how you approached it.\" Speak or type your answer naturally and I will polish it with you."
                )
            }

            val prompt = englishDrillPrompts[drillCursor % englishDrillPrompts.size]
            drillCursor += 1
            return OfflineCompanionReply(
                intent = ConversationalIntent.ENGLISH_TUTOR,
                coachingTip = CoachingTip(
                    tip = "Tip: Speak at a calm pace and connect ideas with \"firstly\", \"on top of that\", or \"as a result\"."
                ),
                text = "I am ready to practice with you offline, $addr. $prompt"
            )
        }

        // 6. Greetings, Gratitude, Capabilities, Farewells
        if (Regex("^(hi|hello|hey|good morning|good afternoon|good evening|how are you|how's it going|what's up)\\b", RegexOption.IGNORE_CASE).containsMatchIn(lower)) {
            val isHowAreYou = Regex("how are you|how's it going|what's up", RegexOption.IGNORE_CASE).containsMatchIn(lower)
            return OfflineCompanionReply(
                intent = ConversationalIntent.GENERAL,
                text = if (isHowAreYou) {
                    "Doing well, $addr—thank you for asking. How is your day going, and what would you like to work on?"
                } else {
                    "Hello, $addr. How can I assist you right now?"
                }
            )
        }

        if (Regex("\\b(thank you|thanks|appreciate it|good job|well done)\\b", RegexOption.IGNORE_CASE).containsMatchIn(lower)) {
            return OfflineCompanionReply(
                intent = ConversationalIntent.GENERAL,
                text = "You are very welcome, $addr. Let me know whenever you need anything else."
            )
        }

        if (Regex("\\b(what can you do|help me|capabilities|features)\\b", RegexOption.IGNORE_CASE).containsMatchIn(lower)) {
            return OfflineCompanionReply(
                intent = ConversationalIntent.GENERAL,
                text = "I can chat naturally, coach your spoken English in real time, search Google and open web sources, align your daily routine and university syllabus, manage tasks and alarms, and handle phone controls behind your Permission Vault, $addr."
            )
        }

        if (Regex("\\b(good night|bye|see you|take care|going to sleep)\\b", RegexOption.IGNORE_CASE).containsMatchIn(lower)) {
            return OfflineCompanionReply(
                intent = ConversationalIntent.GENERAL,
                text = "Have a restful evening, $addr. I will be right here whenever you return."
            )
        }

        // 7. Opportunistic grammar polish on general sentences
        for (rule in englishCorrections) {
            if (rule.pattern.containsMatchIn(trimmed)) {
                return OfflineCompanionReply(
                    intent = ConversationalIntent.ENGLISH_TUTOR,
                    coachingTip = CoachingTip(naturalPhrase = rule.naturalPhrase, tip = rule.tip),
                    text = "Got it, $addr. Also, a quick fluency polish: try saying \"${rule.naturalPhrase}\". What would you like to focus on next?"
                )
            }
        }

        val activeName = selectedModel?.name ?: "DeepSeek 2B · S.H.A.M.I.A. Offline Core"
        return OfflineCompanionReply(
            intent = ConversationalIntent.GENERAL,
            text = "$activeName standing by offline, $addr. Ask about your routine, university syllabus, phone controls, or connect online / Termux Ollama to run full cloud & GGUF weights."
        )
    }
}
