package com.vayana.core.homelibrary.di

import com.vayana.core.diagnostics.DiagnosticCategory
import com.vayana.core.diagnostics.DiagnosticsLogStore
import com.vayana.core.homelibrary.ContentResolverHomeLibrarySource
import com.vayana.core.homelibrary.DataStoreHomeLibraryCheckpointStore
import com.vayana.core.homelibrary.HomeLibraryCheckpointStore
import com.vayana.core.homelibrary.HomeLibrarySource
import com.vayana.core.homelibrary.HomeLibraryStore
import com.vayana.core.homelibrary.HomeLibrarySyncEngine
import com.vayana.core.homelibrary.RoomHomeLibraryStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HomeLibraryModule {
    @Binds
    abstract fun bindSource(impl: ContentResolverHomeLibrarySource): HomeLibrarySource

    @Binds
    abstract fun bindStore(impl: RoomHomeLibraryStore): HomeLibraryStore

    @Binds
    abstract fun bindCheckpointStore(impl: DataStoreHomeLibraryCheckpointStore): HomeLibraryCheckpointStore

    companion object {
        @Provides
        @Singleton
        fun provideEngine(
            source: HomeLibrarySource,
            store: HomeLibraryStore,
            checkpoints: HomeLibraryCheckpointStore,
            logStore: DiagnosticsLogStore,
        ): HomeLibrarySyncEngine = HomeLibrarySyncEngine(
            source = source,
            store = store,
            checkpoints = checkpoints,
            log = { message -> logStore.record(DiagnosticCategory.SYNC, "HomeLibrarySync", message) },
        )
    }
}
