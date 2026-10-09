package com.novelverse.core.domain

import kotlinx.coroutines.flow.Flow

data class WebsiteSource(val id: String, val name: String, val baseUrl: String, val configuration: String, val enabled: Boolean)
data class SearchHit(val sourceId: String, val title: String, val author: String, val url: String)
data class CatalogChapter(val id: String, val novelId: String, val title: String, val order: Long, val downloaded: Boolean)
data class ChapterVersion(val id: String, val chapterId: String, val sourceChapterId: String, val sourceName: String, val paragraphs: List<String>, val warning: String?, val offline: Boolean)
data class ChapterChoice(val sourceChapterId: String, val sourceName: String, val title: String, val confirmed: Boolean = true)
data class ReaderPosition(val chapterId: String, val paragraph: Int, val offset: Int, val fraction: Double)
data class LocalDownload(val versionId: String, val novelId: String, val title: String, val source: String, val characters: Int)
data class SavedBookmark(val id: String, val chapterId: String, val paragraph: Int, val note: String)

interface ReadingRepository {
    fun sources(): Flow<List<WebsiteSource>>
    suspend fun saveSource(configuration: String): String
    suspend fun setSourceEnabled(id: String, enabled: Boolean)
    suspend fun testSource(configuration: String, novelUrl: String): String
    suspend fun search(sourceId: String, query: String): List<SearchHit>
    suspend fun importNovel(hit: SearchHit, linkToNovelId: String? = null): String
    suspend fun refresh(novelId: String): Int
    fun chapters(novelId: String): Flow<List<CatalogChapter>>
    suspend fun loadChapter(chapterId: String, sourceChapterId: String? = null, force: Boolean = false): ChapterVersion
    suspend fun chapterChoices(chapterId: String): List<ChapterChoice>
    suspend fun confirmMapping(chapterId: String, sourceChapterId: String)
    suspend fun savePosition(novelId: String, position: ReaderPosition)
    suspend fun position(novelId: String): ReaderPosition?
    suspend fun download(chapterId: String)
    fun downloads(): Flow<List<LocalDownload>>
    suspend fun deleteDownload(versionId: String)
    suspend fun clearCache()
    suspend fun bookmark(chapterId: String, versionId: String, paragraph: Int, note: String)
    fun bookmarks(chapterId: String): Flow<List<SavedBookmark>>
}
