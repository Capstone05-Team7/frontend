package com.example.capstone07.remote

import com.example.capstone07.model.Speech
import com.example.capstone07.model.SpeechResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface SpeechService {
    @POST("speech")
    fun getNextHint(@Body request: Speech): Call<SpeechResponse>
}