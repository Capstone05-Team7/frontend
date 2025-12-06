package com.example.capstone07.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "image_cache")
data class ImageCacheEntity(
    @PrimaryKey val id: Int,
    val hash: String,
    val filePath: String
)