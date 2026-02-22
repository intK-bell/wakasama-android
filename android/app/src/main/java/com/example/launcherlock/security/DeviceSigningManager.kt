package com.example.launcherlock.security

import android.content.Context
import androidx.core.content.edit
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
        private const val PREFS_NAME = "launcher_lock"
        private const val DEVICE_ID_KEY = "generated_device_id"
    }

    fun deviceId(): String {
        val fromBuildConfig = BuildConfig.DEVICE_ID.trim()
        if (fromBuildConfig.isNotEmpty()) return fromBuildConfig

        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(DEVICE_ID_KEY, null)?.trim().orEmpty()
        if (existing.isNotEmpty()) return existing

        val generated = "app-${UUID.randomUUID()}"
        prefs.edit { putString(DEVICE_ID_KEY, generated) }
        return generated
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
