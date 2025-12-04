package com.example.capstone07

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {

    // 애뮬레이터 사용 시
    //private const val BASE_URL = "http://10.0.2.2:8080/"

    // 실제 안드로이드 기기 사용 시
    // PC와 폰이 동일한 wifi에 연결되어 있어야 함
    //private const val BASE_URL = "http://127.0.0.1:8080" // "http://<PC의_실제_IP>:8080/"

    // ec2 서버
    private const val BASE_URL = "http://3.34.163.79:8080/"

    private var retrofit: Retrofit? = null

    fun getClient(): Retrofit {
        return retrofit ?: run {
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS) // 연결 최대 30초
                .readTimeout(30, TimeUnit.SECONDS)    // 읽기 최대 30초
                .writeTimeout(30, TimeUnit.SECONDS)   // 쓰기 최대 30초
                .build()

            Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .also { retrofit = it }
        }
    }
}