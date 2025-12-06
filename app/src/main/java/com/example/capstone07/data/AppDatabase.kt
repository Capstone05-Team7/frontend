package com.example.capstone07.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.capstone07.model.ImageCacheEntity

@Database(entities = [ImageCacheEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {

    abstract fun imageCacheDao(): ImageCacheDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
