package com.airysdark.gotchaterminal.storage.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val address: String,
    val name: String?,
    val isFavorite: Boolean = false,
    val isSaved: Boolean = true,
    val lastConnected: Long = System.currentTimeMillis(),
    val manufacturerData: String? = null
)
