package com.example.capstone07.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "image_cache")
data class ImageCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val projectId: Int,
    val sentenceId: Int,
    val filePath: String // bitmap에 대한 path
)