package com.airysdark.gotchaterminal.wear.sync.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.airysdark.gotchaterminal.core.sync.SyncConstants
import com.airysdark.gotchaterminal.core.sync.WearSyncManager
import com.airysdark.gotchaterminal.storage.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class SyncWorker(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    private val tag = "SyncWorker"
    private val database = AppDatabase.getDatabase(context)
    private val syncManager = WearSyncManager(context)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val sessions = database.captureDao().getUnsyncedSessions()
            if (sessions.isEmpty()) return@withContext Result.success()

            sessions.forEach { session ->
                val packets = database.captureDao().getPacketsForSessionList(session.id)
                
                // Chunk packets if there are many (Wear Data Client limits)
                val chunks = packets.chunked(50)
                chunks.forEachIndexed { index, chunk ->
                    val root = JSONObject()
                    root.put("sessionId", session.id)
                    root.put("sessionName", session.name)
                    root.put("startTime", session.startTime)
                    root.put("endTime", session.endTime)
                    root.put("deviceName", session.deviceName)
                    root.put("deviceAddress", session.deviceAddress)
                    
                    val packetsArray = JSONArray()
                    chunk.forEach { packet ->
                        val pObj = JSONObject().apply {
                            put("type", packet.type)
                            put("svc", packet.serviceUuid)
                            put("char", packet.charUuid)
                            put("data", packet.data)
                            put("ts", packet.timestamp)
                        }
                        packetsArray.put(pObj)
                    }
                    root.put("packets", packetsArray)
                    root.put("chunkIndex", index)
                    root.put("isLast", index == (chunks.size - 1))

                    syncManager.sendData(
                        "${SyncConstants.PATH_SYNC_DATA}/${session.id}_$index",
                        root.toString()
                    )
                }

                database.captureDao().updateSession(session.copy(isSynced = true))
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(tag, "Sync failed", e)
            Result.retry()
        }
    }
}
