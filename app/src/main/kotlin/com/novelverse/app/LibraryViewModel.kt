package com.novelverse.app

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelverse.core.domain.*
import com.novelverse.core.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryState(val loading: Boolean = true, val novels: List<Novel> = emptyList(), val error: String? = null)
data class PreferenceState(val loading: Boolean = true, val value: UserPreferences = UserPreferences(), val error: String? = null)
sealed interface AppEvent {
    data class Added(val id: String) : AppEvent
    data class Message(val text: String) : AppEvent
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: NovelRepository,
    private val preferencesRepository: PreferencesRepository,
    private val addManualNovel: AddManualNovel,
    private val clock: Clock,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    val query = savedState.getStateFlow("query", "")
    private val limit = MutableStateFlow(60)
    private val retry = MutableStateFlow(0)
    val archived = retry.flatMapLatest {
        repository.observeArchived(1000).map { LibraryState(false, it) }
            .catch { emit(LibraryState(false, error = "Removed novels could not be loaded.")) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryState())
    val library = combine(query, limit, retry) { q, count, _ -> q to count }.flatMapLatest { (q, count) ->
        repository.observeLibrary(count, q).map { LibraryState(false, it) }
            .catch { emit(LibraryState(false, error = "Library could not be loaded. Your data has not been deleted.")) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryState())
    val preferences = retry.flatMapLatest {
        preferencesRepository.preferences.map { PreferenceState(false, it) }
            .catch { emit(PreferenceState(false, error = "Preferences could not be loaded. Try again.")) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PreferenceState())
    private val eventChannel = Channel<AppEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()
    private val _formError = MutableStateFlow<String?>(null)
    val formError = _formError.asStateFlow()

    fun search(value: String) { savedState["query"] = value; limit.value = 60 }
    fun loadMore() { limit.value = (limit.value + 60).coerceAtMost(1000) }
    fun retry() { retry.value++ }
    fun clearFormError() { _formError.value = null }
    fun add(title: String, author: String, description: String) {
        if (_saving.value) return
        _saving.value = true
        _formError.value = null
        viewModelScope.launch {
            try { eventChannel.send(AppEvent.Added(addManualNovel(title, author, description))) }
            catch (e: CancellationException) { throw e }
            catch (e: IllegalArgumentException) { _formError.value = e.message }
            catch (_: Exception) { _formError.value = "Could not save this novel. Check available storage and try again." }
            finally { _saving.value = false }
        }
    }
    fun theme(value: AppTheme) = perform { preferencesRepository.setTheme(value) }
    fun mode(value: ReaderMode) = perform { preferencesRepository.setReaderMode(value) }
    fun font(value: Int) = perform { preferencesRepository.setFontSize(value) }
    fun layout(value: LibraryLayout) = perform { preferencesRepository.setLibraryLayout(value) }
    private fun perform(block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { eventChannel.send(AppEvent.Message("Change could not be saved. Please try again.")) }
        }
    }
}

data class DetailState(val loading: Boolean = true, val novel: Novel? = null, val error: String? = null)

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: NovelRepository,
    private val clock: Clock,
) : ViewModel() {
    private val id: String = checkNotNull(savedState["id"])
    private val retry = MutableStateFlow(0)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val state = retry.flatMapLatest {
        repository.observeNovel(id).map { DetailState(false, it) }
            .catch { emit(DetailState(false, error = "Could not load this novel.")) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailState())
    private val _writeError = MutableStateFlow<String?>(null)
    val writeError = _writeError.asStateFlow()
    fun retry() { retry.value++ }
    fun status(value: ReadingStatus) = write { repository.updateStatus(id, value, clock.nowMillis()) }
    fun inLibrary(value: Boolean) = write { repository.setInLibrary(id, value, clock.nowMillis()) }
    private fun write(block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block(); _writeError.value = null }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { _writeError.value = "Could not save your change. Please try again." }
        }
    }
}
