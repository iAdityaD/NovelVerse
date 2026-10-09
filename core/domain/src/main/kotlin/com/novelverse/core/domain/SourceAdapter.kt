package com.novelverse.core.domain

/** Transport-neutral contract. No native code or scripts may come from source definitions. */
enum class SourceCapability { SEARCH, METADATA, CATALOG, CHAPTER, UPDATE_CHECK }
data class SourceNovelRef(val sourceId: String, val providerId: String, val url: String)
data class SourceNovel(val ref: SourceNovelRef, val title: String, val author: String?, val synopsis: String?)
data class SourceChapterRef(val novel: SourceNovelRef, val providerId: String, val url: String, val rawTitle: String)
data class SourcePage<T>(val items: List<T>, val nextCursor: String?, val complete: Boolean)
data class TextBlock(val id: String, val text: String, val kind: BlockKind)
enum class BlockKind { HEADING, PARAGRAPH, SCENE_BREAK }
data class ExtractedChapter(val blocks: List<TextBlock>, val continuationComplete: Boolean, val warnings: Set<String>)

sealed interface SourceResult<out T> {
    data class Success<T>(val value: T) : SourceResult<T>
    data object NotModified : SourceResult<Nothing>
    data class Failure(val reason: SourceFailure, val retryAtMillis: Long? = null) : SourceResult<Nothing>
}
enum class SourceFailure {
    UNSUPPORTED_CAPABILITY, OFFLINE, TIMEOUT, RATE_LIMITED, ACCESS_DENIED,
    AUTHENTICATION_REQUIRED, PARSER_CHANGED, MISSING_CONTENT, UNSUPPORTED_RENDERING, POLICY_BLOCKED,
}

interface SourceAdapter {
    val sourceId: String
    val capabilities: Set<SourceCapability>
    suspend fun validate(testUrl: String): SourceResult<Map<String, Boolean>>
    suspend fun search(query: String, cursor: String?): SourceResult<SourcePage<SourceNovel>>
    suspend fun fetchNovel(ref: SourceNovelRef): SourceResult<SourceNovel>
    suspend fun fetchCatalog(ref: SourceNovelRef, cursor: String?): SourceResult<SourcePage<SourceChapterRef>>
    suspend fun fetchChapter(ref: SourceChapterRef, validator: String?): SourceResult<ExtractedChapter>
    suspend fun checkUpdates(ref: SourceNovelRef, validator: String?): SourceResult<SourcePage<SourceChapterRef>>
}

/** Baseline definition validation only. A future transport must additionally validate DNS and redirects. */
data class SourceDefinition(
    val schemaVersion: Int,
    val id: String,
    val name: String,
    val baseUrl: String,
    val capabilities: Set<SourceCapability>,
    val selectors: Map<String, String>,
)

object SourceDefinitionValidator {
    fun errors(definition: SourceDefinition): List<String> = buildList {
        if (definition.schemaVersion != 1) add("Unsupported source schema version")
        if (!definition.id.matches(Regex("[a-z0-9][a-z0-9-]{0,79}"))) add("Invalid source ID")
        if (definition.name.isBlank() || definition.name.length > 120) add("Invalid source name")
        val uri = runCatching { java.net.URI(definition.baseUrl) }.getOrNull()
        if (uri == null || uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null ||
            uri.fragment != null || uri.query != null || uri.port !in listOf(-1, 443)) {
            add("Base URL must be an HTTPS origin without credentials, query, fragment or custom port")
        } else if (uri.path !in listOf("", "/")) add("Base URL must be an origin")
        if (definition.capabilities.isEmpty()) add("Select at least one capability")
        if (definition.selectors.size > 40 || definition.selectors.any { it.value.isBlank() || it.value.length > 500 }) {
            add("Selectors must be nonblank and bounded")
        }
        val required = mapOf(
            SourceCapability.SEARCH to setOf("search.item", "search.title", "search.url"),
            SourceCapability.METADATA to setOf("novel.title"),
            SourceCapability.CATALOG to setOf("catalog.item", "catalog.title", "catalog.url"),
            SourceCapability.CHAPTER to setOf("chapter.content"),
        )
        definition.capabilities.forEach { capability ->
            required[capability].orEmpty().forEach { if (definition.selectors[it].isNullOrBlank()) add("Missing selector: $it") }
        }
        if (SourceCapability.UPDATE_CHECK in definition.capabilities && SourceCapability.CATALOG !in definition.capabilities) {
            add("Update checks require catalog support")
        }
    }
}
