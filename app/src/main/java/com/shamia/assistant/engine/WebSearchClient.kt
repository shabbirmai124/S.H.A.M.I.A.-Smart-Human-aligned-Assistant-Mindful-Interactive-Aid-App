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

    fun decodeHtmlEntities(input: String): String {
        if (input.isEmpty()) return ""
        var text = input
            .replace(Regex("<[^>]+>"), " ")
            .replace("&quot;", "\"", ignoreCase = true)
            .replace("&ldquo;", "\"", ignoreCase = true)
            .replace("&rdquo;", "\"", ignoreCase = true)
            .replace("&#39;", "'", ignoreCase = true)
            .replace("&#039;", "'", ignoreCase = true)
            .replace("&apos;", "'", ignoreCase = true)
            .replace("&lsquo;", "'", ignoreCase = true)
            .replace("&rsquo;", "'", ignoreCase = true)
            .replace("&amp;", "&", ignoreCase = true)
            .replace("&lt;", "<", ignoreCase = true)
            .replace("&gt;", ">", ignoreCase = true)
            .replace("&nbsp;", " ", ignoreCase = true)
            .replace("&ndash;", "–", ignoreCase = true)
            .replace("&mdash;", "—", ignoreCase = true)
            .replace("&#8211;", "–")
            .replace("&#8212;", "—")
            .replace("&#8217;", "'")
            .replace("&#8220;", "\"")
            .replace("&#8221;", "\"")

        text = Regex("&#(\\d+);").replace(text) { m ->
            val code = m.groupValues[1].toIntOrNull()
            if (code != null && code in 32..65535) code.toChar().toString() else ""
        }
        return text.replace(Regex("\\s+"), " ").trim()
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
                                    if (title.isNotEmpty() && extract.length > 35) {
                                        wikiFacts.add("$title: $extract")
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
                            if (abstractText.isNotEmpty()) {
                                ddgFacts.add(abstractText)
                                if (abstractUrl.isNotEmpty()) {
                                    ddgLinks.add(GroundingLink(heading.ifEmpty { "DuckDuckGo Instant Summary" }, abstractUrl))
                                }
                            }
                            val related = ddg.optJSONArray("RelatedTopics") ?: JSONArray()
                            for (i in 0 until minOf(2, related.length())) {
                                val item = related.optJSONObject(i) ?: continue
                                val rText = decodeHtmlEntities(item.optString("Text"))
                                val rUrl = item.optString("FirstURL").trim()
                                if (rText.length > 30) {
                                    ddgFacts.add(rText)
                                    if (rUrl.isNotEmpty()) {
                                        ddgLinks.add(GroundingLink(rText.take(48), rUrl))
                                    }
                                }
                            }
                        }
                        conn.disconnect()
                    }
                    ddgFacts to ddgLinks
                }

                // 3. Google News RSS (filtered for relevance & stripped of HTML entities)
                val newsDeferred = async {
                    val newsFacts = mutableListOf<String>()
                    val newsLinks = mutableListOf<GroundingLink>()
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
                                val relevant = queryTokens.isEmpty() || queryTokens.any { lowerTitle.contains(it) }
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
                    if (isEmpty() || query.contains(Regex("\\b(news|latest|today|2025|2026|update)\\b", RegexOption.IGNORE_CASE))) {
                        addAll(newsFacts.take(3))
                    }
                }.distinct()

                val allLinks = (ddgLinks + wikiLinks + newsLinks).distinctBy { it.uri }.take(5)
                val structuredContext = allFacts.joinToString("\n• ", prefix = if (allFacts.isNotEmpty()) "• " else "")
                structuredContext to allLinks
            }
        }

    /**
     * Synthesizes a clean, constructive, natural human answer from search facts when
     * offline/local synthesis is used, never dumping raw pipe-separated fragments or terminal commands.
     */
    fun synthesizeConstructiveAnswer(
        userQuery: String,
        searchContext: String,
        addressAs: String
    ): String {
        val addr = addressAs.trim().ifEmpty { "Sir" }
        val cleanFacts = decodeHtmlEntities(searchContext)
            .split(Regex("[\n|•]+"))
            .map { it.trim() }
            .filter { it.length > 25 }
            .distinct()

        if (cleanFacts.isEmpty()) return ""

        val primary = cleanFacts.first()
        val supporting = cleanFacts.drop(1).take(2)

        return buildString {
            append("Here is a clear summary of what I found for you, $addr:\n\n")
            append(primary)
            if (!primary.endsWith(".")) append(".")
            if (supporting.isNotEmpty()) {
                append("\n\nKey highlights:\n")
                supporting.forEach { item ->
                    val sentence = if (item.endsWith(".")) item else "$item."
                    append("• $sentence\n")
                }
            }
        }.trim()
    }
}
