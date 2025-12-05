package com.example.capstone07.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.capstone07.model.ImageCacheEntity

@Dao
interface ImageCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ImageCacheEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM image_cache WHERE id = :id)")
    suspend fun exists(id: Int): Boolean

    @Query("SELECT * FROM image_cache WHERE id = :id")
    suspend fun getById(id: Int): ImageCacheEntity?

    @Delete
    suspend fun delete(entity: ImageCacheEntity)
}