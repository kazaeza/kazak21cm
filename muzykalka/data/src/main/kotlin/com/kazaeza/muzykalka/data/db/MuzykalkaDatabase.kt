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
    version = 2,
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
        val MIGRATIONS = arrayOf<androidx.room.migration.Migration>(object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE attempt ADD COLUMN focusRound INTEGER")
                db.execSQL("ALTER TABLE attempt ADD COLUMN replayCount INTEGER")
                db.execSQL("ALTER TABLE session ADD COLUMN training TEXT")
                db.execSQL("ALTER TABLE session ADD COLUMN songSectionKey TEXT")
                db.execSQL("ALTER TABLE session ADD COLUMN focusVariant TEXT")
                db.execSQL("ALTER TABLE session ADD COLUMN focusPair TEXT")
                db.execSQL("ALTER TABLE session ADD COLUMN focusLevel INTEGER")
                db.execSQL("ALTER TABLE session ADD COLUMN focusReason TEXT")
                db.execSQL("ALTER TABLE session ADD COLUMN focusParentId INTEGER")
                db.execSQL("ALTER TABLE session ADD COLUMN focusChord TEXT")
                db.execSQL("ALTER TABLE session ADD COLUMN focusStatus TEXT")
            }
        })

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
