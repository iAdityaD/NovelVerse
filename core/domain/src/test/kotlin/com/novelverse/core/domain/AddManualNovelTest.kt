package com.novelverse.core.domain

import com.novelverse.core.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AddManualNovelTest {
    private class MemoryRepository : NovelRepository {
        val rows = MutableStateFlow<List<Novel>>(emptyList())
        override fun observeArchived(limit: Int) = rows.map { it.filterNot { n -> n.inLibrary }.take(limit) }
        override fun observeLibrary(limit: Int, query: String): Flow<List<Novel>> = rows.map { it.filter { n -> n.inLibrary }.take(limit) }
        override fun observeNovel(id: String) = rows.map { it.firstOrNull { n -> n.id == id } }
        override suspend fun insert(novel: Novel) { rows.value += novel }
        override suspend fun updateStatus(id: String, status: ReadingStatus, updatedAt: Long) = Unit
        override suspend fun setInLibrary(id: String, inLibrary: Boolean, updatedAt: Long) = Unit
    }
    @Test fun `manual novels have independent identity even when titles match`() = runTest {
        val repository = MemoryRepository()
        var sequence = 0
        val add = AddManualNovel(repository, IdGenerator { "novel-${++sequence}" }, Clock { 1234L })
        add("  Same title  ", " A ", " Notes ")
        add("Same title", "B", "")
        assertEquals(2, repository.rows.value.size)
        assertNotEquals(repository.rows.value[0].id, repository.rows.value[1].id)
        assertEquals("Same title", repository.rows.value[0].title)
        assertEquals("A", repository.rows.value[0].author)
        assertEquals(1234L, repository.rows.value[0].createdAt)
        assertEquals(ReadingStatus.PLAN_TO_READ, repository.rows.value[0].readingStatus)
    }
    @Test fun `invalid input never reaches persistence`() = runTest {
        val repository = MemoryRepository()
        val add = AddManualNovel(repository, IdGenerator { "id" }, Clock { 0L })
        for ((title, author, description) in listOf(Triple(" ", "", ""), Triple("x".repeat(301), "", ""), Triple("Novel", "x".repeat(201), ""), Triple("Novel", "", "x".repeat(10_001)))) {
            try { add(title, author, description); fail("Expected validation failure") }
            catch (_: IllegalArgumentException) { /* Expected */ }
        }
        assertTrue(repository.rows.value.isEmpty())
    }
}
