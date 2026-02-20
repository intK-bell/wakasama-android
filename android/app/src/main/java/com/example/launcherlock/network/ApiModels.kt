package com.example.launcherlock.network

data class ApiResponse(
    val ok: Boolean? = null,
    val message: String? = null
)

data class DeviceKeyRegistrationRequest(
    val deviceId: String,
    val publicKeyPem: String,
    val keyAlgorithm: String = "ECDSA_P256_SHA256"
)
