package com.example.capstone07.remote

import com.example.capstone07.model.Project
import com.example.capstone07.model.ProjectPostResponse
import com.example.capstone07.model.ProjectResponse
import com.example.capstone07.model.ScriptResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ProjectService {

    @POST("api/projects")
    fun postProject(@Body request: Project): Call<ProjectPostResponse>

    @GET("api/projects")
    fun getProjects() : Call<ProjectResponse>
}