package com.novelverse.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.novelverse.core.domain.*
import com.novelverse.core.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadingJourneyTest {
    private val definition="""{"schemaVersion":1,"id":"fixture","name":"Fixture","baseUrl":"https://fiction.example","searchPath":"/search","queryParameter":"q","searchItem":"article","searchTitle":"h2","searchLink":"a","novelTitle":"h1","novelAuthor":"","catalogItem":"li","catalogLink":"a","chapterContent":"main","catalogNext":"","chapterNext":""}"""
    private class Preferences:PreferencesRepository {
        override val preferences=MutableStateFlow(UserPreferences())
        override suspend fun setAutoFallback(enabled:Boolean){preferences.value=preferences.value.copy(autoFallback=enabled)}
        override suspend fun setLocalOnly(enabled:Boolean){preferences.value=preferences.value.copy(localOnly=enabled)}
        override suspend fun setTheme(theme:AppTheme){preferences.value=preferences.value.copy(theme=theme)}
        override suspend fun setReaderMode(mode:ReaderMode){preferences.value=preferences.value.copy(readerMode=mode)}
        override suspend fun setFontSize(size:Int){preferences.value=preferences.value.copy(fontSizeSp=size)}
        override suspend fun setLibraryLayout(layout:LibraryLayout){preferences.value=preferences.value.copy(libraryLayout=layout)}
    }
    private class Fixture:HtmlTransport {
        var offline=false
        var extra=false
        override suspend fun html(source:CssSource,url:String):String {
            check(!offline){"Simulated disconnection"}
            return when {
                "/search" in url->"<article><h2>Fixture Novel</h2><a href='/novel'>Open</a></article>"
                url.endsWith("/novel")->"<h1>Fixture Novel</h1><ul><li><a href='/one'>Chapter 1: Beginning</a></li><li><a href='/two'>Chapter 2: Crossing</a></li>${if(extra)"<li><a href='/three'>Chapter 3: Arrival</a></li>" else ""}</ul>"
                else->"<main><p>${"Permitted fixture prose. ".repeat(30)}</p><p>The final paragraph.</p></main>"
            }
        }
    }
    @Test fun searchImportReadOfflineRefreshAndBackupUseOneCanonicalNovel()=runTest {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,NovelDatabase::class.java).build()
        val restoredDb=Room.inMemoryDatabaseBuilder(context,NovelDatabase::class.java).build()
        try {
            val transport=Fixture();val repository=LocalReadingRepository(db,transport,context,Preferences())
            repository.saveSource(definition)
            val hit=repository.search("fixture","Fixture").single()
            val novel=repository.importNovel(hit)
            assertEquals(novel,repository.importNovel(hit))
            val chapters=repository.chapters(novel).first();assertEquals(2,chapters.size)
            val version=repository.loadChapter(chapters[0].id)
            assertEquals(2,version.paragraphs.size)
            repository.bookmark(chapters[0].id,version.id,1,"A note")
            repository.savePosition(novel,ReaderPosition(chapters[0].id,1,0,0.5))
            repository.download(chapters[0].id)
            transport.offline=true
            assertTrue(repository.loadChapter(chapters[0].id).offline)
            repository.clearCache()
            assertEquals(version.id,repository.loadChapter(chapters[0].id).id)
            assertEquals(1,repository.position(novel)?.paragraph)
            transport.offline=false;transport.extra=true
            assertEquals(1,repository.refresh(novel));assertEquals(0,repository.refresh(novel))
            assertEquals(1,repository.releases().first().size)
            val backup=MetadataBackup(db).export()
            MetadataBackup(restoredDb).restore(backup)
            MetadataBackup(restoredDb).restore(backup)
            assertEquals(1,restoredDb.novels().observeLibrary(60,"%").first().size)
            assertEquals(3,restoredDb.reading().chapters(novel).first().size)
            assertTrue(restoredDb.reading().downloads().first().isEmpty())
            assertEquals(1,restoredDb.reading().bookmarks(chapters[0].id).first().size)
        }finally{db.close();restoredDb.close()}
    }
}
