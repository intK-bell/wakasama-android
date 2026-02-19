package com.example.launcherlock.repo

import android.content.Context
import com.example.launcherlock.model.AnswerPayload
import com.example.launcherlock.model.QuestionAnswer

class AnswerUnlockUseCase(
    private val context: Context,
    private val repository: SubmissionRepository
) {
    suspend fun submitAnswersAndUnlock(
        deviceId: String,
        to: String?,
        answers: List<QuestionAnswer>
    ): Boolean {
        if (answers.isEmpty() || answers.any { it.q.isBlank() || it.a.isBlank() }) {
            return false
        }

        val payload = AnswerPayload(deviceId = deviceId, to = to?.trim()?.ifBlank { null }, questions = answers)
        val sent = repository.submitOrQueue(payload)
        if (sent) {
            context.getSharedPreferences("launcher_lock", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("is_locked", false)
                .apply()
        }
        return sent
    }
}
