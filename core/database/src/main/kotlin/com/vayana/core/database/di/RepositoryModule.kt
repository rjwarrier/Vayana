package com.vayana.core.database.di

import com.vayana.core.database.repository.AnnotationRepository
import com.vayana.core.database.repository.AnnotationRepositoryImpl
import com.vayana.core.database.repository.BookRepository
import com.vayana.core.database.repository.BookRepositoryImpl
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
}
