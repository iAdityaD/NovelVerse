package com.novelverse.core.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "content_versions", foreignKeys = [
    ForeignKey(entity = ChapterEntity::class, parentColumns = ["id"], childColumns = ["chapterId"], onDelete = ForeignKey.RESTRICT),
    ForeignKey(entity = SourceChapterEntity::class, parentColumns = ["id"], childColumns = ["sourceChapterId"], onDelete = ForeignKey.RESTRICT),
], indices = [Index("chapterId"), Index(value = ["sourceChapterId", "hash"], unique = true)])
data class ContentVersionEntity(@PrimaryKey val id: String, val chapterId: String, val sourceChapterId: String,
    val hash: String, val characters: Int, val warning: String?, val offline: Boolean, val fetchedAt: Long)

@Entity(tableName = "content_blocks", primaryKeys=["versionId","ordinal"], foreignKeys=[
    ForeignKey(entity=ContentVersionEntity::class,parentColumns=["id"],childColumns=["versionId"],onDelete=ForeignKey.CASCADE)
])
data class ContentBlockEntity(val versionId:String,val ordinal:Int,val text:String)

@Entity(tableName = "bookmarks", foreignKeys = [
    ForeignKey(entity = ContentVersionEntity::class, parentColumns = ["id"], childColumns = ["versionId"], onDelete = ForeignKey.RESTRICT),
], indices = [Index("versionId"), Index("chapterId")])
data class BookmarkEntity(@PrimaryKey val id: String, val chapterId: String, val versionId: String, val paragraph: Int, val note: String)

@Entity(tableName="transfer_tasks",foreignKeys=[ForeignKey(entity=ChapterEntity::class,parentColumns=["id"],childColumns=["chapterId"],onDelete=ForeignKey.RESTRICT)])
data class TransferEntity(@PrimaryKey val chapterId:String,val title:String,val status:String,val attempts:Int,val error:String?)

data class ChapterRow(val id: String, val novelId: String, val title: String, val canonicalOrder: Long, val downloaded: Boolean)
data class DownloadRow(val id: String, val novelId: String, val title: String, val source: String, val characters: Int)
data class ChoiceRow(val id: String, val title: String, val name: String)

@Dao
interface ReadingDao {
    @Query("SELECT * FROM transfer_tasks ORDER BY chapterId") fun transfers():Flow<List<TransferEntity>>
    @Query("SELECT * FROM transfer_tasks WHERE status IN ('QUEUED','RUNNING') LIMIT 20") suspend fun pendingTransfers():List<TransferEntity>
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun enqueue(task:TransferEntity)
    @Upsert suspend fun updateTransfer(task:TransferEntity)
    @Query("UPDATE transfer_tasks SET status=:newStatus WHERE status IN ('QUEUED','RUNNING')") suspend fun stopTransfers(newStatus:String)
    @Query("UPDATE transfer_tasks SET status='QUEUED',attempts=0,error=NULL WHERE status=:oldStatus") suspend fun restartTransfers(oldStatus:String)

