package com.novelverse.core.data

import androidx.room.withTransaction
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.novelverse.core.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import com.novelverse.core.model.*
import org.json.JSONObject
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.jsoup.Jsoup
import java.security.MessageDigest
import java.text.Normalizer
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalReadingRepository @Inject constructor(private val db: NovelDatabase, private val transport: HtmlTransport, @ApplicationContext private val context:Context, private val preferences:PreferencesRepository) : ReadingRepository {
    override fun transfers()=dao.transfers().map{rows->rows.map{TransferState(it.chapterId,it.title,it.status,it.attempts,it.error)}}
    override suspend fun enqueueDownloads(chapterIds:List<String>) {
        require(chapterIds.size<=10000)
        db.withTransaction { chapterIds.distinct().forEach{id-> val chapter=dao.chapter(id) ?: error("Chapter not found.");dao.enqueue(TransferEntity(id,chapter.title,"QUEUED",0,null)) } }
        DownloadWorker.enqueue(context)
    }
    override suspend fun pauseDownloads(){dao.stopTransfers("PAUSED");DownloadWorker.cancel(context)}
    override suspend fun cancelDownloads(){dao.stopTransfers("CANCELLED");DownloadWorker.cancel(context)}
    override suspend fun resumeDownloads(){dao.restartTransfers("PAUSED");DownloadWorker.enqueue(context)}
    override suspend fun retryDownloads(){dao.restartTransfers("FAILED");dao.restartTransfers("CANCELLED");DownloadWorker.enqueue(context)}
    override suspend fun exportMetadata():String {
        val p=preferences.preferences.first()
        return JSONObject(MetadataBackup(db).export()).put("preferences",JSONObject().put("theme",p.theme.name).put("readerMode",p.readerMode.name).put("fontSizeSp",p.fontSizeSp).put("libraryLayout",p.libraryLayout.name).put("autoFallback",p.autoFallback).put("localOnly",p.localOnly)).toString()
    }
    override suspend fun restoreMetadata(json:String) {
        val p=JSONObject(json).optJSONObject("preferences")
        val restored=p?.let{UserPreferences(AppTheme.valueOf(it.getString("theme")),ReaderMode.valueOf(it.getString("readerMode")),it.getInt("fontSizeSp"),LibraryLayout.valueOf(it.getString("libraryLayout")),it.optBoolean("autoFallback"),it.optBoolean("localOnly"))}
        require(restored==null||restored.fontSizeSp in 14..36)
        MetadataBackup(db).restore(json)
        if(restored!=null){preferences.setTheme(restored.theme);preferences.setReaderMode(restored.readerMode);preferences.setFontSize(restored.fontSizeSp);preferences.setLibraryLayout(restored.libraryLayout);preferences.setAutoFallback(restored.autoFallback);preferences.setLocalOnly(restored.localOnly)}
        RefreshWorker.start(context)
    }
    override fun schedules()=db.refresh().schedules().map{rows->rows.map{RefreshSchedule(it.novelId,it.intervalHours,it.nextDue,it.lastAttempt,it.lastSuccess,it.error)}}
    override fun releases()=db.refresh().releases().map{rows->rows.map{ReleaseActivity(it.chapterId,it.novelId,it.title,it.discoveredAt)}}
    override suspend fun scheduleRefresh(hours:Int,novelId:String?) {
        require(hours in 0..168){"Use manual-only (0) or an interval from 1 to 168 hours."}
        if(hours==0){if(novelId==null){db.refresh().stopAll();RefreshWorker.stop(context)}else db.refresh().stop(novelId);return}
        val now=System.currentTimeMillis();val interval=java.util.concurrent.TimeUnit.HOURS.toMillis(hours.toLong())
        val novels=db.refresh().eligibleNovels().filter{novelId==null||it.id==novelId}
        novels.forEach{novel->
            if(dao.links(novel.id).isNotEmpty()) db.refresh().schedule(RefreshTargetEntity(novel.id,hours,now+(novel.id.hashCode().toLong() and 0x7fffffff)%interval,0,0,null))
        }
        RefreshWorker.start(context)
    }
    private val dao get() = db.reading()
    private val contentLock = Mutex()
    override fun sources() = dao.sources().map { rows -> rows.map { WebsiteSource(it.id,it.name,it.baseUrl,it.configuration,it.enabled) } }
    override suspend fun saveSource(configuration: String): String = withContext(Dispatchers.IO) {
        val source = CssSource.parse(configuration)
        val previous = dao.source(source.id)
        require(previous == null || previous.baseUrl == source.baseUrl) { "Changing an existing source origin requires a new source ID; link it as an alternative." }
        dao.saveSource(SourceEntity(source.id,source.name,source.baseUrl,configuration,(previous?.definitionVersion ?: 0)+1,true))
        source.id
    }
    override suspend fun setSourceEnabled(id: String, enabled: Boolean) = dao.enabled(id, enabled)
    private suspend fun source(id: String): CssSource {
        val entity = dao.source(id) ?: error("Source not found.")
        require(entity.enabled) { "Source is disabled. Offline copies remain readable." }
        return CssSource.parse(entity.configuration)
    }
    override suspend fun testSource(configuration: String, novelUrl: String): String = withContext(Dispatchers.IO) {
        val source = CssSource.parse(configuration)
        val html = transport.html(source, source.validateUrl(novelUrl))
        val doc = Jsoup.parse(html, novelUrl)
        val title = doc.selectFirst(source.novelTitle)?.text().orEmpty()
        require(title.isNotBlank()) { "Novel title selector returned no text." }
        val count = doc.select(source.catalogItem).size
        require(count > 0) { "Catalog item selector returned no chapters." }
        val first = doc.select(source.catalogItem).first()!!.selectFirst(source.catalogLink)?.absUrl("href").orEmpty()
        require(first.isNotBlank()) { "Chapter link selector returned no URL." }
        val chapter = source.paragraphs(transport.html(source, first), first)
        "Title: $title\nCatalog entries on this page: $count\nFirst chapter: ${chapter.size} paragraphs, ${chapter.sumOf(String::length)} characters.\nSearch must be verified separately."
    }
    override suspend fun search(sourceId: String, query: String): List<SearchHit> = withContext(Dispatchers.IO) {
        require(query.isNotBlank() && query.length <= 200) { "Enter a search query of 1–200 characters." }
        val source = source(sourceId)
        val url = source.validateUrl(source.searchPath).toHttpUrl().newBuilder().addQueryParameter(source.queryParameter, query.trim()).build().toString()
        source.searchResults(transport.html(source, url), url)
    }
    private data class Entry(val url: String, val title: String)
    private suspend fun catalog(source: CssSource, url: String): Triple<String,String,List<Entry>> {
        var next: String? = source.validateUrl(url)
        val visited = mutableSetOf<String>(); val entries = linkedMapOf<String,Entry>(); var title = ""; var author = ""
        while (next != null) {
            require(visited.size < 30 && visited.add(next)) { "Catalog pagination is incomplete or loops. No partial catalog will be committed." }
            val pageUrl = next
            val doc = Jsoup.parse(transport.html(source,pageUrl),pageUrl)
            if (title.isBlank()) title = doc.selectFirst(source.novelTitle)?.text().orEmpty()
            if(author.isBlank()&&source.novelAuthor.isNotBlank())author=doc.selectFirst(source.novelAuthor)?.text().orEmpty()
            doc.select(source.catalogItem).forEach { item ->
                val link = item.selectFirst(source.catalogLink)
                val href = link?.absUrl("href").orEmpty()
                val name = link?.text()?.trim().orEmpty()
                if (href.isNotBlank() && name.isNotBlank()) {
                    val validated = source.validateUrl(href)
                    entries.putIfAbsent(validated,Entry(validated,name))
                }
            }
            require(entries.size <= 10_000) { "Catalog exceeds the current 10,000 chapter safety limit." }
            next = if (source.catalogNext.isBlank()) null else doc.selectFirst(source.catalogNext)?.absUrl("href")?.takeIf { it.isNotBlank() }?.let(source::validateUrl)
        }
        require(entries.isNotEmpty()) { "No chapter links were extracted. Check source rules." }
        return Triple(title,author,entries.values.toList())
    }
    override suspend fun importNovel(hit: SearchHit, linkToNovelId: String?): String = withContext(Dispatchers.IO) {
        val source = source(hit.sourceId)
        val url = source.validateUrl(hit.url)
        val (title,author,entries) = catalog(source,url)
        db.withTransaction {
            val existing = dao.existing(source.id,url)
            if (existing != null) {
                require(linkToNovelId == null || existing.novelId == linkToNovelId) { "This source novel is already linked to another library entry." }
                db.novels().setInLibrary(existing.novelId,true,System.currentTimeMillis())
                return@withTransaction existing.novelId
            }
            val novelId = linkToNovelId ?: UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            if (linkToNovelId == null) db.novels().insert(NovelEntity(novelId,title.ifBlank { hit.title },hit.author.ifBlank{author},"","PLAN_TO_READ",now,now))
            val primary = dao.links(novelId).isEmpty()
            val link = NovelSourceEntity(UUID.randomUUID().toString(),novelId,source.id,url,url)
            dao.insertLink(link)
            reconcile(link,entries,primary)
            novelId
        }
    }
    /** Only exact normalized descriptive titles auto-match. Number-only matches remain unresolved. */
    private fun key(title: String) = Normalizer.normalize(title,Normalizer.Form.NFKC).lowercase().replace(Regex("\\s+")," ").trim()
    private suspend fun reconcile(link: NovelSourceEntity, entries: List<Entry>, createNew: Boolean): Int {
        val chapters = dao.chapterList(link.novelId)
        val keys = chapters.groupBy { key(it.title) }
        var order = (chapters.maxOfOrNull { it.canonicalOrder } ?: -1L) + 1
        var added = 0
        val knownUrls = dao.sourceChapters(link.id).map { it.providerId }.toSet()
        val lastKnownIndex = entries.indexOfLast { it.url in knownUrls }
        entries.forEachIndexed { entryIndex, entry ->
            val previous = dao.sourceChapter(link.id,entry.url)
            if (previous != null) return@forEachIndexed
            val sourceChapter = SourceChapterEntity(UUID.randomUUID().toString(),link.novelId,link.id,entry.url,entry.url,entry.title)
            dao.insertSourceChapter(sourceChapter)
            val candidates = keys[key(entry.title)].orEmpty()
            val descriptive = key(entry.title).replace(Regex("chapter|ch[.]?|volume|vol[.]?|[0-9\\W_]+"), "").length >= 4
            val match = candidates.singleOrNull()?.takeIf { descriptive }
            val canonical = match ?: if (createNew && candidates.isEmpty() && (chapters.isEmpty() || (lastKnownIndex >= 0 && entryIndex > lastKnownIndex))) ChapterEntity(UUID.randomUUID().toString(),link.novelId,entry.title,null,null,"UNCLASSIFIED",order++).also { dao.insertChapter(it); added++ } else null
            if (canonical != null) dao.map(ChapterMappingEntity(sourceChapter.id,link.novelId,canonical.id,if (match == null) "PRIMARY_CATALOG" else "EXACT_TITLE"))
        }
        return added
    }
    override suspend fun refresh(novelId: String): Int = withContext(Dispatchers.IO) {
        val links = dao.links(novelId)
        require(links.isNotEmpty()) { "No linked source. Add one from Explore." }
        var count = 0
        for ((index,link) in links.withIndex()) {
            val source = source(link.sourceId)
            val (_,_,entries) = catalog(source,link.url)
            count += db.withTransaction {
                val known=dao.chapterList(novelId).map{it.id}.toSet()
                val added=reconcile(link,entries,index==0)
                dao.chapterList(novelId).filter{it.id !in known}.forEach{db.refresh().release(ReleaseEventEntity(it.id,novelId,it.title,System.currentTimeMillis(),false))}
                added
            }
        }
        count
    }
    override fun chapters(novelId: String) = dao.chapters(novelId).map { rows -> rows.map { CatalogChapter(it.id,it.novelId,it.title,it.canonicalOrder,it.downloaded) } }
    override suspend fun chapterChoices(chapterId: String): List<ChapterChoice> {
        val chapter = dao.chapter(chapterId) ?: error("Chapter not found.")
        return dao.choices(chapterId).map { ChapterChoice(it.id,it.name,it.title) } + dao.unmapped(chapter.novelId).map { ChapterChoice(it.id,it.name,it.title,false) }
    }
    override suspend fun chooseSource(chapterId:String,sourceChapterId:String,scope:String) {
        require(scope in setOf("CHAPTER","FROM","PRIMARY"))
        val chapter=dao.chapter(chapterId) ?: error("Chapter missing.")
        val link=dao.linkForChapter(sourceChapterId)
        require(link.novelId==chapter.novelId)
        db.policies().save(SourcePolicyEntity("${chapter.novelId}:$scope:${if(scope=="PRIMARY")"" else chapterId}",chapter.novelId,chapterId,link.id,scope))
    }
    private suspend fun preferredSourceChapter(chapterId:String):String? {
        val chapter=dao.chapter(chapterId) ?: return null
        val policies=db.policies().policies(chapter.novelId)
        val order=dao.chapterList(chapter.novelId).associate{it.id to it.canonicalOrder}
        val chosen=policies.firstOrNull{it.scope=="CHAPTER"&&it.chapterId==chapterId}
            ?: policies.filter{it.scope=="FROM"&&(order[it.chapterId] ?: Long.MAX_VALUE)<=chapter.canonicalOrder}.maxByOrNull{order[it.chapterId] ?: -1}
            ?: policies.firstOrNull{it.scope=="PRIMARY"}
        return chosen?.let{policy->dao.choices(chapterId).firstOrNull{dao.linkForChapter(it.id).id==policy.novelSourceId}?.id}
    }
    override suspend fun confirmMapping(chapterId: String, sourceChapterId: String) = db.withTransaction {
        val chapter = dao.chapter(chapterId) ?: error("Chapter not found.")
        val candidate = dao.sourceChapter(sourceChapterId) ?: error("Source chapter not found.")
        require(candidate.novelId == chapter.novelId) { "Chapters must belong to the same novel." }
        dao.map(ChapterMappingEntity(sourceChapterId,chapter.novelId,chapterId,"USER_CONFIRMED"))
    }
    override suspend fun loadChapter(chapterId:String,sourceChapterId:String?,force:Boolean):ChapterVersion {
        try{return loadFrom(chapterId,sourceChapterId,force)}catch(e:CancellationException){throw e}catch(original:Exception){
            if(sourceChapterId!=null||!preferences.preferences.first().autoFallback||preferences.preferences.first().localOnly)throw original
            val selected=preferredSourceChapter(chapterId) ?: dao.choices(chapterId).firstOrNull()?.id
            for(choice in dao.choices(chapterId).filter{it.id!=selected}){
                try{return loadFrom(chapterId,choice.id,force)}catch(e:CancellationException){throw e}catch(_:Exception){}
            }
            throw original
        }
    }
    private suspend fun loadFrom(chapterId: String, sourceChapterId: String?, force: Boolean): ChapterVersion = withContext(Dispatchers.IO) {
        contentLock.withLock {
            val resolvedSource=sourceChapterId ?: preferredSourceChapter(chapterId)
            val cached = if (resolvedSource == null) dao.bestContent(chapterId) else dao.sourceContent(resolvedSource)?.takeIf { it.chapterId == chapterId }
            if (cached != null && !force) return@withLock model(cached)
            val choices = dao.choices(chapterId)
            val selected = if (resolvedSource != null) choices.firstOrNull { it.id == resolvedSource } ?: error("Chapter mapping is not confirmed.") else choices.firstOrNull() ?: error("No enabled, confirmed source. Previously downloaded content remains available.")
            val sourceChapter = dao.sourceChapter(selected.id) ?: error("Chapter source is missing.")
            val link = dao.linkForChapter(selected.id)
            val source = source(link.sourceId)
            var next: String? = sourceChapter.url; val visited = mutableSetOf<String>(); val paragraphs = mutableListOf<String>()
            while (next != null) {
                require(visited.size < 10 && visited.add(next)) { "Chapter continuation loops or exceeds ten pages. Existing copies are preserved." }
                val pageUrl = next
                val html = transport.html(source,pageUrl)
                paragraphs += source.paragraphs(html,pageUrl)
                require(paragraphs.sumOf(String::length) <= 1_000_000) { "Chapter exceeds the safe text limit." }
                next = if (source.chapterNext.isBlank()) null else Jsoup.parse(html,pageUrl).selectFirst(source.chapterNext)?.absUrl("href")?.takeIf { it.isNotBlank() }?.let(source::validateUrl)
            }
            val body = JSONArray(paragraphs).toString()
            val hash = MessageDigest.getInstance("SHA-256").digest(body.toByteArray()).joinToString("") { "%02x".format(it) }
            val existing = dao.sameContent(selected.id,hash)
            if (existing != null) {
                if(dao.blocks(existing.id).isEmpty()) dao.insertBlocks(paragraphs.mapIndexed { i,text -> ContentBlockEntity(existing.id,i,text) })
                return@withLock model(existing)
            }
            val length = paragraphs.sumOf(String::length)
            val warning = when { length < 400 -> "This chapter may be unusually short."; paragraphs.distinct().size < paragraphs.size * 0.7 -> "This extraction contains many repeated paragraphs."; else -> null }
            val content = ContentVersionEntity(UUID.randomUUID().toString(),chapterId,selected.id,hash,length,warning,false,System.currentTimeMillis())
            db.withTransaction {
                dao.insertContent(content)
                dao.insertBlocks(paragraphs.mapIndexed { index,text -> ContentBlockEntity(content.id,index,text) })
            }
            while(dao.cacheBytes()>50L*1024*1024){if(dao.evictOldest(content.id)==0)break}
            model(content)
        }
    }
    private suspend fun model(content: ContentVersionEntity): ChapterVersion {
        val link = dao.linkForChapter(content.sourceChapterId)
        val source = dao.source(link.sourceId)
        val blocks = dao.blocks(content.id)
        return ChapterVersion(content.id,content.chapterId,content.sourceChapterId,source?.name ?: "Archived source",blocks.map { it.text },content.warning,content.offline)
    }
    override suspend fun savePosition(novelId: String, position: ReaderPosition) {
        require(position.paragraph >= 0 && position.offset >= 0 && position.fraction in 0.0..1.0)
        dao.progress(ReadingProgressEntity(novelId,position.chapterId,"${position.paragraph}:${position.kind}",position.offset,position.fraction,System.currentTimeMillis()))
    }
    override suspend fun position(novelId: String) = dao.progress(novelId)?.let { ReaderPosition(it.chapterId,it.blockAnchor.substringBefore(':').toIntOrNull() ?: 0,it.characterOffset,it.relativeProgress,it.blockAnchor.substringAfter(':',"SCROLL")) }
    override suspend fun download(chapterId: String) { val version = loadChapter(chapterId); dao.pin(version.id,true) }
    override fun downloads() = dao.downloads().map { rows -> rows.map { LocalDownload(it.id,it.novelId,it.title,it.source,it.characters) } }
    override suspend fun deleteDownload(versionId: String) { dao.pin(versionId,false) }
    override suspend fun clearCache() = contentLock.withLock { dao.clearCache() }
    override suspend fun bookmark(chapterId: String, versionId: String, paragraph: Int, note: String) {
        val content=dao.content(versionId) ?: error("Content version missing.")
        require(content.chapterId==chapterId)
        require(paragraph in dao.blocks(versionId).indices && note.length <= 5000)
        dao.bookmark(BookmarkEntity(UUID.randomUUID().toString(),chapterId,versionId,paragraph,note))
    }
    override suspend fun bookmarkedVersion(bookmarkId:String):ChapterVersion {
        val bookmark=dao.bookmarkById(bookmarkId) ?: error("Bookmark missing.")
        val content=dao.content(bookmark.versionId) ?: error("Version missing.")
        require(dao.blocks(content.id).isNotEmpty()){ "This backup retained the note but not its website text. Retrieve the chapter before reviewing the original position." }
        return model(content)
    }
    override fun bookmarks(chapterId: String) = dao.bookmarks(chapterId).map { rows -> rows.map { SavedBookmark(it.id,it.chapterId,it.paragraph,it.note) } }
}
