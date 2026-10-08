package com.novelverse.core.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.novelverse.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PersistenceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    @Test fun librarySurvivesReopenAndRemovalPreservesRecord() = runTest {
        val name = "test-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, NovelDatabase::class.java, name).build()
        var db = open()
        try {
            db.novels().insert(NovelEntity("novel", "100% original", "Author", "", "READING", 1, 1))
            db.close()
            db = open()
            assertEquals("100% original", db.novels().observeNovel("novel").first()?.title)
            val repository = LocalNovelRepository(db.novels())
            assertEquals(1, repository.observeLibrary(60, "%").first().size)
            assertEquals(0, repository.observeLibrary(60, "_").first().size)
            repository.setInLibrary("novel", false, 2)
            assertTrue(repository.observeLibrary(60).first().isEmpty())
            assertNotNull(repository.observeNovel("novel").first())
            repository.setInLibrary("novel", true, 3)
            assertEquals(ReadingStatus.READING, repository.observeLibrary(60).first().single().readingStatus)
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun crossNovelProgressIsRejectedByDatabase() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, NovelDatabase::class.java).build()
        try {
            db.novels().insert(NovelEntity("a", "A", "", "", "READING", 1, 1))
            db.novels().insert(NovelEntity("b", "B", "", "", "READING", 1, 1))
            withContext(Dispatchers.IO) {
                val sql = db.openHelper.writableDatabase
                sql.execSQL("INSERT INTO chapters (id,novelId,title,numberText,volume,type,canonicalOrder) VALUES ('ch','a','Chapter',NULL,NULL,'MAIN',1)")
                try {
                    sql.execSQL("INSERT INTO reading_progress (novelId,chapterId,blockAnchor,characterOffset,relativeProgress,updatedAt) VALUES ('b','ch','p',0,0.0,1)")
                    fail("Expected cross-novel foreign-key rejection")
                } catch (_: android.database.sqlite.SQLiteConstraintException) { /* Expected */ }
            }
        } finally { db.close() }
    }
    @Test fun preferencesSurviveStoreRecreation() = runTest {
        val file = File(context.cacheDir, "${UUID.randomUUID()}.preferences_pb")
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            var repository = LocalPreferencesRepository(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
            repository.setTheme(AppTheme.OLED)
            repository.setReaderMode(ReaderMode.PAGINATED)
            repository.setFontSize(26)
            scope.coroutineContext[Job]!!.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            repository = LocalPreferencesRepository(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
            val restored = repository.preferences.first()
            assertEquals(AppTheme.OLED, restored.theme)
            assertEquals(ReaderMode.PAGINATED, restored.readerMode)
            assertEquals(26, restored.fontSizeSp)
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin(); file.delete() }
    }
}
