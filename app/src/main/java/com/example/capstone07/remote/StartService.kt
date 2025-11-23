package com.example.capstone07.remote

import com.example.capstone07.model.ApiResponse
import com.example.capstone07.model.StartRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface StartService {
    @POST("start")
    suspend fun startPresentation(
        @Body request: StartRequestDto
    ): Response<ApiResponse<String>>
}