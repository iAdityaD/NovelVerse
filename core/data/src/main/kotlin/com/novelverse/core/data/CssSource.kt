package com.novelverse.core.data

import com.novelverse.core.domain.SearchHit
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URI

/** Non-executable, versioned CSS definition. No imported code or arbitrary headers. */
data class CssSource(
    val id: String, val name: String, val baseUrl: String, val searchPath: String, val queryParameter: String,
    val searchItem: String, val searchTitle: String, val searchLink: String,
    val novelTitle: String, val novelAuthor: String, val catalogItem: String, val catalogLink: String,
    val chapterContent: String, val catalogNext: String, val chapterNext: String,
) {
    companion object {
        fun parse(json: String): CssSource {
            require(json.length <= 65_536) { "Source definition exceeds 64 KB." }
            val o = JSONObject(json)
            require(o.getInt("schemaVersion") == 1) { "Unsupported definition version." }
            fun field(key: String, required: Boolean = true): String {
                val value = o.optString(key).trim()
                require(value.length <= 500 && (!required || value.isNotBlank())) { "Invalid or missing $key." }
                return value
            }
            val source = CssSource(field("id"), field("name"), field("baseUrl").trimEnd('/'), field("searchPath"),
                field("queryParameter"), field("searchItem"), field("searchTitle"), field("searchLink"),
                field("novelTitle"), field("novelAuthor", false), field("catalogItem"), field("catalogLink"),
                field("chapterContent"), field("catalogNext", false), field("chapterNext", false))
            require(source.id.matches(Regex("[a-z0-9][a-z0-9-]{0,79}"))) { "Invalid source ID." }
            val uri = URI(source.baseUrl)
            require(uri.scheme == "https" && uri.host != null && uri.rawUserInfo == null && uri.port == -1 && uri.path.isNullOrEmpty() && uri.query == null && uri.fragment == null) { "Use an HTTPS origin, without credentials or a path." }
            require(source.searchPath.startsWith("/") && !source.searchPath.startsWith("//")) { "Search path must be relative to the source origin." }
            val probe = Jsoup.parse("<html></html>")
            listOf(source.searchItem, source.searchTitle, source.searchLink, source.novelTitle, source.novelAuthor,
                source.catalogItem, source.catalogLink, source.chapterContent, source.catalogNext, source.chapterNext)
                .filter { it.isNotBlank() }.forEach { probe.select(it) }
            return source
        }
    }
    fun validateUrl(url: String): String {
        val candidate = URI(baseUrl).resolve(url)
        val base = URI(baseUrl)
        require(candidate.scheme == "https" && candidate.host == base.host && candidate.port == -1 && candidate.userInfo == null) { "The source attempted to leave its approved HTTPS origin." }
        return candidate.toString()
    }
    fun searchResults(html: String, url: String): List<SearchHit> {
        val doc = Jsoup.parse(html, url)
        return doc.select(searchItem).take(100).mapNotNull { item ->
            val title = item.selectFirst(searchTitle)?.text()?.trim().orEmpty()
            val link = item.selectFirst(searchLink)?.absUrl("href").orEmpty()
            if (title.isBlank() || link.isBlank()) null else SearchHit(id, title, "", validateUrl(link))
        }.distinctBy { it.url }
    }
    fun paragraphs(html: String, url: String): List<String> {
        val doc = Jsoup.parse(html, url)
        val root = doc.selectFirst(chapterContent) ?: error("Chapter container not found. Repair the source selector.")
        root.select("script,style,nav,form,button,iframe,noscript").remove()
        val blocks = root.select("p,h1,h2,h3,li,blockquote").filter { it.parents().none { parent -> parent != root && parent.tagName() in setOf("p", "li", "blockquote") } }
        val result = if (blocks.isEmpty()) root.wholeText().lines() else blocks.map { it.wholeText() }
        return result.map { it.trim() }.filter { it.isNotBlank() }.flatMap { it.chunked(6000) }.also {
            require(it.isNotEmpty()) { "The chapter body is empty." }
            require(it.sumOf(String::length) <= 1_000_000) { "Chapter exceeds the safe text limit." }
        }
    }
}
