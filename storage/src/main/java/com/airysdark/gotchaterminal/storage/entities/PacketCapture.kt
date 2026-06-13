package com.airysdark.gotchaterminal.storage.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "packet_captures")
data class PacketCapture(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val type: String, // READ, WRITE, NOTIF
    val serviceUuid: String,
    val charUuid: String,
    val data: String, // Hex string
    val timestamp: Long = System.currentTimeMillis()
)
