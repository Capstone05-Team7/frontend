package com.example.capstone07.model

data class ProjectPostResponse(
    val isSuccess: Boolean,
    val code: String,
    val message: String,
    val result: ProjectPostResult
)

data class ProjectPostResult(
    val name: String,
    val description: String,
    val color: String
)

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
    val color: String,
    val isScriptSaved: Boolean
)