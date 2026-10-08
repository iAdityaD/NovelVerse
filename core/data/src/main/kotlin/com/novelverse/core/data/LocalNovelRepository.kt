package com.novelverse.core.data

import com.novelverse.core.domain.NovelRepository
import com.novelverse.core.model.*
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class LocalNovelRepository @Inject constructor(private val dao: NovelDao) : NovelRepository {
    override fun observeArchived(limit: Int) = dao.observeArchived(limit.coerceIn(1, 1000)).map { rows -> rows.map { it.toModel() } }
    override fun observeLibrary(limit: Int, query: String) = dao.observeLibrary(
        limit.coerceIn(1, 1000), "%${query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")}%",
    ).map { rows -> rows.map { it.toModel() } }
    override fun observeNovel(id: String) = dao.observeNovel(id).map { it?.toModel() }
    override suspend fun insert(novel: Novel) = dao.insert(NovelEntity(
        novel.id, novel.title, novel.author, novel.description, novel.readingStatus.name,
        novel.createdAt, novel.updatedAt, novel.inLibrary,
    ))
    override suspend fun updateStatus(id: String, status: ReadingStatus, updatedAt: Long) = dao.updateStatus(id, status.name, updatedAt)
    override suspend fun setInLibrary(id: String, inLibrary: Boolean, updatedAt: Long) = dao.setInLibrary(id, inLibrary, updatedAt)
    private fun NovelEntity.toModel() = Novel(id, title, author, description,
        ReadingStatus.entries.firstOrNull { it.name == readingStatus } ?: ReadingStatus.PLAN_TO_READ,
        createdAt, updatedAt, inLibrary)
}