    @Query("SELECT * FROM sources ORDER BY name") fun sources(): Flow<List<SourceEntity>>
    @Query("SELECT * FROM sources WHERE id=:id") suspend fun source(id: String): SourceEntity?
    @Upsert suspend fun saveSource(source: SourceEntity)
    @Query("UPDATE sources SET enabled=:enabled WHERE id=:id") suspend fun enabled(id: String, enabled: Boolean)
    @Query("SELECT * FROM novel_sources WHERE sourceId=:sourceId AND providerId=:url") suspend fun existing(sourceId: String, url: String): NovelSourceEntity?
    @Query("SELECT * FROM novel_sources WHERE novelId=:novelId AND enabled=1") suspend fun links(novelId: String): List<NovelSourceEntity>
    @Insert suspend fun insertLink(link: NovelSourceEntity)
    @Query("SELECT * FROM chapters WHERE novelId=:novelId ORDER BY canonicalOrder LIMIT 10000") suspend fun chapterList(novelId: String): List<ChapterEntity>
    @Query("SELECT * FROM source_chapters WHERE novelSourceId=:link") suspend fun sourceChapters(link: String): List<SourceChapterEntity>
    @Query("SELECT cs.id,cs.title,s.name FROM source_chapters cs JOIN novel_sources ns ON ns.id=cs.novelSourceId JOIN sources s ON s.id=ns.sourceId WHERE cs.novelId=:novelId AND s.enabled=1 AND cs.id NOT IN (SELECT sourceChapterId FROM chapter_mappings) LIMIT 10000")
    suspend fun unmapped(novelId: String): List<ChoiceRow>
    @Query("SELECT * FROM chapters WHERE id=:id") suspend fun chapter(id: String): ChapterEntity?
    @Insert suspend fun insertChapter(chapter: ChapterEntity)
    @Query("SELECT * FROM source_chapters WHERE novelSourceId=:link AND providerId=:url") suspend fun sourceChapter(link: String, url: String): SourceChapterEntity?
    @Query("SELECT * FROM source_chapters WHERE id=:id") suspend fun sourceChapter(id: String): SourceChapterEntity?
    @Insert suspend fun insertSourceChapter(chapter: SourceChapterEntity)
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun map(mapping: ChapterMappingEntity)
    @Query("SELECT cs.id, cs.title, s.name FROM source_chapters cs JOIN chapter_mappings m ON m.sourceChapterId=cs.id JOIN novel_sources ns ON ns.id=cs.novelSourceId JOIN sources s ON s.id=ns.sourceId WHERE m.chapterId=:chapterId AND ns.enabled=1 AND s.enabled=1")
    suspend fun choices(chapterId: String): List<ChoiceRow>
    @Query("SELECT ns.* FROM novel_sources ns JOIN source_chapters cs ON cs.novelSourceId=ns.id WHERE cs.id=:id") suspend fun linkForChapter(id: String): NovelSourceEntity
    @Query("SELECT c.id,c.novelId,c.title,c.canonicalOrder,EXISTS(SELECT 1 FROM content_versions v WHERE v.chapterId=c.id AND v.offline=1) AS downloaded FROM chapters c WHERE c.novelId=:id ORDER BY c.canonicalOrder LIMIT 10000")
    fun chapters(id: String): Flow<List<ChapterRow>>
    @Query("SELECT * FROM content_versions WHERE chapterId=:id AND EXISTS(SELECT 1 FROM content_blocks WHERE versionId=content_versions.id) ORDER BY offline DESC, fetchedAt DESC LIMIT 1") suspend fun bestContent(id: String): ContentVersionEntity?
    @Query("SELECT * FROM content_versions WHERE sourceChapterId=:id AND EXISTS(SELECT 1 FROM content_blocks WHERE versionId=content_versions.id) ORDER BY offline DESC, fetchedAt DESC LIMIT 1") suspend fun sourceContent(id: String): ContentVersionEntity?
    @Query("SELECT * FROM content_versions WHERE sourceChapterId=:sourceChapterId AND hash=:hash") suspend fun sameContent(sourceChapterId: String, hash: String): ContentVersionEntity?
    @Insert suspend fun insertBlocks(blocks:List<ContentBlockEntity>)
    @Query("SELECT * FROM content_blocks WHERE versionId=:id ORDER BY ordinal") suspend fun blocks(id:String):List<ContentBlockEntity>
    @Insert suspend fun insertContent(content: ContentVersionEntity)
    @Query("UPDATE content_versions SET offline=:offline WHERE id=:id") suspend fun pin(id: String, offline: Boolean)
    @Query("SELECT v.id,c.novelId,c.title,s.name AS source,v.characters FROM content_versions v JOIN chapters c ON c.id=v.chapterId JOIN source_chapters cs ON cs.id=v.sourceChapterId JOIN novel_sources ns ON ns.id=cs.novelSourceId JOIN sources s ON s.id=ns.sourceId WHERE v.offline=1 ORDER BY c.novelId,c.canonicalOrder")
    fun downloads(): Flow<List<DownloadRow>>
    @Query("DELETE FROM content_versions WHERE offline=0 AND id NOT IN (SELECT versionId FROM bookmarks)") suspend fun clearCache()
    @Query("SELECT * FROM reading_progress WHERE novelId=:id") suspend fun progress(id: String): ReadingProgressEntity?
    @Upsert suspend fun progress(progress: ReadingProgressEntity)
    @Insert suspend fun bookmark(bookmark: BookmarkEntity)
    @Query("SELECT * FROM bookmarks WHERE chapterId=:id ORDER BY paragraph") fun bookmarks(id: String): Flow<List<BookmarkEntity>>
}

