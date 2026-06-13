package com.airysdark.gotchaterminal.sync

import android.util.Log
import com.airysdark.gotchaterminal.core.sync.SyncConstants
import com.airysdark.gotchaterminal.storage.AppDatabase
import com.airysdark.gotchaterminal.storage.entities.PacketCapture
import com.airysdark.gotchaterminal.storage.entities.SessionCapture
import com.google.android.gms.wearable.*
import kotlinx.coroutines.*
import org.json.JSONObject

class WearDataListenerService : WearableListenerService() {
    private val TAG = "WearDataListener"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database by lazy { AppDatabase.getDatabase(this) }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                val path = event.dataItem.uri.path ?: return@forEach
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                val json = dataMap.getString(SyncConstants.KEY_DATA_JSON) ?: return@forEach

                scope.launch {
                    handleIncomingData(path, json)
                }
            }
        }
    }

    private suspend fun handleIncomingData(path: String, json: String) {
        try {
            val obj = JSONObject(json)
            when {
                path.startsWith(SyncConstants.PATH_SESSION_CAPTURE) -> {
                    val session = SessionCapture(
                        name = obj.getString("name"),
                        deviceName = obj.optString("deviceName"),
                        deviceAddress = obj.optString("deviceAddress"),
                        startTime = obj.getLong("startTime"),
                        endTime = obj.optLong("endTime"),
                        isSynced = true
                    )
                    database.captureDao().insertSession(session)
                    Log.d(TAG, "Synced Session: ${session.name}")
                }
                path.startsWith(SyncConstants.PATH_PACKET_CAPTURE) -> {
                    val sessionId = obj.getLong("sessionId")
                    val packetsArray = obj.getJSONArray("packets")
                    
                    for (i in 0 until packetsArray.length()) {
                        val p = packetsArray.getJSONObject(i)
                        database.captureDao().insertPacket(
                            PacketCapture(
                                sessionId = sessionId,
                                type = p.getString("type"),
                                serviceUuid = p.getString("serviceUuid"),
                                charUuid = p.getString("charUuid"),
                                data = p.getString("data"),
                                timestamp = p.getLong("timestamp")
                            )
                        )
                    }
                    Log.d(TAG, "Synced chunk of ${packetsArray.length()} packets for session $sessionId")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing synced data from $path", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
