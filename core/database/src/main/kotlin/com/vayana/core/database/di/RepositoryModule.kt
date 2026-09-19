package com.vayana.core.database.di

import com.vayana.core.database.repository.AnnotationRepository
import com.vayana.core.database.repository.AnnotationRepositoryImpl
import com.vayana.core.database.repository.BookRepository
import com.vayana.core.database.repository.BookRepositoryImpl
import com.vayana.core.database.repository.HighlightReviewRepository
import com.vayana.core.database.repository.HighlightReviewRepositoryImpl
import com.vayana.core.database.repository.ReadingSessionRepository
import com.vayana.core.database.repository.ReadingSessionRepositoryImpl
import com.vayana.core.database.repository.ShelfRepository
import com.vayana.core.database.repository.ShelfRepositoryImpl
import com.vayana.core.database.repository.VocabularyCardRepository
import com.vayana.core.database.repository.VocabularyCardRepositoryImpl
import com.vayana.core.database.repository.WordLookupStatRepository
import com.vayana.core.database.repository.WordLookupStatRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindBookRepository(impl: BookRepositoryImpl): BookRepository

    @Binds
    @Singleton
    abstract fun bindAnnotationRepository(impl: AnnotationRepositoryImpl): AnnotationRepository

    @Binds
    @Singleton
    abstract fun bindWordLookupStatRepository(impl: WordLookupStatRepositoryImpl): WordLookupStatRepository

    @Binds
    @Singleton
    abstract fun bindReadingSessionRepository(impl: ReadingSessionRepositoryImpl): ReadingSessionRepository

    @Binds
    @Singleton
    abstract fun bindShelfRepository(impl: ShelfRepositoryImpl): ShelfRepository

    @Binds
    @Singleton
    abstract fun bindVocabularyCardRepository(impl: VocabularyCardRepositoryImpl): VocabularyCardRepository

    @Binds
    @Singleton
    abstract fun bindHighlightReviewRepository(impl: HighlightReviewRepositoryImpl): HighlightReviewRepository
}
