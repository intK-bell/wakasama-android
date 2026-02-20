package com.example.launcherlock.network

import com.example.launcherlock.security.DeviceSigningManager
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class AuthInterceptor(
    private val signingManager: DeviceSigningManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val bodyString = requestBodyString(original)
        val deviceId = signingManager.deviceId()
        val timestamp = signingManager.currentEpochSeconds().toString()
        val nonce = signingManager.nonce()
        val bodyHash = sha256Hex(bodyString)
        val canonical = listOf(deviceId, timestamp, nonce, bodyHash).joinToString("\n")
        val signature = signingManager.signCanonical(canonical)
        val token = signingManager.appToken()

        val request = chain.request().newBuilder()
            .addHeader("X-Device-Id", deviceId)
            .addHeader("X-Timestamp", timestamp)
            .addHeader("X-Nonce", nonce)
            .addHeader("X-Signature", signature)
            .addHeader("X-Auth-Version", "v2")
            .addHeader("X-App-Token", token)
            .addHeader("Content-Type", "application/json")
            .build()
        return chain.proceed(request)
    }

    private fun requestBodyString(request: okhttp3.Request): String {
        val body = request.body ?: return ""
        val buffer = Buffer()
        body.writeTo(buffer)
        return buffer.readString(StandardCharsets.UTF_8)
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
