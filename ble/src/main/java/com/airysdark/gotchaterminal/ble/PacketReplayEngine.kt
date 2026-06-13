package com.airysdark.gotchaterminal.ble

import android.bluetooth.BluetoothDevice
import android.util.Log
import com.airysdark.gotchaterminal.core.PacketModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*

class PacketReplayEngine(
    private val bleManager: BLEManager,
    private val gattServerManager: GattServerManager
) {
    private val TAG = "PacketReplayEngine"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _isReplaying = MutableStateFlow(false)
    val isReplaying = _isReplaying.asStateFlow()

    private val _replayProgress = MutableStateFlow(0f)
    val replayProgress = _replayProgress.asStateFlow()

    fun replaySession(packets: List<PacketModel>, targetDevice: BluetoothDevice?) {
        if (_isReplaying.value || packets.isEmpty()) return

        scope.launch {
            _isReplaying.value = true
            _replayProgress.value = 0f
            
            try {
                packets.forEachIndexed { index, packet ->
                    if (!isActive) return@forEachIndexed
                    
                    Log.d(TAG, "Replaying packet ${index + 1}/${packets.size}: ${packet.type}")
                    
                    when (packet.type) {
                        "WRITE" -> {
                            bleManager.writeCharacteristic(
                                UUID.fromString(packet.serviceUuid),
                                UUID.fromString(packet.uuid),
                                packet.data.hexToByteArray()
                            )
                        }
                        "NOTIF" -> {
                            targetDevice?.let { device ->
                                val service = gattServerManager.serverServices.value.find { it.uuid.toString() == packet.serviceUuid }
                                val char = service?.getCharacteristic(UUID.fromString(packet.uuid))
                                if (char != null) {
                                    char.value = packet.data.hexToByteArray()
                                    gattServerManager.notifyCharacteristic(device, char, false)
                                }
                            }
                        }
                    }
                    
                    _replayProgress.value = (index + 1).toFloat() / packets.size.toFloat()
                    delay(200)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Replay failed", e)
            } finally {
                _isReplaying.value = false
            }
        }
    }

    private fun String.hexToByteArray(): ByteArray {
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
