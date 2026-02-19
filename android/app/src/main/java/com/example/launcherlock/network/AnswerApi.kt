package com.example.launcherlock.network

import com.example.launcherlock.model.AnswerPayload
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AnswerApi {
    @POST("submit-answers")
    suspend fun submitAnswers(@Body payload: AnswerPayload): Response<ApiResponse>
}
