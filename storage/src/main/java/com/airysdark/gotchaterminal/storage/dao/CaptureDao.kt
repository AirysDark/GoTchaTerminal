package com.airysdark.gotchaterminal.storage.dao

import androidx.room.*
import com.airysdark.gotchaterminal.storage.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CaptureDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionCapture): Long

    @Update
    suspend fun updateSession(session: SessionCapture)

    @Query("SELECT * FROM session_captures WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: Long): SessionCapture?

    @Query("SELECT * FROM session_captures ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<SessionCapture>>

    @Query("SELECT * FROM session_captures WHERE isSynced = 0")
    suspend fun getUnsyncedSessions(): List<SessionCapture>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPacket(packet: PacketCapture)

    @Query("SELECT * FROM packet_captures WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getPacketsForSessionList(sessionId: Long): List<PacketCapture>

    @Query("SELECT * FROM packet_captures WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getPacketsForSession(sessionId: Long): Flow<List<PacketCapture>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAdvertisement(advert: AdvertisementCapture)

    @Query("SELECT * FROM advertisement_captures WHERE isSynced = 0 ORDER BY timestamp DESC")
    suspend fun getUnsyncedAdvertisements(): List<AdvertisementCapture>

    @Query("UPDATE advertisement_captures SET isSynced = 1 WHERE id = :id")
    suspend fun markAdvertisementSynced(id: Long)

    @Query("SELECT * FROM advertisement_captures ORDER BY timestamp DESC")
    fun getAllAdvertisements(): Flow<List<AdvertisementCapture>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChallenge(challenge: ChallengeCapture)

    @Query("SELECT * FROM challenge_captures WHERE isSynced = 0 ORDER BY timestamp DESC")
    suspend fun getUnsyncedChallenges(): List<ChallengeCapture>

    @Query("UPDATE challenge_captures SET isSynced = 1 WHERE id = :id")
    suspend fun markChallengeSynced(id: Long)

    @Query("UPDATE session_captures SET isSynced = 1 WHERE id = :sessionId")
    suspend fun markSessionSynced(sessionId: Long)
}
