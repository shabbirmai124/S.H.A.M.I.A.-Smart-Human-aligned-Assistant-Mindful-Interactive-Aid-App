package com.shamia.assistant.engine

import com.shamia.assistant.model.GroundingLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object WebSearchClient {

    suspend fun searchGoogleAndOpenWeb(query: String): Pair<String, List<GroundingLink>> =
        withContext(Dispatchers.IO) {
            val cleanQuery = query.trim()
                .replace(
                    Regex(
                        "^(?:search(?:\\s+for)?|research|look\\s+up|find(?:\\s+out)?|tell\\s+me\\s+about|google(?:\\s+search)?)\\s+",
                        RegexOption.IGNORE_CASE
                    ),
                    ""
                )
                .trim()
            if (cleanQuery.isEmpty()) return@withContext "" to emptyList()

            val links = mutableListOf<GroundingLink>()
            val snippets = mutableListOf<String>()
            val encoded = URLEncoder.encode(cleanQuery, "UTF-8")

            // 1. Live Google Search Index RSS
            runCatching {
                val url = URL("https://news.google.com/rss/search?q=$encoded&hl=en-US&gl=US&ceid=US:en")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4500
                    readTimeout = 4500
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 15) SHAMIA/1.0")
                    setRequestProperty("Cache-Control", "no-cache")
                }
                if (conn.responseCode == 200) {
                    val xml = conn.inputStream.bufferedReader().use { it.readText() }
                    Regex("<item>([\\s\\S]*?)</item>").findAll(xml).take(4).forEach { match ->
                        val block = match.groupValues[1]
                        val title = Regex("<title>([\\s\\S]*?)</title>").find(block)?.groupValues?.get(1)
                            ?.replace(Regex("<!\\[CDATA\\[(.*?)\\]\\]>"), "$1")?.trim().orEmpty()
                        val link = Regex("<link>([\\s\\S]*?)</link>").find(block)?.groupValues?.get(1)
                            ?.trim().orEmpty()
                        if (title.isNotEmpty() && link.isNotEmpty()) {
                            links.add(GroundingLink(title, link))
                            snippets.add(title)
                        }
                    }
                }
                conn.disconnect()
            }

            // 2. DuckDuckGo Instant Answer API
            runCatching {
                val url = URL("https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 3500
                    readTimeout = 3500
                }
                if (conn.responseCode == 200) {
                    val ddg = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    val abstractText = ddg.optString("AbstractText").trim()
                    val abstractUrl = ddg.optString("AbstractURL").trim()
                    val heading = ddg.optString("Heading").trim()
                    if (abstractText.isNotEmpty()) {
                        snippets.add(abstractText)
                        if (abstractUrl.isNotEmpty()) {
                            links.add(GroundingLink(heading.ifEmpty { "DuckDuckGo Answer" }, abstractUrl))
                        }
                    }
                }
                conn.disconnect()
            }

            // 3. Wikipedia Open Search API
            runCatching {
                val url = URL("https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encoded&format=json&srlimit=2")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4000
                    readTimeout = 4000
                }
                if (conn.responseCode == 200) {
                    val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    val arr = json.optJSONObject("query")?.optJSONArray("search") ?: JSONArray()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val title = obj.optString("title")
                        val snippet = obj.optString("snippet").replace(Regex("<[^>]+>"), "")
                        val pageId = obj.optLong("pageid")
                        if (title.isNotEmpty()) {
                            links.add(GroundingLink(title, "https://en.wikipedia.org/?curid=$pageId"))
                            if (snippet.isNotEmpty()) snippets.add(snippet)
                        }
                    }
                }
                conn.disconnect()
            }

            snippets.joinToString(" | ") to links.distinctBy { it.uri }.take(5)
        }
}
