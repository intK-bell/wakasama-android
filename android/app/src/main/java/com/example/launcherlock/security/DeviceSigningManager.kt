package com.example.launcherlock.security

import android.content.Context
import android.provider.Settings
import com.example.launcherlock.BuildConfig
import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.util.Base64
import java.util.UUID
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

class DeviceSigningManager(
    private val appContext: Context
) {
    companion object {
        private const val KEYSTORE_TYPE = "AndroidKeyStore"
        private const val KEY_ALIAS = "launcher_lock_signing_v1"
    }

    fun deviceId(): String {
        val fromBuildConfig = BuildConfig.DEVICE_ID.trim()
        if (fromBuildConfig.isNotEmpty()) return fromBuildConfig

        val androidId = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ANDROID_ID
        )?.trim().orEmpty()
        return androidId.ifBlank { "device-unknown" }
    }

    fun currentEpochSeconds(): Long = System.currentTimeMillis() / 1_000L

    fun nonce(): String = UUID.randomUUID().toString()

    fun publicKeyPem(): String {
        val keyStore = loadKeyStore()
        ensureKeyPair(keyStore)
        val cert = keyStore.getCertificate(KEY_ALIAS)
        val publicKey = cert.publicKey as ECPublicKey
        val encoded = Base64.getMimeEncoder(64, "\n".toByteArray())
            .encodeToString(publicKey.encoded)
        return buildString {
            append("-----BEGIN PUBLIC KEY-----\n")
            append(encoded)
            append("\n-----END PUBLIC KEY-----")
        }
    }

    fun signCanonical(canonical: String): String {
        val keyStore = loadKeyStore()
        ensureKeyPair(keyStore)
        val privateKey = keyStore.getKey(KEY_ALIAS, null) as PrivateKey
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey)
        signature.update(canonical.toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().encodeToString(signature.sign())
    }

    private fun loadKeyStore(): KeyStore {
        return KeyStore.getInstance(KEYSTORE_TYPE).apply { load(null) }
    }

    private fun ensureKeyPair(keyStore: KeyStore) {
        if (keyStore.containsAlias(KEY_ALIAS)) return

        val generator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            KEYSTORE_TYPE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
            .setUserAuthenticationRequired(false)
            .build()
        generator.initialize(spec)
        generator.generateKeyPair()
    }
}
