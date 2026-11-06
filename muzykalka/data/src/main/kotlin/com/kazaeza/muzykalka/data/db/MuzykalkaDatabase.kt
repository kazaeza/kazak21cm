package com.kazaeza.muzykalka.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        LibraryItemEntity::class,
        SrsCardEntity::class,
        OrderLogEntity::class,
        AttemptEntity::class,
        SessionEntity::class,
        ExamResultEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class MuzykalkaDatabase : RoomDatabase() {
    abstract fun library(): LibraryDao
    abstract fun srs(): SrsDao
    abstract fun orders(): OrderLogDao
    abstract fun attempts(): AttemptDao
    abstract fun sessions(): SessionDao
    abstract fun exams(): ExamDao
    abstract fun backup(): BackupDao

    companion object {
        const val FILE_NAME = "muzykalka.db"

        /** All migrations, in order. Add here; never fall back to destructive migration. */
        val MIGRATIONS = arrayOf<androidx.room.migration.Migration>()

        fun open(context: Context): MuzykalkaDatabase =
            Room.databaseBuilder(context.applicationContext, MuzykalkaDatabase::class.java, FILE_NAME)
                .addMigrations(*MIGRATIONS)
                .build()

        /** For tests. */
        fun inMemory(context: Context): MuzykalkaDatabase =
            Room.inMemoryDatabaseBuilder(context.applicationContext, MuzykalkaDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }
}
