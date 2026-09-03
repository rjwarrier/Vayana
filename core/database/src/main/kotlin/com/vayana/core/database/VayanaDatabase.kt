package com.vayana.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.vayana.core.database.dao.AnnotationDao
import com.vayana.core.database.dao.BookDao
import com.vayana.core.database.entity.AnnotationEntity
import com.vayana.core.database.entity.BookEntity

/** Bumping this is a real Room migration + a docs/DATABASE_CHANGELOG.md entry, from day one (PROMPT2appbuild.md §2). */
const val DATABASE_VERSION = 4

@Database(entities = [BookEntity::class, AnnotationEntity::class], version = DATABASE_VERSION, exportSchema = true)
abstract class VayanaDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun annotationDao(): AnnotationDao
}
