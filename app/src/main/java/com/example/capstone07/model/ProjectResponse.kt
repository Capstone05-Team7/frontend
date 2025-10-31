package com.example.capstone07.model

data class ProjectResponse(
    val isSuccess: Boolean,
    val code: String,
    val message: String,
    val result: Project
)