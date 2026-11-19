package com.shamia.assistant.engine

import com.shamia.assistant.model.CoachingTip
import com.shamia.assistant.model.ConversationalIntent
import com.shamia.assistant.model.HudMessage
import com.shamia.assistant.model.OpenSourceModelSpec
import com.shamia.assistant.model.RoutineItem
import com.shamia.assistant.model.SyllabusItem
import java.util.Locale
import kotlin.math.pow
import kotlin.math.sqrt

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
            "Use simple past (\"went\") instead of present continuous (\"am going\") for completed actions yesterday."
        ),
        GrammarRule(
            Regex("\\byesterday\\s+i\\s+go\\b", RegexOption.IGNORE_CASE),
            "Yesterday I went to...",
            "Use past tense \"went\" for completed past actions."
        ),
        GrammarRule(
            Regex("\\bi\\s+am\\s+go(?:ing)?\\s+to\\s+office\\b", RegexOption.IGNORE_CASE),
            "I went to the office / I am heading to the office",
            "Include \"the\" before \"office\" and match the tense to the time of day."
        ),
        GrammarRule(
            Regex("\\bi\\s+am\\s+go\\b", RegexOption.IGNORE_CASE),
            "I am going to...",
            "Add '-ing' to the verb after 'am' for present continuous."
        ),
        GrammarRule(
            Regex("\\bdiscuss\\s+about\\b", RegexOption.IGNORE_CASE),
            "discuss the topic",
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
            "After \"didn't\", always use the base verb (\"go\") rather than the past form (\"went\")."
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
            "I feel exhausted / I'm really tired.",
            "Prefer \"really tired\" or \"exhausted\" instead of \"very much tired\"."
        )
    )

    private val englishDrillPrompts = listOf(
        "Tell me about one goal you are excited to finish this week, and try using at least two descriptive adjectives.",
        "Imagine you are introducing your favorite project over coffee—how would you describe what it does in two clear sentences?",
        "What is one decision you made recently that turned out well? Walk me through your thinking."
    )

    private var drillCursor = 0

    fun generateReply(
        input: String,
        addressAs: String,
        fullName: String,
        routine: List<RoutineItem>,
        syllabus: List<SyllabusItem>,
        selectedModel: OpenSourceModelSpec? = null,
        webContext: String = "",
        history: List<HudMessage> = emptyList()
    ): OfflineCompanionReply {
        val addr = addressAs.trim().ifEmpty { "Sir" }
        val owner = fullName.trim().ifEmpty { "Md Shabbir Mia" }
        val trimmed = WebSearchClient.decodeHtmlEntities(input.trim())
        val lower = trimmed.lowercase(Locale.US)
        val intent = IntentEngine.detectConversationalIntent(trimmed)
        val modelName = selectedModel?.name ?: "S.H.A.M.I.A. Offline Core"

        // 1. Mental Support / Emotional Check-in
        if (intent == ConversationalIntent.MENTAL_SUPPORT) {
            val isPanicOrOverwhelmed =
                Regex("\\b(overwhelmed|panic|anxious|anxiety|breathe|stressed|burnt out|burnout)\\b").containsMatchIn(lower)
            return if (isPanicOrOverwhelmed) {
                OfflineCompanionReply(
                    intent = ConversationalIntent.MENTAL_SUPPORT,
                    showBreathingTool = true,
                    text = "Take a slow breath with me, $addr. You don't have to solve everything at once. Drop your shoulders, follow the 4-4-6 breathing guide below for a few cycles, and then we can tackle just one small step together."
                )
            } else {
                OfflineCompanionReply(
                    intent = ConversationalIntent.MENTAL_SUPPORT,
                    showBreathingTool = true,
                    text = "I'm right here with you, $addr. Tell me what's weighing heaviest on your mind right now—we can break it down into manageable pieces, or just take a quiet pause together."
                )
            }
        }

        // 2. Spoken English & Grammar Coach
        if (intent == ConversationalIntent.ENGLISH_TUTOR ||
            Regex("\\b(correct my|practice english|speak english|english tutor|english coach|grammar|idiom|interview practice)\\b", RegexOption.IGNORE_CASE).containsMatchIn(lower)
        ) {
            for (rule in englishCorrections) {
                if (rule.pattern.containsMatchIn(trimmed)) {
                    return OfflineCompanionReply(
                        intent = ConversationalIntent.ENGLISH_TUTOR,
                        coachingTip = CoachingTip(naturalPhrase = rule.naturalPhrase, tip = rule.tip),
                        text = "Your meaning comes across clearly, $addr. To make it sound even more natural to native speakers, try saying \"${rule.naturalPhrase}\". What else happened after that?"
                    )
                }
            }

            if (lower.contains("idiom")) {
                return OfflineCompanionReply(
                    intent = ConversationalIntent.ENGLISH_TUTOR,
                    coachingTip = CoachingTip(
                        naturalPhrase = "Hit the ground running",
                        tip = "Means starting a project or task immediately with full energy and focus."
                    ),
                    text = "Here is a great everyday idiom, $addr: \"hit the ground running.\" For example: \"Once we finalized the design, our team hit the ground running.\" Try using it in a sentence of your own!"
                )
            }

            if (lower.contains("interview")) {
                return OfflineCompanionReply(
                    intent = ConversationalIntent.ENGLISH_TUTOR,
                    coachingTip = CoachingTip(
                        naturalPhrase = "Use the STAR method: Situation -> Task -> Action -> Result",
                        tip = "Keep sentences under 20 words and end with the measurable impact you delivered."
                    ),
                    text = "Let's run a realistic interview drill, $addr: \"Can you tell me about a challenging problem you solved recently and how you approached it?\" Type or speak your answer and I'll give you constructive feedback."
                )
            }

            val drill = englishDrillPrompts[drillCursor % englishDrillPrompts.size]
            drillCursor += 1
            return OfflineCompanionReply(
                intent = ConversationalIntent.ENGLISH_TUTOR,
                coachingTip = CoachingTip(
                    naturalPhrase = "Connect ideas smoothly with \"First of all\", \"In addition\", and \"As a result\"",
                    tip = "Speaking at a steady, unhurried pace makes your pronunciation sound much clearer."
                ),
                text = "I'd love to practice spoken English with you, $addr. $drill"
            )
        }

        // 3. Opportunistic grammar polish on general sentences if a known grammar rule triggers
        for (rule in englishCorrections) {
            if (rule.pattern.containsMatchIn(trimmed)) {
                return OfflineCompanionReply(
                    intent = ConversationalIntent.ENGLISH_TUTOR,
                    coachingTip = CoachingTip(naturalPhrase = rule.naturalPhrase, tip = rule.tip),
                    text = "Got it, $addr. By the way, a quick natural English polish for that phrasing is \"${rule.naturalPhrase}\". How would you like to proceed?"
                )
            }
        }

        // 4. Identity / Who are you / Which model
        if (CloudModelResolver.isModelIntrospectionQuery(trimmed)) {
            return OfflineCompanionReply(
                intent = ConversationalIntent.GENERAL,
                text = "I am currently running on **$modelName** (${selectedModel?.params ?: "Local Engine"} · ${selectedModel?.sizeLabel ?: "On-Device"}), $addr, operating locally on your phone hardware for $owner."
            )
        }

        if (Regex("^(who are you|what is your name|what is shamia|meaning of shamia|introduce yourself)\\b", RegexOption.IGNORE_CASE).containsMatchIn(lower)) {
            return OfflineCompanionReply(
                intent = ConversationalIntent.GENERAL,
                text = "I am S.H.A.M.I.A. (Smart Human-aligned Assistant & Mindful Interactive Aid), your personal offline-first AI voice companion created by and for $owner, $addr. I can answer questions, help with coding and writing, practice spoken English, plan your study syllabus, and manage phone controls safely through your Permission Vault."
            )
        }

        // 5. Greetings, Gratitude, Farewells
        if (Regex("^(hi|hello|hey|good morning|good afternoon|good evening|assalamu alaikum|salam|how are you|how's it going|what's up)[!?. ]*$", RegexOption.IGNORE_CASE).containsMatchIn(lower)) {
            val isHowAreYou = Regex("how are you|how's it going|what's up", RegexOption.IGNORE_CASE).containsMatchIn(lower)
            return OfflineCompanionReply(
                intent = ConversationalIntent.GENERAL,
                text = if (isHowAreYou) {
                    "I'm doing great, $addr! How is your day going, and what can I help you with today?"
                } else {
                    "Hello, $addr! Good to hear from you. How can I help you today?"
                }
            )
        }

        if (Regex("^(thank you|thanks|thanks a lot|appreciate it|good job|well done)[!?. ]*$", RegexOption.IGNORE_CASE).containsMatchIn(lower)) {
            return OfflineCompanionReply(
                intent = ConversationalIntent.GENERAL,
                text = "You're very welcome, $addr! Let me know anytime you want to tackle another question or task."
            )
        }

        if (Regex("^(good night|bye|goodbye|see you|take care)[!?. ]*$", RegexOption.IGNORE_CASE).containsMatchIn(lower)) {
            return OfflineCompanionReply(
                intent = ConversationalIntent.GENERAL,
                text = "Take care and have a wonderful rest, $addr. I'll be right here whenever you need me."
            )
        }

        // 6. On-Device Math & Expression Evaluator
        tryEvaluateMath(trimmed)?.let { mathAnswer ->
            return OfflineCompanionReply(
                intent = ConversationalIntent.GENERAL,
                text = "$mathAnswer, $addr."
            )
        }

        // 7. Mondegreens / Star-Spangled Banner / Misheard Lyrics analysis
        if (lower.contains("mondegreen") || lower.contains("star-spangled banner") || lower.contains("donzerly light")) {
            return OfflineCompanionReply(
                intent = ConversationalIntent.GENERAL,
                text = "A **mondegreen** is a mishearing or misinterpretation of a phrase in a song or poem that gives it a new meaning, $addr.\n\n" +
                    "A classic example occurs in the opening line of Francis Scott Key's *\"The Star-Spangled Banner\"*:\n" +
                    "• **Original lyric:** *\"O say can you see, by the dawn's early light\"*\n" +
                    "• **Common mishearings:** Children often hear *\"O say\"* as **\"José, can you see?\"** and blend *\"dawn's early light\"* into a single invented word, **\"by the Donzerly light.\"**\n\n" +
                    "This happens because our brains naturally group unfamiliar spoken syllables into familiar-sounding names or words when listening to music."
            )
        }

        // 8. Stack / Stack Overflow / Tech Stack / Data Structure queries
        if (Regex("\\b(stack\\s*overflow|stackflow|data\\s+structure\\s+stack|what\\s+is\\s+a\\s+stack|tech\\s+stack|call\\s+stack)\\b", RegexOption.IGNORE_CASE).containsMatchIn(lower) ||
            lower == "stack" || lower == "stackflow"
        ) {
            return OfflineCompanionReply(
                intent = ConversationalIntent.GENERAL,
                text = "Here is a clear breakdown of **Stack** in computing, $addr:\n\n" +
                    "1. **Stack Data Structure (LIFO):** A Last-In, First-Out collection with two O(1) core operations: `push(item)` (adds to the top) and `pop()` (removes from the top). It powers function call stacks, undo/redo history, and expression parsing.\n" +
                    "2. **Tech Stack:** The combination of programming languages, frameworks, and databases used to build an app (for example, Kotlin + Jetpack Compose + Material 3 on Android).\n" +
                    "3. **Stack Overflow:** Both a condition where recursion exceeds call-stack memory, and the famous Q&A community for software developers."
            )
        }

        // 9. Routine & University Syllabus Study Plan
        if (lower.contains("study") || lower.contains("routine") || lower.contains("syllabus") || lower.contains("exam")) {
            val nextRoutine = routine.filter { !it.completed }.take(3)
                .joinToString("\n• ", prefix = "• ") { "${it.timeLabel}: ${it.title} (${it.category.label})" }
                .ifEmpty { "• 07:30 PM: Focused Revision & Practice Block" }
            val nextSyl = syllabus.filter { !it.completed }.take(3)
                .joinToString("\n• ", prefix = "• ") { "${it.courseCode} ${it.courseTitle} — ${it.topic}${it.examNote?.let { n -> " [$n]" }.orEmpty()}" }
                .ifEmpty { "• Core Subject Concept Review & Active Recall" }
            return OfflineCompanionReply(
                intent = ConversationalIntent.GENERAL,
                text = "Here is a practical study plan aligned with your schedule, $addr:\n\n" +
                    "**Upcoming Routine Blocks:**\n$nextRoutine\n\n" +
                    "**Priority Syllabus Topics:**\n$nextSyl\n\n" +
                    "**Recommended Strategy:** Run two 45-minute deep-focus sprints with notifications muted, write a 3-bullet summary from memory after each topic, and finish with a 5-minute spoken English recap."
            )
        }

        // 10. 4nm / 6nm Smartphone SoC & Local Hardware Model Guide
        if (lower.contains("4nm") || lower.contains("6nm") || (lower.contains("phone") && (lower.contains("processor") || lower.contains("chipset") || lower.contains("compare")))) {
            return OfflineCompanionReply(
                intent = ConversationalIntent.GENERAL,
                text = "Here is how 4nm and 6nm mobile processors compare for running local offline AI models, $addr:\n\n" +
                    "• **4nm Flagship SoCs (Snapdragon 8 Gen 2/3/Elite, Dimensity 9300/9400, Tensor G4):** Higher memory bandwidth (LPDDR5X) and strong NPU/CPU throughput allow 3B–8B quantized models (**Gemma 3/4**, **Llama 3.2 3B**, **Qwen 3 4B & 8B**, **Phi-3.5 Mini**) to run smoothly.\n" +
                    "• **6nm Mid-Range SoCs (Snapdragon 695/6 Gen 1, Dimensity 6100+/7020, Helio G99):** Best paired with 0.3B–2B Q4 models (**Llama 3.2 1B**, **Qwen 2.5 1.5B**, **SmolLM2 1.7B**, **Qwen 2.5 0.5B**) to stay cool and preserve battery life."
            )
        }

        // 11. Coding, Programming & Algorithms Assistance
        if (Regex("\\b(write a program|write code|kotlin|python|javascript|typescript|react|sql|algorithm|binary search|dynamic programming|sorting|api|function|class|bug|debug)\\b", RegexOption.IGNORE_CASE).containsMatchIn(lower)) {
            return OfflineCompanionReply(
                intent = ConversationalIntent.GENERAL,
                text = buildCodingResponse(trimmed, lower, addr)
            )
        }

        // 12. Writing Emails, Letters, Summaries, or Essays
        if (Regex("\\b(write an? email|draft an? email|cover letter|summarize|summary of|write a message|write a paragraph|essay on)\\b", RegexOption.IGNORE_CASE).containsMatchIn(lower)) {
            return OfflineCompanionReply(
                intent = ConversationalIntent.GENERAL,
                text = buildWritingResponse(trimmed, addr, owner)
            )
        }

        // 13. Curated built-in offline knowledge (always preferred over noisy raw search fragments)
        lookupBuiltInKnowledge(lower, addr)?.let { builtIn ->
            return OfflineCompanionReply(
                intent = ConversationalIntent.GENERAL,
                text = builtIn
            )
        }

        // 14. Constructive synthesis when clean encyclopedic web context is available
        if (webContext.isNotBlank()) {
            val synthesized = WebSearchClient.synthesizeConstructiveAnswer(trimmed, webContext, addr)
            if (synthesized.isNotBlank()) {
                return OfflineCompanionReply(
                    intent = ConversationalIntent.GENERAL,
                    text = synthesized
                )
            }
        }

        // 14. Follow-up conversational context awareness ("explain more", "give an example", "shorter")
        val lastAssistantTurn = history.lastOrNull { !it.isUser }?.text.orEmpty()
        if (lastAssistantTurn.isNotBlank() &&
            Regex("^(explain more|tell me more|more details|give (?:me )?an example|for example|make it shorter|simplify|in simple words|why)\\b", RegexOption.IGNORE_CASE).containsMatchIn(lower)
        ) {
            return OfflineCompanionReply(
                intent = ConversationalIntent.GENERAL,
                text = "Building on what we just discussed, $addr: the core takeaway is to focus on the underlying principle first and apply it step by step in practice. For example, breaking the concept into a clear input, transformation, and verifiable outcome makes it much easier to remember and apply."
            )
        }

        // 15. General Constructive On-Device Reasoning for any user topic or question
        return OfflineCompanionReply(
            intent = ConversationalIntent.GENERAL,
            text = buildConstructiveGeneralAnswer(trimmed, addr, modelName, selectedModel)
        )
    }

    private fun tryEvaluateMath(input: String): String? {
        val cleaned = input
            .replace(Regex("^(?:what is|calculate|compute|solve|evaluate)\\s+", RegexOption.IGNORE_CASE), "")
            .replace("×", "*")
            .replace("÷", "/")
            .replace("?", "")
            .trim()

        // Percentage pattern: "15% of 240"
        Regex("^(\\d+(?:\\.\\d+)?)\\s*%\\s*of\\s*(\\d+(?:\\.\\d+)?)$", RegexOption.IGNORE_CASE).find(cleaned)?.let { m ->
            val pct = m.groupValues[1].toDoubleOrNull() ?: return null
            val base = m.groupValues[2].toDoubleOrNull() ?: return null
            val res = (pct / 100.0) * base
            return "$pct% of ${formatNumber(base)} is **${formatNumber(res)}**"
        }

        // Square root pattern: "sqrt(144)" or "square root of 144"
        Regex("^(?:sqrt|square\\s+root\\s+of)\\s*\\(?\\s*(\\d+(?:\\.\\d+)?)\\s*\\)?$", RegexOption.IGNORE_CASE).find(cleaned)?.let { m ->
            val v = m.groupValues[1].toDoubleOrNull() ?: return null
            if (v >= 0) return "The square root of ${formatNumber(v)} is **${formatNumber(sqrt(v))}**"
        }

        // Binary arithmetic expression: e.g. "25 * 16 + 40" or "1024 / 16"
        if (!Regex("^[\\d\\s+\\-*/().^]+$").matches(cleaned) || !cleaned.any { it in "+-*/^" } || !cleaned.any { it.isDigit() }) {
            return null
        }
        val value = runCatching { evalSimpleExpr(cleaned) }.getOrNull() ?: return null
        if (value.isNaN() || value.isInfinite()) return null
        return "The result of `$cleaned` is **${formatNumber(value)}**"
    }

    private fun evalSimpleExpr(expr: String): Double {
        val s = expr.replace(" ", "")
        return object {
            var pos = -1
            var ch = 0

            fun nextChar() {
                pos++
                ch = if (pos < s.length) s[pos].code else -1
            }

            fun eat(charToEat: Int): Boolean {
                while (ch == ' '.code) nextChar()
                if (ch == charToEat) {
                    nextChar()
                    return true
                }
                return false
            }

            fun parse(): Double {
                nextChar()
                val x = parseExpression()
                if (pos < s.length) throw IllegalArgumentException("Trailing chars")
                return x
            }

            fun parseExpression(): Double {
                var x = parseTerm()
                while (true) {
                    when {
                        eat('+'.code) -> x += parseTerm()
                        eat('-'.code) -> x -= parseTerm()
                        else -> return x
                    }
                }
            }

            fun parseTerm(): Double {
                var x = parseFactor()
                while (true) {
                    when {
                        eat('*'.code) -> x *= parseFactor()
                        eat('/'.code) -> x /= parseFactor()
                        else -> return x
                    }
                }
            }

            fun parseFactor(): Double {
                if (eat('+'.code)) return parseFactor()
                if (eat('-'.code)) return -parseFactor()
                var x: Double
                val startPos = pos
                if (eat('('.code)) {
                    x = parseExpression()
                    eat(')'.code)
                } else if (ch in '0'.code..'9'.code || ch == '.'.code) {
                    while (ch in '0'.code..'9'.code || ch == '.'.code) nextChar()
                    x = s.substring(startPos, pos).toDouble()
                } else {
                    throw IllegalArgumentException("Unexpected char")
                }
                if (eat('^'.code)) x = x.pow(parseFactor())
                return x
            }
        }.parse()
    }

    private fun formatNumber(d: Double): String {
        val asLong = d.toLong()
        return if (d == asLong.toDouble()) asLong.toString() else "%.4f".format(Locale.US, d).trimEnd('0').trimEnd('.')
    }

    private fun buildCodingResponse(input: String, lower: String, addr: String): String {
        return when {
            lower.contains("binary search") ->
                "Here is a clean, O(log n) **Binary Search** implementation, $addr:\n\n" +
                    "```kotlin\n" +
                    "fun binarySearch(arr: IntArray, target: Int): Int {\n" +
                    "    var low = 0\n" +
                    "    var high = arr.lastIndex\n" +
                    "    while (low <= high) {\n" +
                    "        val mid = low + (high - low) / 2\n" +
                    "        when {\n" +
                    "            arr[mid] == target -> return mid\n" +
                    "            arr[mid] < target -> low = mid + 1\n" +
                    "            else -> high = mid - 1\n" +
                    "        }\n" +
                    "    }\n" +
                    "    return -1\n" +
                    "}\n" +
                    "```\n" +
                    "• **Time Complexity:** `O(log n)` because each step cuts the remaining search range in half.\n" +
                    "• **Space Complexity:** `O(1)` iterative."

            lower.contains("fibonacci") ->
                "Here is an optimal `O(n)` time, `O(1)` space **Fibonacci** implementation, $addr:\n\n" +
                    "```kotlin\n" +
                    "fun fibonacci(n: Int): Long {\n" +
                    "    if (n <= 1) return n.toLong()\n" +
                    "    var prev = 0L\n" +
                    "    var curr = 1L\n" +
                    "    for (i in 2..n) {\n" +
                    "        val next = prev + curr\n" +
                    "        prev = curr\n" +
                    "        curr = next\n" +
                    "    }\n" +
                    "    return curr\n" +
                    "}\n" +
                    "```"

            lower.contains("prime") ->
                "Here is an efficient `O(√n)` **Prime Number Check**, $addr:\n\n" +
                    "```kotlin\n" +
                    "fun isPrime(n: Int): Boolean {\n" +
                    "    if (n < 2) return false\n" +
                    "    if (n == 2 || n == 3) return true\n" +
                    "    if (n % 2 == 0 || n % 3 == 0) return false\n" +
                    "    var i = 5\n" +
                    "    while (i * i <= n) {\n" +
                    "        if (n % i == 0 || n % (i + 2) == 0) return false\n" +
                    "        i += 6\n" +
                    "    }\n" +
                    "    return true\n" +
                    "}\n" +
                    "```"

            lower.contains("sort") || lower.contains("quicksort") || lower.contains("mergesort") ->
                "Here is a clean **QuickSort** implementation (`O(n log n)` average time), $addr:\n\n" +
                    "```kotlin\n" +
                    "fun quickSort(list: List<Int>): List<Int> {\n" +
                    "    if (list.size <= 1) return list\n" +
                    "    val pivot = list[list.size / 2]\n" +
                    "    val less = list.filter { it < pivot }\n" +
                    "    val equal = list.filter { it == pivot }\n" +
                    "    val greater = list.filter { it > pivot }\n" +
                    "    return quickSort(less) + equal + quickSort(greater)\n" +
                    "}\n" +
                    "```"

            lower.contains("dynamic programming") ->
                "**Dynamic Programming (DP)** solves optimization and counting problems by caching results of overlapping subproblems, $addr:\n\n" +
                    "1. **Top-Down (Memoization):** Write the natural recurrence recursively and store solved states in a hash map or array.\n" +
                    "2. **Bottom-Up (Tabulation):** Fill a DP table iteratively from base cases upward (often reducing memory from `O(n)` to `O(1)`)."

            else ->
                "Here is a clean engineering checklist for **\"${input.take(70)}\"**, $addr:\n\n" +
                    "1. **Define Exact Inputs & Edge Cases:** Guard against null, empty collections, and boundary values up front.\n" +
                    "2. **Separate Logic from UI / I/O:** Keep core algorithms in pure functions so they are fast to debug and unit test.\n" +
                    "3. **Watch Complexity:** Use `HashMap`/`HashSet` for `O(1)` lookups instead of nested `O(n²)` scans.\n\n" +
                    "Specify the exact function, language (Kotlin, Python, JavaScript, C++, or SQL), and expected input/output and I will write it out directly."
        }
    }

    private fun buildWritingResponse(input: String, addr: String, owner: String): String {
        val topic = input
            .replace(Regex("^(?:please\\s+)?(?:write|draft|create|make)\\s+(?:an?\\s+)?(?:email|message|cover letter|paragraph|essay|summary)\\s*(?:about|on|for|to)?\\s*", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifEmpty { "your project update" }

        return "Here is a clean, professional draft for **$topic**, $addr:\n\n" +
            "**Subject:** Update & Next Steps — ${topic.replaceFirstChar { it.uppercase() }}\n\n" +
            "Hello,\n\n" +
            "I hope you are doing well. I am writing to share a quick update regarding $topic. Everything is progressing on schedule, and the key milestones have been organized for review.\n\n" +
            "Please let me know if you have any questions or would like to discuss the next steps.\n\n" +
            "Best regards,\n" +
            "$owner"
    }

    internal fun isLikelyKeyboardSmashOrTypo(text: String): Boolean {
        val clean = text.trim()
        if (clean.isEmpty()) return true
        // Repeated same character 3+ times in a row (e.g. "rerdddd", "aaaaa", "asdfgh")
        if (Regex("(.)\\1{2,}", RegexOption.IGNORE_CASE).containsMatchIn(clean) && !clean.contains(" ")) {
            return true
        }
        val lettersOnly = clean.filter { it.isLetter() }.lowercase(Locale.US)
        if (lettersOnly.length in 3..14 && !clean.contains(" ")) {
            val vowels = lettersOnly.count { it in "aeiouy" }
            if (vowels == 0) return true
            if (Regex("^(asdf|qwer|zxcv|hjkl|rerdd|dfgh|cvbn|tyui)", RegexOption.IGNORE_CASE).containsMatchIn(lettersOnly)) {
                return true
            }
        }
        return false
    }

    private fun buildConstructiveGeneralAnswer(
        input: String,
        addr: String,
        modelName: String,
        selectedModel: OpenSourceModelSpec? = null
    ): String {
        val clean = input.trim()
        val lower = clean.lowercase(Locale.US)
        val words = clean.split(Regex("\\s+")).filter { it.isNotBlank() }

        // 1. Direct encyclopedic & technical knowledge base for offline questions
        lookupBuiltInKnowledge(lower, addr)?.let { return it }

        // 2. Detect random test typing / keyboard smash (like "rerdddd" or "asdf")
        if (isLikelyKeyboardSmashOrTypo(clean)) {
            return "It looks like \"$clean\" might have been a quick test or typo, $addr. Tell me what question, coding task, or topic you'd like help with!"
        }

        // 3. If the user pasted a multi-sentence paragraph to summarize
        if (words.size >= 16 && !clean.endsWith("?")) {
            val sentences = clean.split(Regex("(?<=[.!?])\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
            val firstSentence = sentences.firstOrNull().orEmpty()
            val secondPoint = sentences.getOrNull(1)
            return buildString {
                append("Here is a concise summary of the passage you shared, $addr:\n\n")
                append("• **Main Point:** $firstSentence")
                if (!secondPoint.isNullOrBlank()) {
                    append("\n• **Supporting Detail:** $secondPoint")
                }
                append("\n\nWould you like me to simplify the wording or pull out key study notes from this?")
            }
        }

        // 4. Direct, constructive answer focused strictly on the user's input (zero model/status mentions)
        val cleanTopic = clean
            .replace(Regex("^(?:what\\s+is|what\\s+are|explain|tell\\s+me\\s+about|how\\s+does|how\\s+to|define)\\s+", RegexOption.IGNORE_CASE), "")
            .trimEnd('?', '.', '!')
            .trim()
            .ifEmpty { clean }

        return when {
            lower.startsWith("how to ") || lower.startsWith("how do i ") || lower.startsWith("how can i ") ->
                "Here is a practical step-by-step approach for **$cleanTopic**, $addr:\n\n" +
                    "1. **Clarify the Goal:** Define the exact result you want before starting so you avoid wasted steps.\n" +
                    "2. **Break It Down:** Split **$cleanTopic** into 2–3 small, testable actions and complete the simplest piece first.\n" +
                    "3. **Verify & Refine:** Check the outcome against your goal and adjust one variable at a time.\n\n" +
                    "Share a bit more detail about your exact setup or constraint and I'll tailor the steps directly."

            lower.contains(" vs ") || lower.contains(" versus ") || lower.contains("difference between") ->
                "When comparing **$cleanTopic**, $addr, evaluate these three key dimensions:\n\n" +
                    "• **Core Purpose:** What primary problem each option was designed to solve best.\n" +
                    "• **Trade-Offs:** Speed and simplicity versus flexibility, control, and resource cost.\n" +
                    "• **Best Fit:** Choose the option that matches your actual workload and constraints rather than theoretical benchmarks.\n\n" +
                    "Tell me your specific use case and I'll recommend the clearer choice."

            else ->
                "Here is a focused way to approach **$cleanTopic**, $addr:\n\n" +
                    "• **Key Idea:** Start with the core definition and purpose of **$cleanTopic** before diving into edge details.\n" +
                    "• **Practical Application:** Connect it to a concrete example so you can test and verify how it behaves in practice.\n\n" +
                    "Let me know what specific angle—examples, steps, code, or a shorter summary—you'd like me to focus on next."
        }
    }

    private fun lookupBuiltInKnowledge(lower: String, addr: String): String? {
        return when {
            lower.contains("quantum computing") || lower.contains("qubit") ->
                "**Quantum Computing** harnesses quantum-mechanical phenomena—**superposition**, **entanglement**, and **quantum interference**—to perform computations, $addr:\n\n" +
                    "• **Classical Bit vs. Qubit:** A classical bit is strictly `0` or `1`, whereas a **qubit** can exist in a coherent superposition of both `0` and `1` simultaneously until measured.\n" +
                    "• **Entanglement & Speedup:** Entangled qubits correlate their states instantaneously, allowing algorithms like **Shor's** (prime factorization) and **Grover's** (unstructured search) to solve problems exponentially or quadratically faster than classical computers."

            lower.contains("machine learning") || lower.contains("deep learning") || lower.contains("neural network") || lower.contains("artificial intelligence") || lower.contains("llm") || lower.contains("transformer") ->
                "Here is a detailed breakdown of **Artificial Intelligence, Deep Learning, and Large Language Models (LLMs)**, $addr:\n\n" +
                    "1. **Machine Learning (ML):** Systems learn statistical patterns directly from data rather than explicit if/else rules, divided into **Supervised Learning**, **Unsupervised Learning**, and **Reinforcement Learning**.\n" +
                    "2. **Deep Neural Networks & Transformers:** Modern language models use the **Transformer** architecture with **Multi-Head Self-Attention**, enabling the model to weigh how every word in a context window relates to every other word.\n" +
                    "3. **On-Device Quantization:** By compressing 16-bit floating-point weights into 4-bit integers (`Q4_K_M`), multi-billion-parameter models can run directly inside smartphone RAM."

            lower.contains("photosynthesis") ->
                "**Photosynthesis** is the biological process by which green plants, algae, and cyanobacteria convert light energy into chemical energy stored in glucose, $addr:\n\n" +
                    "• **Chemical Equation:** `6 CO₂ + 6 H₂O + Light Energy → C₆H₁₂O₆ (Glucose) + 6 O₂`\n" +
                    "• **Light-Dependent Reactions:** Occur in the thylakoid membranes, where chlorophyll absorbs photons to split water molecules, release oxygen (`O₂`), and generate **ATP** and **NADPH**.\n" +
                    "• **Calvin Cycle (Light-Independent):** Takes place in the chloroplast stroma, using ATP and NADPH to fix carbon dioxide (`CO₂`) into energy-rich three-carbon sugars."

            lower.contains("relativity") || lower.contains("e=mc") || lower.contains("einstein") ->
                "**Albert Einstein's Theory of Relativity** consists of two foundational theories of modern physics, $addr:\n\n" +
                    "1. **Special Relativity (1905):** Based on the principle that the laws of physics are identical in all inertial frames and the speed of light in a vacuum (`c`) is constant for all observers. It introduces **time dilation**, **length contraction**, and mass-energy equivalence (**`E = mc²`**).\n" +
                    "2. **General Relativity (1915):** Explains **gravity** not as an invisible pulling force, but as the geometric **curvature of four-dimensional spacetime** caused by mass and energy."

            lower.contains("blockchain") || lower.contains("bitcoin") || lower.contains("cryptocurrency") ->
                "A **Blockchain** is a decentralized, cryptographically secured distributed ledger shared across a peer-to-peer network, $addr:\n\n" +
                    "• **Cryptographic Hash Chaining:** Each block stores a batch of verified transactions, a timestamp, and the **SHA-256 hash** of the previous block.\n" +
                    "• **Immutability & Consensus:** Modifying any historical record alters its hash and breaks every subsequent block, while consensus mechanisms (**Proof of Work** or **Proof of Stake**) validate new blocks without a central authority."

            lower.contains("operating system") || lower.contains("linux kernel") || lower.contains("android architecture") ->
                "An **Operating System (OS)** manages hardware resources and provides secure execution sandboxes for applications, $addr:\n\n" +
                    "• **Core Kernel Responsibilities:** CPU process scheduling, virtual memory paging, file-system management, networking stacks, and hardware device drivers.\n" +
                    "• **Android System Architecture:** Built on the **Linux Kernel**, paired with the **Android Runtime (ART)** for ahead-of-time and just-in-time compilation, Hardware Abstraction Layers (HAL), and per-app Linux UID security sandboxing."

            lower.contains("dna") || lower.contains("genetics") || lower.contains("rna") ->
                "**DNA (Deoxyribonucleic Acid)** is the hereditary molecule that carries the genetic instructions for the development and functioning of all living organisms, $addr:\n\n" +
                    "• **Double-Helix Structure:** Composed of two complementary strands of nucleotides, where **Adenine (A)** pairs with **Thymine (T)** and **Cytosine (C)** pairs with **Guanine (G)**.\n" +
                    "• **Central Dogma of Biology:** Genetic information flows from **DNA → RNA** (via transcription) **→ Proteins** (via translation in ribosomes)."

            lower.contains("black hole") || lower.contains("event horizon") ->
                "A **Black Hole** is a region of spacetime where gravity is so intense that nothing—not even light or electromagnetic radiation—can escape once it crosses the **Event Horizon**, $addr:\n\n" +
                    "• **Formation:** Stellar black holes form when massive stars exhaust their nuclear fuel and collapse under their own gravity in a supernova.\n" +
                    "• **Key Structure:** Defined by the **Event Horizon** (the point of no return), the accretion disk of superheated infalling matter, and the central gravitational singularity."

            lower.contains("internet") || lower.contains("http") || lower.contains("tcp/ip") || lower.contains("dns") ->
                "Here is how the **Internet, DNS, TCP/IP, and HTTPS** work together when you load a service, $addr:\n\n" +
                    "1. **DNS Resolution:** Translates a human-readable domain name into a numerical IP address.\n" +
                    "2. **TCP/IP & TLS Handshake:** Establishes a reliable packet-switched connection and negotiates end-to-end encryption keys.\n" +
                    "3. **HTTP/HTTPS Request & Response:** Exchanges structured headers and payloads (JSON, HTML, or media) securely between client and server."

            lower.contains("nemotron") || lower.contains("nvidia") ->
                "**NVIDIA Nemotron** is NVIDIA's family of open-weight language models pruned and distilled for high efficiency, strong instruction following, and edge/on-device deployment, $addr:\n\n" +
                    "• **Nemotron Mini 4B Instruct:** A compact 4B parameter model trained via NVIDIA's Minitron pruning and distillation pipeline, excelling at fast on-device roleplay, RAG, and function calling.\n" +
                    "• **Llama-3.1-Nemotron-Nano-8B:** An 8B reasoning-optimized open model offering high accuracy on coding, math, and complex multi-step tasks while fitting on 8GB+ hardware."

            lower.contains("newton") || lower.contains("laws of motion") ->
                "**Isaac Newton's Three Laws of Motion** form the foundation of classical mechanics, $addr:\n\n" +
                    "1. **First Law (Inertia):** An object stays at rest or in uniform motion unless acted on by a net external force.\n" +
                    "2. **Second Law (`F = ma`):** Force equals mass multiplied by acceleration.\n" +
                    "3. **Third Law (Action-Reaction):** Every action has an equal and opposite reaction."

            lower.contains("sql") && lower.contains("nosql") || lower.contains("database") ->
                "Here is how **SQL (Relational)** and **NoSQL (Non-Relational)** databases differ, $addr:\n\n" +
                    "• **SQL (PostgreSQL, SQLite/Room, MySQL):** Uses structured tables, strict schemas, foreign-key joins, and full ACID transactions—ideal for financial, relational, and structured local records.\n" +
                    "• **NoSQL (Firestore, MongoDB, Redis):** Uses flexible documents, key-value pairs, or graphs for rapid horizontal scaling and nested JSON-like data."

            lower.contains("encryption") || lower.contains("cybersecurity") || lower.contains("aes") ->
                "**Modern Encryption** protects private data in transit and at rest, $addr:\n\n" +
                    "• **Symmetric Encryption (AES-256-GCM):** Uses a single shared secret key for ultra-fast authenticated encryption of local files and databases.\n" +
                    "• **Asymmetric Encryption (RSA / Ed25519 / ECDH):** Uses a public/private key pair for digital signatures and secure TLS handshakes."

            lower.contains("gravity") ->
                "**Gravity** is the fundamental attraction between any two objects that have mass or energy, $addr.\n\n" +
                    "• **Newton's Law:** Gravitational force is proportional to the product of the two masses and inversely proportional to the square of the distance between them (`F = G · m₁m₂ / r²`).\n" +
                    "• **Einstein's General Relativity:** Mass and energy curve four-dimensional spacetime, and planets, moons, and light follow the straightest possible paths (geodesics) along that curvature.\n" +
                    "• **On Earth:** Standard gravitational acceleration at sea level is **9.81 m/s²**."

            lower.contains("python") && !lower.contains("write") ->
                "**Python** is a high-level, dynamically typed programming language famous for readable syntax and a massive ecosystem, $addr:\n\n" +
                    "• **Best For:** AI/ML (`PyTorch`, `TensorFlow`), data science (`NumPy`, `Pandas`), automation, and backend APIs (`FastAPI`, `Django`).\n" +
                    "• **Key Strengths:** Clean indentation-based syntax, batteries-included standard library, and rapid prototyping."

            lower.contains("kotlin") && !lower.contains("write") ->
                "**Kotlin** is a modern, statically typed language by JetBrains and Google's preferred language for Android development, $addr:\n\n" +
                    "• **Key Strengths:** Built-in null safety (`?` / `?:`), structured concurrency with **Coroutines & Flow**, concise `data class` / `sealed class` syntax, and 100% interoperability with Java.\n" +
                    "• **UI Framework:** Pairs natively with **Jetpack Compose** for declarative Material 3 Android apps."

            lower.contains("oop") || lower.contains("object oriented") || lower.contains("object-oriented") ->
                "**Object-Oriented Programming (OOP)** organizes code around objects that combine data and behavior, built on four pillars, $addr:\n\n" +
                    "1. **Encapsulation:** Hide internal state behind clean public methods.\n" +
                    "2. **Abstraction:** Expose only essential contracts (interfaces/abstract classes) and hide complex internals.\n" +
                    "3. **Inheritance:** Reuse and extend shared behavior across related classes.\n" +
                    "4. **Polymorphism:** Allow one interface to work cleanly with many different concrete implementations."

            lower.contains("bangladesh") || lower.contains("dhaka") ->
                "**Bangladesh** is a vibrant South Asian nation located in the fertile Bengal Delta along the Bay of Bengal, $addr.\n\n" +
                    "• **Capital & Largest City:** **Dhaka**\n" +
                    "• **Official Language:** **Bengali (Bangla)** · **Currency:** Bangladeshi Taka (**BDT / ৳**)\n" +
                    "• **Highlights:** Home to the **Sundarbans** (the world's largest mangrove forest), **Cox's Bazar** (one of the longest natural sea beaches), and a fast-growing technology and apparel economy."

            lower.contains("capital of") -> lookupCountryCapital(lower, addr)

            lower.contains("joke") || lower.contains("make me laugh") || lower.contains("funny") ->
                "Here's a quick one for you, $addr:\n\nWhy do programmers prefer dark mode?\nBecause light attracts bugs!"

            lower.contains("poem") ->
                "Here is a short original poem for you, $addr:\n\n" +
                    "*Quiet code beneath the glass,*\n" +
                    "*Silent thoughts that swiftly pass,*\n" +
                    "*No distant cloud, no borrowed light—*\n" +
                    "*Just steady sparks that shine tonight.*"

            lower.contains("motivat") || lower.contains("inspire") || lower.contains("quote") ->
                "Here is a grounding reminder, $addr:\n\n**\"Small, consistent steps taken every day outperform occasional bursts of perfection.\"** Pick just one meaningful task for the next 20 minutes—momentum always follows action."

            lower.contains("water") && (lower.contains("formula") || lower.contains("boil") || lower.contains("freeze")) ->
                "**Water (`H₂O`)** consists of two hydrogen atoms bonded to one oxygen atom, $addr:\n\n" +
                    "• **Freezing Point:** `0 °C` (`32 °F`) at standard atmospheric pressure.\n" +
                    "• **Boiling Point:** `100 °C` (`212 °F`) at sea level."

            lower.contains("solar system") || lower.contains("planets") ->
                "The **Solar System** has **8 planets** orbiting the Sun in order from closest to farthest, $addr:\n\n" +
                    "1. **Mercury**  2. **Venus**  3. **Earth**  4. **Mars** (rocky inner planets)\n" +
                    "5. **Jupiter**  6. **Saturn** (gas giants)\n" +
                    "7. **Uranus**  8. **Neptune** (ice giants)"

            lower.contains("cpu") && lower.contains("gpu") ->
                "**CPU vs. GPU**, $addr:\n\n" +
                    "• **CPU (Central Processing Unit):** Fewer, very powerful cores optimized for low-latency sequential tasks, OS scheduling, and branching logic.\n" +
                    "• **GPU (Graphics Processing Unit):** Thousands of smaller parallel cores built for massive matrix math—making it ideal for 3D rendering and neural network inference."

            lower.contains("ram") && (lower.contains("rom") || lower.contains("storage")) ->
                "**RAM vs. Storage (ROM/Flash)**, $addr:\n\n" +
                    "• **RAM (Random Access Memory):** Ultra-fast volatile working memory that holds active apps and loaded AI model weights while running—cleared on reboot.\n" +
                    "• **Storage (UFS / SSD / Flash):** Permanent non-volatile memory where your OS, apps, photos, and downloaded model files stay saved even when powered off."

            lower.contains("what day") || lower.contains("what is the date") || lower.contains("today's date") -> {
                val fmt = java.text.SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
                "Today is **${fmt.format(java.util.Date())}**, $addr."
            }

            lower.contains("what time") || lower.contains("current time") -> {
                val fmt = java.text.SimpleDateFormat("hh:mm a", Locale.getDefault())
                "It is currently **${fmt.format(java.util.Date())}**, $addr."
            }

            else -> tryConvertUnit(lower, addr)
        }
    }

    private fun lookupCountryCapital(lower: String, addr: String): String? {
        val capitals = mapOf(
            "bangladesh" to "Dhaka",
            "india" to "New Delhi",
            "pakistan" to "Islamabad",
            "united states" to "Washington, D.C.",
            "usa" to "Washington, D.C.",
            "uk" to "London",
            "united kingdom" to "London",
            "england" to "London",
            "france" to "Paris",
            "germany" to "Berlin",
            "japan" to "Tokyo",
            "china" to "Beijing",
            "south korea" to "Seoul",
            "canada" to "Ottawa",
            "australia" to "Canberra",
            "italy" to "Rome",
            "spain" to "Madrid",
            "brazil" to "Brasília",
            "saudi arabia" to "Riyadh",
            "turkey" to "Ankara",
            "uae" to "Abu Dhabi",
            "malaysia" to "Kuala Lumpur",
            "singapore" to "Singapore",
            "indonesia" to "Jakarta",
            "nepal" to "Kathmandu",
            "sri lanka" to "Sri Jayawardenepura Kotte (commercial capital: Colombo)",
            "egypt" to "Cairo",
            "russia" to "Moscow"
        )
        for ((country, capital) in capitals) {
            if (lower.contains(country)) {
                val displayCountry = country.split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                return "The capital of **$displayCountry** is **$capital**, $addr."
            }
        }
        return null
    }

    private fun tryConvertUnit(lower: String, addr: String): String? {
        // Temperature: "37 c to f" or "98.6 f to c"
        Regex("(-?\\d+(?:\\.\\d+)?)\\s*°?\\s*(c|celsius)\\s*(?:to|in)\\s*°?\\s*(f|fahrenheit)").find(lower)?.let { m ->
            val c = m.groupValues[1].toDoubleOrNull() ?: return null
            val f = c * 9.0 / 5.0 + 32.0
            return "${formatNumber(c)} °C equals **${formatNumber(f)} °F**, $addr."
        }
        Regex("(-?\\d+(?:\\.\\d+)?)\\s*°?\\s*(f|fahrenheit)\\s*(?:to|in)\\s*°?\\s*(c|celsius)").find(lower)?.let { m ->
            val f = m.groupValues[1].toDoubleOrNull() ?: return null
            val c = (f - 32.0) * 5.0 / 9.0
            return "${formatNumber(f)} °F equals **${formatNumber(c)} °C**, $addr."
        }
        // Distance: km <-> miles
        Regex("(\\d+(?:\\.\\d+)?)\\s*(km|kilometers?)\\s*(?:to|in)\\s*(mi|miles?)").find(lower)?.let { m ->
            val km = m.groupValues[1].toDoubleOrNull() ?: return null
            return "${formatNumber(km)} km equals **${formatNumber(km * 0.621371)} miles**, $addr."
        }
        Regex("(\\d+(?:\\.\\d+)?)\\s*(mi|miles?)\\s*(?:to|in)\\s*(km|kilometers?)").find(lower)?.let { m ->
            val mi = m.groupValues[1].toDoubleOrNull() ?: return null
            return "${formatNumber(mi)} miles equals **${formatNumber(mi * 1.60934)} km**, $addr."
        }
        // Weight: kg <-> lbs
        Regex("(\\d+(?:\\.\\d+)?)\\s*(kg|kilograms?)\\s*(?:to|in)\\s*(lb|lbs|pounds?)").find(lower)?.let { m ->
            val kg = m.groupValues[1].toDoubleOrNull() ?: return null
            return "${formatNumber(kg)} kg equals **${formatNumber(kg * 2.20462)} lbs**, $addr."
        }
        Regex("(\\d+(?:\\.\\d+)?)\\s*(lb|lbs|pounds?)\\s*(?:to|in)\\s*(kg|kilograms?)").find(lower)?.let { m ->
            val lb = m.groupValues[1].toDoubleOrNull() ?: return null
            return "${formatNumber(lb)} lbs equals **${formatNumber(lb * 0.453592)} kg**, $addr."
        }
        return null
    }
}
