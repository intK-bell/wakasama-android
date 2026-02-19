package com.example.launcherlock.network

import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val tokenProvider: () -> String
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenProvider().trim()
        val request = chain.request().newBuilder()
            .addHeader("X-App-Token", token)
            .addHeader("Content-Type", "application/json")
            .build()
        return chain.proceed(request)
    }
}
