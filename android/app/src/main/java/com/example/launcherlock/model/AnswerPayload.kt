package com.example.launcherlock.model

import java.time.OffsetDateTime
import java.util.UUID

data class QuestionAnswer(
    val q: String,
    val a: String
)

data class AnswerPayload(
    val deviceId: String,
    val to: String? = null,
    val answeredAt: String = OffsetDateTime.now().toString(),
    val idempotencyKey: String = UUID.randomUUID().toString(),
    val questions: List<QuestionAnswer>
)
