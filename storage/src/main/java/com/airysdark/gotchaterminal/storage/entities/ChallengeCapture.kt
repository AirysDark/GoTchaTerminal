package com.airysdark.gotchaterminal.storage.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "challenge_captures")
data class ChallengeCapture(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val challengeData: String,
    val responseData: String?,
    val isSuccessful: Boolean,
    val isSynced: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
