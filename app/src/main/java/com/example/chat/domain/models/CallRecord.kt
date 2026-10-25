package com.example.chat.domain.models

import java.time.Instant

/** Exact port of the CallRecord model in lib/domain/models/other_models.dart. */

enum class CallType { voice, video }

enum class CallDirection { incoming, outgoing, missed }

data class CallRecord(
    val id: String,
    val callerId: String,
    val participantIds: List<String>,
    val type: CallType,
    val direction: CallDirection,
    val timestamp: Instant,
    val durationSeconds: Long,
    val isEncrypted: Boolean = true,
)
