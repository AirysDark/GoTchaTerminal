package com.airysdark.gotchaterminal.storage.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "session_captures")
data class SessionCapture(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val deviceName: String?,
    val deviceAddress: String?,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val isSynced: Boolean = false
)
