package com.example.launcherlock.repo

import android.util.Log
import com.example.launcherlock.model.AnswerPayload
import com.example.launcherlock.network.AnswerApi
import com.example.launcherlock.network.DeviceKeyRegistrationRequest
import com.example.launcherlock.queue.PendingSubmissionDao
import com.example.launcherlock.queue.PendingSubmissionEntity
import com.example.launcherlock.security.DeviceSigningManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class SubmissionRepository(
    private val api: AnswerApi,
    private val dao: PendingSubmissionDao,
    private val signingManager: DeviceSigningManager,
    moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
) {
    enum class SubmitResult {
        SUCCESS,
        QUEUED_RATE_LIMITED,
        QUEUED
    }

    private enum class RegisterResult {
        OK,
        RATE_LIMITED,
        FAILED
    }

    companion object {
        private const val TAG = "SubmissionRepository"
    }

    private val adapter = moshi.adapter(AnswerPayload::class.java)

    suspend fun submitOrQueue(payload: AnswerPayload): SubmitResult {
        when (registerDeviceKey(payload.deviceId)) {
            RegisterResult.RATE_LIMITED -> {
                queue(payload)
                return SubmitResult.QUEUED_RATE_LIMITED
            }
            RegisterResult.FAILED -> {
                queue(payload)
                return SubmitResult.QUEUED
            }
            RegisterResult.OK -> Unit
        }
        return try {
            val response = api.submitAnswers(payload)
            val bodyOk = response.body()?.ok
            val accepted = response.isSuccessful && bodyOk != false
            if (accepted) {
                Log.i(TAG, "submitOrQueue success code=${response.code()}")
                SubmitResult.SUCCESS
            } else {
                Log.w(
                    TAG,
                    "submitOrQueue failed code=${response.code()} ok=$bodyOk body=${response.errorBody()?.string()}"
                )
                queue(payload)
                if (response.code() == 429) {
                    SubmitResult.QUEUED_RATE_LIMITED
                } else {
                    SubmitResult.QUEUED
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "submitOrQueue exception: ${e.message}", e)
            queue(payload)
            SubmitResult.QUEUED
        }
    }

    suspend fun flushQueue(batchSize: Int = 20): Int {
        val ready = dao.findReady(System.currentTimeMillis(), batchSize)
        var sent = 0
        ready.forEach { item ->
            val payload = adapter.fromJson(item.payloadJson)
            if (payload == null) {
                dao.delete(item)
                return@forEach
            }

            val ok = try {
                val registerResult = registerDeviceKey(payload.deviceId)
                if (registerResult != RegisterResult.OK) {
                    false
                } else {
                    val response = api.submitAnswers(payload)
                    val bodyOk = response.body()?.ok
                    val accepted = response.isSuccessful && bodyOk != false
                    if (!accepted) {
                        Log.w(
                            TAG,
                            "flushQueue failed item=${item.id} code=${response.code()} ok=$bodyOk body=${response.errorBody()?.string()}"
                        )
                    } else {
                        Log.i(TAG, "flushQueue success item=${item.id} code=${response.code()}")
                    }
                    accepted
                }
            } catch (e: Exception) {
                Log.e(TAG, "flushQueue exception item=${item.id}: ${e.message}", e)
                false
            }

            if (ok) {
                dao.delete(item)
                sent += 1
            } else {
                val nextRetry = computeNextRetry(item.retryCount + 1)
                dao.update(
                    item.copy(
                        retryCount = item.retryCount + 1,
                        nextRetryAtMillis = System.currentTimeMillis() + nextRetry
                    )
                )
            }
        }
        return sent
    }

    private suspend fun queue(payload: AnswerPayload) {
        val json = adapter.toJson(payload)
        dao.insert(PendingSubmissionEntity(payloadJson = json))
    }

    private suspend fun registerDeviceKey(deviceId: String): RegisterResult {
        val localDeviceId = signingManager.deviceId()
        if (deviceId.trim() != localDeviceId.trim()) {
            Log.w(TAG, "registerDeviceKey skipped: payload deviceId mismatch")
            return RegisterResult.FAILED
        }
        return try {
            val response = api.registerDeviceKey(
                DeviceKeyRegistrationRequest(
                    deviceId = localDeviceId,
                    publicKeyPem = signingManager.publicKeyPem()
                )
            )
            val bodyOk = response.body()?.ok
            val accepted = response.isSuccessful && bodyOk != false
            if (!accepted) {
                Log.w(
                    TAG,
                    "registerDeviceKey failed code=${response.code()} ok=$bodyOk body=${response.errorBody()?.string()}"
                )
            }
            when {
                accepted -> RegisterResult.OK
                response.code() == 429 -> RegisterResult.RATE_LIMITED
                else -> RegisterResult.FAILED
            }
        } catch (e: Exception) {
            Log.e(TAG, "registerDeviceKey exception: ${e.message}", e)
            RegisterResult.FAILED
        }
    }

    private fun computeNextRetry(retryCount: Int): Long {
        val base = 30_000L
        val max = 6 * 60 * 60 * 1000L
        val backoff = base shl retryCount.coerceAtMost(8)
        return backoff.coerceAtMost(max)
    }
}
