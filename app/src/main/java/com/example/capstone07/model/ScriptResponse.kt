package com.example.capstone07.model

data class ScriptResponse(
    val isSuccess: Boolean,
    val code: String,
    val message: String,
    val result: List<ScriptResponseData>
)

data class ScriptResponseData(
    val projectId: Int,
    val scripts: List<ScriptResponseFragment>
)

data class ScriptResponseFragment(
    val sentenceId: Int,
    val sentenceOrder: Int,
    val sentenceFragmentContent: String,
    var keyword: String
)

data class ScriptRegisterResponse(
    val isSuccess: Boolean,
    val code: String,
    val message: String
)