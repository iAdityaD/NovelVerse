package com.novelverse.core.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MigrationTest {
    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), NovelDatabase::class.java
    )

    @Test fun upgradePreservesCanonicalIdentityAndProgress() {
        helper.createDatabase("migration-test", 1).apply {
            execSQL("INSERT INTO novels VALUES ('novel','A retained novel','Author','','READING',1,2,1)")
            execSQL("INSERT INTO chapters VALUES ('chapter','novel','Chapter 1',NULL,NULL,'MAIN',0)")
            execSQL("INSERT INTO reading_progress VALUES ('novel','chapter','4',12,0.5,3)")
            close()
        }
        helper.runMigrationsAndValidate("migration-test", 2, true, MIGRATION_1_2).apply {
            query("SELECT title FROM novels WHERE id='novel'").use {
                check(it.moveToFirst()); assertEquals("A retained novel", it.getString(0))
            }
            query("SELECT blockAnchor,characterOffset FROM reading_progress WHERE novelId='novel'").use {
                check(it.moveToFirst()); assertEquals("4", it.getString(0)); assertEquals(12,it.getInt(1))
            }
            query("PRAGMA foreign_key_check").use { assertEquals(0,it.count) }
            close()
        }
    }
}
