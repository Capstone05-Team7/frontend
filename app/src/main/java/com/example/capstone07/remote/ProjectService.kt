package com.example.capstone07.remote

import com.example.capstone07.model.Project
import com.example.capstone07.model.ProjectResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface ProjectService {

    @POST("api/projects")
    fun postProject(@Body request: Project): Call<ProjectResponse>
}