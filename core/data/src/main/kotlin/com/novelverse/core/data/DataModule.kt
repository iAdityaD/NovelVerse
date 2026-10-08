package com.novelverse.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.novelverse.core.domain.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.UUID
import javax.inject.Singleton

private val Context.preferences by preferencesDataStore(name = "user_preferences")

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): NovelDatabase =
        Room.databaseBuilder(context, NovelDatabase::class.java, "novelverse.db").build()
    @Provides fun dao(database: NovelDatabase): NovelDao = database.novels()
    @Provides @Singleton fun novels(repository: LocalNovelRepository): NovelRepository = repository
    @Provides @Singleton fun store(@ApplicationContext context: Context): DataStore<Preferences> = context.preferences
    @Provides @Singleton fun preferences(repository: LocalPreferencesRepository): PreferencesRepository = repository
    @Provides fun clock(): Clock = Clock { System.currentTimeMillis() }
    @Provides fun ids(): IdGenerator = IdGenerator { UUID.randomUUID().toString() }
    @Provides fun addNovel(repository: NovelRepository, ids: IdGenerator, clock: Clock) = AddManualNovel(repository, ids, clock)
}
