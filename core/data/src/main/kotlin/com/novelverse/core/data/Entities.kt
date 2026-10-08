package com.novelverse.core.data

import androidx.room.*

@Entity(tableName = "novels", indices = [Index(value = ["inLibrary", "updatedAt"])])
data class NovelEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val description: String,
    val readingStatus: String,
    val createdAt: Long,
    val updatedAt: Long,
    val inLibrary: Boolean = true,
)

@Entity(tableName = "sources")
data class SourceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val baseUrl: String,
    val configuration: String,
    val definitionVersion: Int,
    val enabled: Boolean,
)

@Entity(tableName = "novel_sources", foreignKeys = [
    ForeignKey(entity = NovelEntity::class, parentColumns = ["id"], childColumns = ["novelId"], onDelete = ForeignKey.RESTRICT),
    ForeignKey(entity = SourceEntity::class, parentColumns = ["id"], childColumns = ["sourceId"], onDelete = ForeignKey.RESTRICT),
], indices = [Index("novelId"), Index(value = ["sourceId", "providerId"], unique = true), Index(value = ["id", "novelId"], unique = true)])
data class NovelSourceEntity(
    @PrimaryKey val id: String,
    val novelId: String,
    val sourceId: String,
    val providerId: String,
    val url: String,
    val enabled: Boolean = true,
)

@Entity(tableName = "chapters", foreignKeys = [
    ForeignKey(entity = NovelEntity::class, parentColumns = ["id"], childColumns = ["novelId"], onDelete = ForeignKey.RESTRICT),
], indices = [Index(value = ["novelId", "canonicalOrder"]), Index(value = ["id", "novelId"], unique = true)])
data class ChapterEntity(
    @PrimaryKey val id: String,
    val novelId: String,
    val title: String,
    val numberText: String?,
    val volume: String?,
    val type: String,
    val canonicalOrder: Long,
)

@Entity(tableName = "source_chapters", foreignKeys = [
    ForeignKey(entity = NovelSourceEntity::class, parentColumns = ["id", "novelId"], childColumns = ["novelSourceId", "novelId"], onDelete = ForeignKey.RESTRICT),
], indices = [Index(value = ["novelSourceId", "novelId"]), Index(value = ["novelSourceId", "providerId"], unique = true), Index(value = ["id", "novelId"], unique = true)])
data class SourceChapterEntity(
    @PrimaryKey val id: String,
    val novelId: String,
    val novelSourceId: String,
    val providerId: String,
    val url: String,
    val title: String,
)

/** Confirmed mappings only. Composite foreign keys prohibit linking two different novels. */
@Entity(tableName = "chapter_mappings", foreignKeys = [
    ForeignKey(entity = SourceChapterEntity::class, parentColumns = ["id", "novelId"], childColumns = ["sourceChapterId", "novelId"], onDelete = ForeignKey.RESTRICT),
    ForeignKey(entity = ChapterEntity::class, parentColumns = ["id", "novelId"], childColumns = ["chapterId", "novelId"], onDelete = ForeignKey.RESTRICT),
], indices = [Index(value = ["sourceChapterId", "novelId"]), Index(value = ["chapterId", "novelId"])])
data class ChapterMappingEntity(
    @PrimaryKey val sourceChapterId: String,
    val novelId: String,
    val chapterId: String,
    val method: String,
)

@Entity(tableName = "reading_progress", foreignKeys = [
    ForeignKey(entity = NovelEntity::class, parentColumns = ["id"], childColumns = ["novelId"], onDelete = ForeignKey.RESTRICT),
    ForeignKey(entity = ChapterEntity::class, parentColumns = ["id", "novelId"], childColumns = ["chapterId", "novelId"], onDelete = ForeignKey.RESTRICT),
], indices = [Index(value = ["chapterId", "novelId"])])
data class ReadingProgressEntity(
    @PrimaryKey val novelId: String,
    val chapterId: String,
    val blockAnchor: String,
    val characterOffset: Int,
    val relativeProgress: Double,
    val updatedAt: Long,
)
