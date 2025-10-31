package com.example.capstone07.model

data class ScriptResponse(
    val isSuccess: Boolean,
    val code: String,
    val message: String,
    val result: Script
)
