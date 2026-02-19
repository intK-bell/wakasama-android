package com.example.launcherlock.network

import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import okhttp3.OkHttpClient
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object NetworkModule {
    fun createApi(baseUrl: String, appTokenProvider: () -> String): AnswerApi {
        val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(appTokenProvider))
            .build()

        return Retrofit.Builder()
            .baseUrl(normalizedBaseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(AnswerApi::class.java)
    }
}
