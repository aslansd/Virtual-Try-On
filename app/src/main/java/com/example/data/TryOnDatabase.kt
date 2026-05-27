package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [TryOnSession::class], version = 1, exportSchema = false)
abstract class TryOnDatabase : RoomDatabase() {
    abstract fun tryOnDao(): TryOnDao

    companion object {
        @Volatile
        private var INSTANCE: TryOnDatabase? = null

        fun getDatabase(context: Context): TryOnDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TryOnDatabase::class.java,
                    "try_on_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
