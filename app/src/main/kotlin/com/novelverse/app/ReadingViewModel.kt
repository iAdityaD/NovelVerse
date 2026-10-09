package com.novelverse.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelverse.core.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class ReaderState(val chapterId: String = "", val novelId: String = "", val version: ChapterVersion? = null, val position: ReaderPosition? = null, val loading: Boolean = false)

@HiltViewModel
class ReadingViewModel @Inject constructor(private val repository: ReadingRepository) : ViewModel() {
    val schedules=repository.schedules().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val releases=repository.releases().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    fun schedule(hours:Int,novelId:String?=null)=operation{repository.scheduleRefresh(hours,novelId);message.value=if(hours==0)"Scheduled checks paused." else "Refresh schedule saved; Android may delay execution."}
    val sources = repository.sources().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val transfers = repository.transfers().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val downloads = repository.downloads().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    var message = MutableStateFlow<String?>(null); private set
    var busy = MutableStateFlow(false); private set
    var hits = MutableStateFlow<List<SearchHit>>(emptyList()); private set
    var reader = MutableStateFlow(ReaderState()); private set
    var choices = MutableStateFlow<List<ChapterChoice>>(emptyList()); private set
    var downloadProgress = MutableStateFlow(""); private set
    private var downloadJob: Job? = null
    private var loadJob: Job? = null
    fun chapters(novelId: String) = repository.chapters(novelId)
    fun bookmarks(chapterId: String) = repository.bookmarks(chapterId)
    fun clearMessage() { message.value = null }
    private fun operation(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try { block() } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message.value = e.message ?: "Operation failed. Please retry." }
            finally { busy.value = false }
        }
    }
    fun saveSource(json: String) = operation { repository.saveSource(json); message.value = "Source saved. Test its rules before relying on it." }
    fun testSource(json: String, url: String) = operation { message.value = repository.testSource(json,url) }
    fun enabled(source: WebsiteSource) = operation { repository.setSourceEnabled(source.id,!source.enabled) }
    fun search(sourceId: String, query: String) = operation { hits.value = emptyList(); hits.value = repository.search(sourceId,query); if (hits.value.isEmpty()) message.value = "No results from this source. Check the query or search selectors." }
    fun add(hit: SearchHit, novelId: String?, done: (String) -> Unit) = operation { done(repository.importNovel(hit,novelId)) }
    fun refresh(novelId: String) = operation { message.value = "${repository.refresh(novelId)} confirmed new chapters found." }
    fun open(novelId: String, chapterId: String, source: ChapterChoice? = null, force: Boolean = false, scope:String="CHAPTER") {
        loadJob?.cancel()
        val previous = reader.value
        reader.value = if (previous.chapterId == chapterId) previous.copy(loading=true) else ReaderState(chapterId,novelId,loading=true)
        loadJob = viewModelScope.launch {
            try {
                if (source != null && !source.confirmed) repository.confirmMapping(chapterId,source.sourceChapterId)
                val version = repository.loadChapter(chapterId,source?.sourceChapterId,force)
                if(source!=null)repository.chooseSource(chapterId,source.sourceChapterId,scope)
                val saved = repository.position(novelId)?.takeIf { it.chapterId == chapterId }
                val position = if (source != null && saved != null) saved.copy(paragraph=(saved.fraction * version.paragraphs.size).toInt().coerceIn(version.paragraphs.indices),offset=0) else saved
                reader.value = ReaderState(chapterId,novelId,version,position)
                choices.value = repository.chapterChoices(chapterId)
                if (source != null) message.value = "Source selection saved. Position restored approximately."
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { reader.value = reader.value.copy(loading=false); message.value = e.message ?: "Chapter could not be loaded." }
        }
    }
    fun position(paragraph: Int, offset: Int, kind:String="SCROLL") {
        val state = reader.value; val version = state.version ?: return
        val fraction = paragraph.toDouble() / version.paragraphs.size.coerceAtLeast(1)
        val position = ReaderPosition(state.chapterId,paragraph.coerceAtLeast(0),offset.coerceAtLeast(0),fraction.coerceIn(0.0,1.0),kind)
        reader.value = state.copy(position=position)
        viewModelScope.launch { try { repository.savePosition(state.novelId,position) } catch (e: CancellationException) { throw e } catch (_: Exception) { message.value = "Reading position could not be saved." } }
    }
    fun download(chapters:List<CatalogChapter>)=operation{repository.enqueueDownloads(chapters.map{it.id});message.value="Added ${chapters.size} chapters to the durable queue."}
    fun cancelDownloads()=operation{repository.cancelDownloads();message.value="Cancelled. Completed copies retained."}
    fun pauseDownloads()=operation{repository.pauseDownloads()}
    fun resumeDownloads()=operation{repository.resumeDownloads()}
    fun retryDownloads()=operation{repository.retryDownloads()}
    suspend fun exportMetadata()=repository.exportMetadata()
    suspend fun restoreMetadata(json:String)=repository.restoreMetadata(json)
    fun deleteDownload(id: String) = operation { repository.deleteDownload(id); message.value = "Offline pin removed. Clear cache to remove unannotated cached copies." }
    fun clearCache() = operation { repository.clearCache(); message.value = "Temporary content cleared. Offline copies and bookmarked versions retained." }
    fun openBookmark(bookmark:SavedBookmark)=operation {
        val version=repository.bookmarkedVersion(bookmark.id)
        val previous=reader.value
        reader.value=previous.copy(version=version,position=ReaderPosition(version.chapterId,bookmark.paragraph,0,bookmark.paragraph.toDouble()/version.paragraphs.size.coerceAtLeast(1)))
    }
    fun bookmark(note: String) = operation {
        val state=reader.value; val version=state.version ?: return@operation
        repository.bookmark(state.chapterId,version.id,state.position?.paragraph ?: 0,note)
        message.value = "Bookmark saved with this content version."
    }
}
