package com.example.capstone07.model

data class ProjectResponse(
    val isSuccess: Boolean,
    val code: String,
    val message: String,
    val result: List<ProjectResponseData>
)

data class ProjectResponseData(
    val id: Int,
    val name: String,
    val description: String,
    val color: String
)