package com.airysdark.gotchaterminal.core.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * WearSyncManager handles synchronization between Phone and Wear OS.
 */
class WearSyncManager(context: Context) {
    private val tag = "WearSyncManager"
    private val dataClient: DataClient = Wearable.getDataClient(context)
    private val messageClient: MessageClient = Wearable.getMessageClient(context)
    private val capabilityClient: CapabilityClient = Wearable.getCapabilityClient(context)

    /**
     * Sends data to the wear network using DataClient.
     */
    suspend fun sendData(path: String, dataJson: String) = withContext(Dispatchers.IO) {
        try {
            val request: PutDataRequest = PutDataMapRequest.create(path).apply {
                dataMap.putString("payload", dataJson)
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()
            
            dataClient.putDataItem(request).await()
            Log.d(tag, "Data sent to $path")
        } catch (e: Exception) {
            Log.e(tag, "Failed to send data to $path", e)
        }
    }

    /**
     * Sends a message to all nodes with a specific capability.
     */
    suspend fun sendMessage(capability: String, path: String, payload: ByteArray = byteArrayOf()) = withContext(Dispatchers.IO) {
        try {
            val nodes = capabilityClient.getCapability(capability, CapabilityClient.FILTER_REACHABLE).await().nodes
            nodes.forEach { node ->
                messageClient.sendMessage(node.id, path, payload).await()
                Log.d(tag, "Message sent to ${node.displayName} at $path")
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to send message to $capability at $path", e)
        }
    }

    /**
     * Sends a message to a specific node ID.
     */
    suspend fun sendMessageToNode(nodeId: String, path: String, payload: ByteArray = byteArrayOf()) = withContext(Dispatchers.IO) {
        try {
            messageClient.sendMessage(nodeId, path, payload).await()
            Log.d(tag, "Message sent to node $nodeId at $path")
        } catch (e: Exception) {
            Log.e(tag, "Failed to send message to node $nodeId at $path", e)
        }
    }

    /**
     * Generic message sender as requested by instructions.
     */
    suspend fun <T> sendMessageToNode(nodeId: String, payload: T, converter: (T) -> ByteArray) = withContext(Dispatchers.IO) {
        try {
            val data = converter(payload)
            messageClient.sendMessage(nodeId, "", data).await() // path empty if not provided
            Log.d(tag, "Generic message sent to node $nodeId")
        } catch (e: Exception) {
            Log.e(tag, "Failed to send generic message to node $nodeId", e)
        }
    }

    /**
     * Generic message sender with explicit type parameter.
     */
    suspend fun <T> sendMessageGeneric(nodeId: String, path: String, payload: T, converter: (T) -> ByteArray) = withContext(Dispatchers.IO) {
        try {
            val data: ByteArray = converter(payload)
            messageClient.sendMessage(nodeId, path, data).await()
            Log.d(tag, "Generic message sent to node $nodeId at $path")
        } catch (e: Exception) {
            Log.e(tag, "Failed to send generic message to node $nodeId at $path", e)
        }
    }
}
