package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CategoryNodeEntity::class,
        QuestionEntity::class,
        QuestionNodeCrossRef::class,
        UserProgressEntity::class,
        ClozeCardEntity::class,
        ExamSessionEntity::class,
        InstalledPackEntity::class,
        MediaBlobEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MedoDatabase : RoomDatabase() {
    abstract fun medoDao(): MedoDao

    companion object {
        @Volatile
        private var INSTANCE: MedoDatabase? = null

        fun getInstance(context: Context): MedoDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MedoDatabase::class.java,
                    "medo_next_clinical.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
