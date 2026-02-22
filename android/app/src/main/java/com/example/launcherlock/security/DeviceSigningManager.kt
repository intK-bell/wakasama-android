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
        if (fromBuildConfig.isNotEmpty()) {
            return normalizeDeviceId(fromBuildConfig)
        }

        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(DEVICE_ID_KEY, null)?.trim().orEmpty()
        if (existing.isNotEmpty()) {
            val normalized = normalizeDeviceId(existing)
            if (normalized != existing) {
                prefs.edit { putString(DEVICE_ID_KEY, normalized) }
            }
            return normalized
        }

        val generated = UUID.randomUUID().toString()
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

    private fun normalizeDeviceId(raw: String): String {
        val trimmed = raw.trim()
        if (isUuidV4(trimmed)) return trimmed

        // Backward compatibility: migrate "app-<uuidv4>" into plain uuidv4.
        val legacyPrefix = "app-"
        if (trimmed.startsWith(legacyPrefix)) {
            val suffix = trimmed.removePrefix(legacyPrefix)
            if (isUuidV4(suffix)) return suffix
        }
        return UUID.randomUUID().toString()
    }

    private fun isUuidV4(value: String): Boolean {
        return Regex(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$"
        ).matches(value)
    }
}
