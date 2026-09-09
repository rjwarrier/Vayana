package com.vayana.core.database.di

import android.content.Context
import androidx.room.Room
import com.vayana.core.database.ALL_MIGRATIONS
import com.vayana.core.database.VayanaDatabase
import com.vayana.core.database.dao.AnnotationDao
import com.vayana.core.database.dao.BookAliasDao
import com.vayana.core.database.dao.BookDao
import com.vayana.core.database.dao.ReadingSessionDao
import com.vayana.core.database.dao.ShelfDao
import com.vayana.core.database.dao.TombstoneDao
import com.vayana.core.database.dao.VocabularyCardDao
import com.vayana.core.database.dao.WordLookupStatDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideVayanaDatabase(@ApplicationContext context: Context): VayanaDatabase =
        Room.databaseBuilder(context, VayanaDatabase::class.java, "vayana.db")
            .addMigrations(*ALL_MIGRATIONS)
            .build()

    @Provides
    fun provideBookDao(database: VayanaDatabase): BookDao = database.bookDao()

    @Provides
    fun provideAnnotationDao(database: VayanaDatabase): AnnotationDao = database.annotationDao()

    @Provides
    fun provideWordLookupStatDao(database: VayanaDatabase): WordLookupStatDao = database.wordLookupStatDao()

    @Provides
    fun provideReadingSessionDao(database: VayanaDatabase): ReadingSessionDao = database.readingSessionDao()

    @Provides
    fun provideShelfDao(database: VayanaDatabase): ShelfDao = database.shelfDao()

    @Provides
    fun provideVocabularyCardDao(database: VayanaDatabase): VocabularyCardDao = database.vocabularyCardDao()

    @Provides
    fun provideBookAliasDao(database: VayanaDatabase): BookAliasDao = database.bookAliasDao()

    @Provides
    fun provideTombstoneDao(database: VayanaDatabase): TombstoneDao = database.tombstoneDao()
}
