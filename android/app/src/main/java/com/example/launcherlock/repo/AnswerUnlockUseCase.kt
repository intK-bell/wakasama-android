package com.example.launcherlock.repo

import com.example.launcherlock.model.AnswerPayload
import com.example.launcherlock.model.QuestionAnswer

class AnswerUnlockUseCase(
    private val repository: SubmissionRepository
) {
    suspend fun submitAnswersAndUnlock(
        deviceId: String,
        to: String?,
        answers: List<QuestionAnswer>
    ): SubmissionRepository.SubmitResult {
        if (answers.isEmpty() || answers.any { it.q.isBlank() || it.a.isBlank() }) {
            return SubmissionRepository.SubmitResult.QUEUED
        }

        val payload = AnswerPayload(deviceId = deviceId, to = to?.trim()?.ifBlank { null }, questions = answers)
        return repository.submitOrQueue(payload)
    }
}
