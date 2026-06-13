package com.airysdark.gotchaterminal.storage.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "advertisement_captures")
data class AdvertisementCapture(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceName: String?,
    val address: String,
    val rssi: Int,
    val serviceUuids: String, // Comma separated
    val manufacturerData: String?, // Hex or JSON
    val isSynced: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
