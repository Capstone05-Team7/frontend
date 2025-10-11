package com.example.capstone07

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object NetworkModule {

    // 애뮬레이터 사용 시
    private const val BASE_URL = "http://10.0.2.2:8080/"

    // 실제 안드로이드 기기 사용 시
    // PC와 폰이 동일한 wifi에 연결되어 있어야 함
    // private const val BASE_URL = "http://<PC의_실제_IP>:8080/"

    private var retrofit: Retrofit? = null

    fun getClient(): Retrofit {
        return retrofit ?: Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build().also { retrofit = it }
    }
}