val MIGRATION_1_2 = object : Migration(1,2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS source_policies (id TEXT NOT NULL PRIMARY KEY,novelId TEXT NOT NULL,chapterId TEXT NOT NULL,novelSourceId TEXT NOT NULL,scope TEXT NOT NULL,FOREIGN KEY(novelId) REFERENCES novels(id) ON UPDATE NO ACTION ON DELETE RESTRICT,FOREIGN KEY(novelSourceId) REFERENCES novel_sources(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_source_policies_novelId ON source_policies(novelId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_source_policies_novelSourceId ON source_policies(novelSourceId)")
        db.execSQL("CREATE TABLE IF NOT EXISTS refresh_targets (novelId TEXT NOT NULL PRIMARY KEY,intervalHours INTEGER NOT NULL,nextDue INTEGER NOT NULL,lastAttempt INTEGER NOT NULL,lastSuccess INTEGER NOT NULL,error TEXT,FOREIGN KEY(novelId) REFERENCES novels(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_refresh_targets_nextDue ON refresh_targets(nextDue)")
        db.execSQL("CREATE TABLE IF NOT EXISTS release_events (chapterId TEXT NOT NULL PRIMARY KEY,novelId TEXT NOT NULL,title TEXT NOT NULL,discoveredAt INTEGER NOT NULL,notified INTEGER NOT NULL,FOREIGN KEY(chapterId) REFERENCES chapters(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_release_events_novelId ON release_events(novelId)")
        db.execSQL("CREATE TABLE IF NOT EXISTS transfer_tasks (chapterId TEXT NOT NULL PRIMARY KEY,title TEXT NOT NULL,status TEXT NOT NULL,attempts INTEGER NOT NULL,error TEXT,FOREIGN KEY(chapterId) REFERENCES chapters(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS content_versions (id TEXT NOT NULL PRIMARY KEY, chapterId TEXT NOT NULL, sourceChapterId TEXT NOT NULL, hash TEXT NOT NULL, characters INTEGER NOT NULL, warning TEXT, offline INTEGER NOT NULL, fetchedAt INTEGER NOT NULL, FOREIGN KEY(chapterId) REFERENCES chapters(id) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(sourceChapterId) REFERENCES source_chapters(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_content_versions_chapterId ON content_versions(chapterId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_content_versions_sourceChapterId_hash ON content_versions(sourceChapterId,hash)")
        db.execSQL("CREATE TABLE IF NOT EXISTS content_blocks (versionId TEXT NOT NULL, ordinal INTEGER NOT NULL, text TEXT NOT NULL, PRIMARY KEY(versionId,ordinal), FOREIGN KEY(versionId) REFERENCES content_versions(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE TABLE IF NOT EXISTS bookmarks (id TEXT NOT NULL PRIMARY KEY, chapterId TEXT NOT NULL, versionId TEXT NOT NULL, paragraph INTEGER NOT NULL, note TEXT NOT NULL, FOREIGN KEY(versionId) REFERENCES content_versions(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_bookmarks_versionId ON bookmarks(versionId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_bookmarks_chapterId ON bookmarks(chapterId)")
    }
}
