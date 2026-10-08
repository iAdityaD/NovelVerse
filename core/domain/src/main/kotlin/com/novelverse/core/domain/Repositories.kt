package com.novelverse.core.domain

import com.novelverse.core.model.*
import kotlinx.coroutines.flow.Flow

interface NovelRepository {
    /** Bounded view; callers increase limit explicitly rather than loading the whole library. */
    fun observeLibrary(limit: Int, query: String = ""): Flow<List<Novel>>
    fun observeArchived(limit: Int): Flow<List<Novel>>
    fun observeNovel(id: String): Flow<Novel?>
    suspend fun insert(novel: Novel)
    suspend fun updateStatus(id: String, status: ReadingStatus, updatedAt: Long)
    /** Archives only. Sources, content, progress and annotations remain owned by the user. */
    suspend fun setInLibrary(id: String, inLibrary: Boolean, updatedAt: Long)
}

interface PreferencesRepository {
    val preferences: Flow<UserPreferences>
    suspend fun setTheme(theme: AppTheme)
    suspend fun setReaderMode(mode: ReaderMode)
    suspend fun setFontSize(size: Int)
    suspend fun setLibraryLayout(layout: LibraryLayout)
}

fun interface IdGenerator { fun next(): String }
fun interface Clock { fun nowMillis(): Long }

class AddManualNovel(
    private val repository: NovelRepository,
    private val ids: IdGenerator,
    private val clock: Clock,
) {
    suspend operator fun invoke(title: String, author: String, description: String): String {
        val cleanTitle = title.trim()
        require(cleanTitle.isNotEmpty()) { "Enter a novel title." }
        require(cleanTitle.length <= 300) { "Use a title of 300 characters or fewer." }
        require(author.trim().length <= 200) { "Use an author name of 200 characters or fewer." }
        require(description.trim().length <= 10_000) { "Description must be 10,000 characters or fewer." }
        val id = ids.next()
        val now = clock.nowMillis()
        repository.insert(Novel(id, cleanTitle, author.trim(), description.trim(), ReadingStatus.PLAN_TO_READ, now, now))
        return id
    }
}
