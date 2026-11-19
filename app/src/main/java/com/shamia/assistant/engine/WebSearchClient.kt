package com.shamia.assistant.engine

import com.shamia.assistant.model.GroundingLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

object WebSearchClient {

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 15; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    fun decodeHtmlEntities(input: String, collapseNewlines: Boolean = true): String {
        if (input.isEmpty()) return ""
        var text = input
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), if (collapseNewlines) " " else "\n")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&quot;", "\"", ignoreCase = true)
            .replace("&ldquo;", "\"", ignoreCase = true)
            .replace("&rdquo;", "\"", ignoreCase = true)
            .replace("&#39;", "'", ignoreCase = true)
            .replace("&#039;", "'", ignoreCase = true)
            .replace("&#x27;", "'", ignoreCase = true)
            .replace("&apos;", "'", ignoreCase = true)
            .replace("&lsquo;", "'", ignoreCase = true)
            .replace("&rsquo;", "'", ignoreCase = true)
            .replace("&amp;", "&", ignoreCase = true)
            .replace("&lt;", "<", ignoreCase = true)
            .replace("&gt;", ">", ignoreCase = true)
            .replace("&nbsp;", " ", ignoreCase = true)
            .replace("&ndash;", "–", ignoreCase = true)
            .replace("&mdash;", "—", ignoreCase = true)
            .replace("&hellip;", "…", ignoreCase = true)
            .replace("&#8211;", "–")
            .replace("&#8212;", "—")
            .replace("&#8217;", "'")
            .replace("&#8220;", "\"")
            .replace("&#8221;", "\"")

        text = Regex("&#(\\d+);").replace(text) { m ->
            val code = m.groupValues[1].toIntOrNull()
            if (code != null && code in 32..65535) code.toChar().toString() else ""
        }
        text = Regex("&#x([0-9a-fA-F]+);").replace(text) { m ->
            val code = m.groupValues[1].toIntOrNull(16)
            if (code != null && code in 32..65535) code.toChar().toString() else ""
        }
        return if (collapseNewlines) {
            text.replace(Regex("\\s+"), " ").trim()
        } else {
            text.lines()
                .joinToString("\n") { line -> line.replace(Regex("[ \\t]+"), " ").trimEnd() }
                .replace(Regex("\\n{3,}"), "\n\n")
                .trim()
        }
    }

    fun cleanSearchQuery(raw: String): String {
        return raw.trim()
            .replace(
                Regex(
                    "^(?:please\\s+)?(?:can\\s+you\\s+)?(?:search(?:\\s+for)?|research|look\\s+up|find(?:\\s+out)?|tell\\s+me\\s+about|what\\s+is|what\\s+are|who\\s+is|explain|define|google(?:\\s+search)?)\\s+",
                    RegexOption.IGNORE_CASE
                ),
                ""
            )
            .trim('?', '.', '!', ' ')
            .ifBlank { raw.trim() }
    }

    suspend fun searchGoogleAndOpenWeb(query: String): Pair<String, List<GroundingLink>> =
        withContext(Dispatchers.IO) {
            val cleanQuery = cleanSearchQuery(query)
            if (cleanQuery.isEmpty()) return@withContext "" to emptyList()

            val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
            val queryTokens = cleanQuery.lowercase(Locale.US)
                .split(Regex("[^a-z0-9]+"))
                .filter { it.length >= 3 }
                .toSet()

            coroutineScope {
                // 1. Wikipedia REST & Extract Search (clean encyclopedic facts without cut-off sentences)
                val wikiDeferred = async {
                    val wikiFacts = mutableListOf<String>()
                    val wikiLinks = mutableListOf<GroundingLink>()
                    runCatching {
                        val url = URL(
                            "https://en.wikipedia.org/w/api.php?action=query&prop=extracts&exintro=1&explaintext=1&exsentences=4&generator=search&gsrsearch=$encoded&gsrlimit=2&format=json"
                        )
                        val conn = (url.openConnection() as HttpURLConnection).apply {
                            connectTimeout = 3000
                            readTimeout = 3500
                            setRequestProperty("User-Agent", USER_AGENT)
                        }
                        if (conn.responseCode == 200) {
                            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                            val pages = json.optJSONObject("query")?.optJSONObject("pages")
                            if (pages != null) {
                                val keys = pages.keys()
                                while (keys.hasNext()) {
                                    val key = keys.next()
                                    val page = pages.optJSONObject(key) ?: continue
                                    val title = decodeHtmlEntities(page.optString("title"))
                                    val extract = decodeHtmlEntities(page.optString("extract"))
                                    val pageId = page.optLong("pageid")
                                    val titleLower = title.lowercase(Locale.US)
                                    val extractLower = extract.lowercase(Locale.US)
                                    val titleRelevant = queryTokens.isEmpty() ||
                                        queryTokens.any { tok -> titleLower.contains(tok) } ||
                                        (queryTokens.size >= 2 && queryTokens.count { tok -> extractLower.contains(tok) } >= 2)
                                    if (title.isNotEmpty() && extract.length > 45 && titleRelevant) {
                                        wikiFacts.add(extract)
                                        wikiLinks.add(GroundingLink(title, "https://en.wikipedia.org/?curid=$pageId"))
                                    }
                                }
                            }
                        }
                        conn.disconnect()
                    }
                    wikiFacts to wikiLinks
                }

                // 2. DuckDuckGo Instant Answer API
                val ddgDeferred = async {
                    val ddgFacts = mutableListOf<String>()
                    val ddgLinks = mutableListOf<GroundingLink>()
                    runCatching {
                        val url = URL("https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1")
                        val conn = (url.openConnection() as HttpURLConnection).apply {
                            connectTimeout = 2800
                            readTimeout = 3000
                            setRequestProperty("User-Agent", USER_AGENT)
                        }
                        if (conn.responseCode == 200) {
                            val ddg = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                            val abstractText = decodeHtmlEntities(ddg.optString("AbstractText"))
                            val abstractUrl = ddg.optString("AbstractURL").trim()
                            val heading = decodeHtmlEntities(ddg.optString("Heading"))
                            if (abstractText.length > 35) {
                                ddgFacts.add(abstractText)
                                if (abstractUrl.isNotEmpty()) {
                                    ddgLinks.add(GroundingLink(heading.ifEmpty { cleanQuery }, abstractUrl))
                                }
                            }
                        }
                        conn.disconnect()
                    }
                    ddgFacts to ddgLinks
                }

                // 3. Google News RSS — ONLY fetched when the user explicitly asks for current news/latest updates
                val wantsNews = Regex("\\b(news|latest|today|headline|breaking|2025|2026|update|score|match|price|weather)\\b", RegexOption.IGNORE_CASE)
                    .containsMatchIn(query)
                val newsDeferred = async {
                    val newsFacts = mutableListOf<String>()
                    val newsLinks = mutableListOf<GroundingLink>()
                    if (!wantsNews) return@async newsFacts to newsLinks
                    runCatching {
                        val url = URL("https://news.google.com/rss/search?q=$encoded&hl=en-US&gl=US&ceid=US:en")
                        val conn = (url.openConnection() as HttpURLConnection).apply {
                            connectTimeout = 2800
                            readTimeout = 3000
                            setRequestProperty("User-Agent", USER_AGENT)
                            setRequestProperty("Cache-Control", "no-cache")
                        }
                        if (conn.responseCode == 200) {
                            val xml = conn.inputStream.bufferedReader().use { it.readText() }
                            Regex("<item>([\\s\\S]*?)</item>").findAll(xml).take(4).forEach { match ->
                                val block = match.groupValues[1]
                                val rawTitle = Regex("<title>([\\s\\S]*?)</title>").find(block)?.groupValues?.get(1)
                                    ?.replace(Regex("<!\\[CDATA\\[(.*?)\\]\\]>"), "$1").orEmpty()
                                val cleanTitle = decodeHtmlEntities(rawTitle)
                                    .replace(Regex("\\s+-\\s+[^-]+$"), "")
                                    .trim()
                                val link = Regex("<link>([\\s\\S]*?)</link>").find(block)?.groupValues?.get(1)
                                    ?.trim().orEmpty()
                                val lowerTitle = cleanTitle.lowercase(Locale.US)
                                val relevant = queryTokens.isEmpty() || queryTokens.all { lowerTitle.contains(it) } ||
                                    (queryTokens.size >= 2 && queryTokens.count { lowerTitle.contains(it) } >= 2)
                                if (cleanTitle.length > 20 && link.isNotEmpty() && relevant) {
                                    newsLinks.add(GroundingLink(cleanTitle, link))
                                    newsFacts.add(cleanTitle)
                                }
                            }
                        }
                        conn.disconnect()
                    }
                    newsFacts to newsLinks
                }

                val (wikiFacts, wikiLinks) = wikiDeferred.await()
                val (ddgFacts, ddgLinks) = ddgDeferred.await()
                val (newsFacts, newsLinks) = newsDeferred.await()

                val allFacts = buildList {
                    addAll(ddgFacts)
                    addAll(wikiFacts)
                    if (wantsNews) addAll(newsFacts.take(3))
                }.distinct()

                val allLinks = (ddgLinks + wikiLinks + newsLinks).distinctBy { it.uri }.take(4)
                val structuredContext = allFacts.joinToString("\n\n")
                structuredContext to allLinks
            }
        }

    /**
     * Synthesizes a clean, constructive, natural human answer directly from verified encyclopedic
     * context — never dumping raw pipe-separated fragments, unrelated news, or robotic headers.
     */
    fun synthesizeConstructiveAnswer(
        userQuery: String,
        searchContext: String,
        addressAs: String
    ): String {
        val cleanParagraphs = decodeHtmlEntities(searchContext, collapseNewlines = false)
            .split(Regex("\\n{2,}|\\s*\\|\\s*"))
            .map { it.trim().removePrefix("•").trim() }
            .filter { it.length > 40 }
            .distinct()

        if (cleanParagraphs.isEmpty()) return ""

        val primary = cleanParagraphs.first()
        val secondary = cleanParagraphs.drop(1).firstOrNull()

        return buildString {
            append(primary)
            if (!primary.endsWith(".") && !primary.endsWith("!") && !primary.endsWith("?")) append(".")
            if (!secondary.isNullOrBlank() && secondary.length > 45) {
                append("\n\n")
                append(secondary)
                if (!secondary.endsWith(".") && !secondary.endsWith("!") && !secondary.endsWith("?")) append(".")
            }
        }.trim()
    }
}
