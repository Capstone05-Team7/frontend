package com.example.capstone07.remote

import com.example.capstone07.model.Script
import com.example.capstone07.model.ScriptResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ScriptService {
    @POST("api/scripts")
    fun postscript(@Body request: Script): Call<ScriptResponse>

    @GET("api/scripts/{projectId}")
    fun getScripts(
        @Path("projectId") projectId: Int
    ): Call<ScriptResponse>
}