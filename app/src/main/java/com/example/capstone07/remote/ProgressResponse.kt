package com.example.capstone07.remote

import com.google.gson.annotations.SerializedName

data class ProgressResponse(
    @SerializedName("next_script_id")
    val nextScriptId: String?,
    @SerializedName("similarity_score")
    val similarityScore: String
)
