package com.novelverse.core.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NovelDao {
    @Query("SELECT * FROM novels WHERE inLibrary = 0 ORDER BY updatedAt DESC, id ASC LIMIT :limit")
    fun observeArchived(limit: Int): Flow<List<NovelEntity>>
    @Query("""SELECT * FROM novels WHERE inLibrary = 1 AND (title LIKE :pattern ESCAPE '\' OR author LIKE :pattern ESCAPE '\') ORDER BY updatedAt DESC, id ASC LIMIT :limit""")
    fun observeLibrary(limit: Int, pattern: String): Flow<List<NovelEntity>>
    @Query("SELECT * FROM novels WHERE id = :id")
    fun observeNovel(id: String): Flow<NovelEntity?>
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(novel: NovelEntity)
    @Query("UPDATE novels SET readingStatus = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, updatedAt: Long)
    @Query("UPDATE novels SET inLibrary = :inLibrary, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setInLibrary(id: String, inLibrary: Boolean, updatedAt: Long)
}

@Database(
    entities = [NovelEntity::class, SourceEntity::class, NovelSourceEntity::class, ChapterEntity::class,
        SourceChapterEntity::class, ChapterMappingEntity::class, ReadingProgressEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class NovelDatabase : RoomDatabase() { abstract fun novels(): NovelDao }
