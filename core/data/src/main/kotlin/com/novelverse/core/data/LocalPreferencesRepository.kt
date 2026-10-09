package com.novelverse.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.novelverse.core.domain.PreferencesRepository
import com.novelverse.core.model.*
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class LocalPreferencesRepository @Inject constructor(private val store: DataStore<Preferences>) : PreferencesRepository {
    private val fallbackKey=booleanPreferencesKey("auto_fallback")
    private val localOnlyKey=booleanPreferencesKey("local_only")
    private val themeKey = stringPreferencesKey("theme")
    private val modeKey = stringPreferencesKey("reader_mode")
    private val fontKey = intPreferencesKey("font_size_sp")
    private val layoutKey = stringPreferencesKey("library_layout")
    // Storage failures propagate to visible UI error states; never silently reset durable preferences.
    override val preferences = store.data.map { values ->
        UserPreferences(
            AppTheme.entries.firstOrNull { it.name == values[themeKey] } ?: AppTheme.SYSTEM,
            ReaderMode.entries.firstOrNull { it.name == values[modeKey] } ?: ReaderMode.CONTINUOUS,
            (values[fontKey] ?: 20).coerceIn(14, 36),
            LibraryLayout.entries.firstOrNull { it.name == values[layoutKey] } ?: LibraryLayout.GRID,
            values[fallbackKey] ?: false,
            values[localOnlyKey] ?: false,
        )
    }
    override suspend fun setAutoFallback(enabled:Boolean){store.edit{it[fallbackKey]=enabled}}
    override suspend fun setLocalOnly(enabled:Boolean){store.edit{it[localOnlyKey]=enabled}}
    override suspend fun setTheme(theme: AppTheme) { store.edit { it[themeKey] = theme.name } }
    override suspend fun setReaderMode(mode: ReaderMode) { store.edit { it[modeKey] = mode.name } }
    override suspend fun setFontSize(size: Int) {
        require(size in 14..36) { "Font size must be between 14 and 36 sp." }
        store.edit { it[fontKey] = size }
    }
    override suspend fun setLibraryLayout(layout: LibraryLayout) { store.edit { it[layoutKey] = layout.name } }
}
