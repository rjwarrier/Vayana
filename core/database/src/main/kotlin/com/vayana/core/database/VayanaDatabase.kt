package com.vayana.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.vayana.core.database.dao.AnnotationDao
import com.vayana.core.database.dao.BookDao
import com.vayana.core.database.dao.ReadingSessionDao
import com.vayana.core.database.dao.WordLookupStatDao
import com.vayana.core.database.entity.AnnotationEntity
import com.vayana.core.database.entity.BookEntity
import com.vayana.core.database.entity.ReadingSessionEntity
import com.vayana.core.database.entity.WordLookupStatEntity

/** Bumping this is a real Room migration + a docs/DATABASE_CHANGELOG.md entry, from day one (PROMPT2appbuild.md §2). */
const val DATABASE_VERSION = 8

@Database(
    entities = [BookEntity::class, AnnotationEntity::class, WordLookupStatEntity::class, ReadingSessionEntity::class],
    version = DATABASE_VERSION,
    exportSchema = true,
)
abstract class VayanaDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun annotationDao(): AnnotationDao
    abstract fun wordLookupStatDao(): WordLookupStatDao
    abstract fun readingSessionDao(): ReadingSessionDao
}
