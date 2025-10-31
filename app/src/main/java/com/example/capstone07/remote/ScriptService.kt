package com.example.capstone07.remote

import com.example.capstone07.model.Script
import com.example.capstone07.model.ScriptResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface ScriptService {
    @POST("api/scripts")
    fun postscript(@Body request: Script): Call<ScriptResponse>
}