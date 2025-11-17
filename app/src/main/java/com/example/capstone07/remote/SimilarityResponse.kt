package com.example.capstone07.remote

import com.google.gson.annotations.SerializedName

data class SimilarityResponse(
    @SerializedName("most_similar_id")
    val mostSimilarId: String, // 문장 번호 (ID)
    @SerializedName("most_similar_text")
    val mostSimilarText: String, // 유사한 문장 (힌트 텍스트)
    @SerializedName("similarity_score")
    val similarityScore: Float? = null,
    @SerializedName("query_process_time")
    val queryProcessTime: Float? = null
)
