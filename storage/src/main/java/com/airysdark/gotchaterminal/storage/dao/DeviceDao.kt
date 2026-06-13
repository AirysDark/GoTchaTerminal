package com.airysdark.gotchaterminal.storage.dao

import androidx.room.*
import com.airysdark.gotchaterminal.storage.entities.DeviceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: DeviceEntity)

    @Query("SELECT * FROM devices ORDER BY lastConnected DESC")
    fun getAllDevices(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE isFavorite = 1")
    fun getFavoriteDevices(): Flow<List<DeviceEntity>>

    @Query("UPDATE devices SET isFavorite = :isFavorite WHERE address = :address")
    suspend fun setFavorite(address: String, isFavorite: Boolean)

    @Query("SELECT * FROM devices WHERE address = :address")
    suspend fun getDeviceByAddress(address: String): DeviceEntity?
}
