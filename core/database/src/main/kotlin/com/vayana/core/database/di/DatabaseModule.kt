package com.vayana.core.database.di

import android.content.Context
import androidx.room.Room
import com.vayana.core.database.VayanaDatabase
import com.vayana.core.database.dao.BookDao
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
        Room.databaseBuilder(context, VayanaDatabase::class.java, "vayana.db").build()

    @Provides
    fun provideBookDao(database: VayanaDatabase): BookDao = database.bookDao()
}
