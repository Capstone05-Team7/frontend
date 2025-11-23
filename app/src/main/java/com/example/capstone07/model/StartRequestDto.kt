package com.example.capstone07.model

data class StartRequestDto(
    val projectId: Long
)

data class ApiResponse<T>(
    val status: String,
    val data: T?
)