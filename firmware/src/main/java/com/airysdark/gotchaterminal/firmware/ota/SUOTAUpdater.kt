package com.airysdark.gotchaterminal.firmware.ota

import android.bluetooth.BluetoothGattCharacteristic
import android.util.Log
import com.airysdark.gotchaterminal.ble.BLEManager
import com.airysdark.gotchaterminal.protocol.GoTchaUUIDs
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*

/**
 * Universal SUOTA Updater for Dialog SmartBond (DA1458x, DA1468x, DA1469x).
 * Supports Go-tcha Generation 1 and Generation 2 (Evolve/DA14697).
 */
class SUOTAUpdater(private val bleManager: BLEManager) {

    private val TAG = "SUOTAUpdater"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _progress = MutableStateFlow(0f)
    val progress = _progress.asStateFlow()

    private val _statusText = MutableStateFlow("Ready")
    val statusText = _statusText.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy = _isBusy.asStateFlow()
    
    private val _bytesSent = MutableStateFlow(0)
    val bytesSent = _bytesSent.asStateFlow()
    
    private val _currentState = MutableStateFlow(OtaState.IDLE)
    val currentState = _currentState.asStateFlow()

    enum class OtaState {
        IDLE, NEGOTIATING, PREPARING, SENDING_BLOCKS, VERIFYING, COMPLETED, FAILED
    }

    private val SUOTA_SERVICE = UUID.fromString(GoTchaUUIDs.SUOTA_SERVICE)
    private val MEM_DEV_CHAR = UUID.fromString(GoTchaUUIDs.SUOTA_MEM_DEV)
    private val PATCH_LEN_CHAR = UUID.fromString(GoTchaUUIDs.SUOTA_PATCH_LEN)
    private val PATCH_DATA_CHAR = UUID.fromString(GoTchaUUIDs.SUOTA_PATCH_DATA)
    private val STATUS_CHAR = UUID.fromString(GoTchaUUIDs.SUOTA_STATUS)

    fun startUpdate(firmware: ByteArray) {
        if (_isBusy.value) return
        
        scope.launch {
            try {
                _isBusy.value = true
                _progress.value = 0f
                _bytesSent.value = 0
                updateState(OtaState.NEGOTIATING, "Starting Universal SUOTA...")

                // 1. MTU Negotiation
                bleManager.requestMtu(512)
                withTimeoutOrNull(5000) { bleManager.mtu.filter { it > 23 }.first() }
                val packetSize = bleManager.mtu.value - 3
                bleManager.addLog("OTA: MTU set to ${bleManager.mtu.value}, Packet Size: $packetSize")

                // 2. Setup Status Notifications
                updateState(OtaState.PREPARING, "Enabling Status Channel...")
                bleManager.clearCharacteristicValue(STATUS_CHAR)
                bleManager.toggleNotification(SUOTA_SERVICE, STATUS_CHAR)
                delay(500)

                // 3. Initialize Target Memory (SPI Flash)
                updateState(OtaState.PREPARING, "Initializing Flash (DA1469x)...")
                // Command: 0x13 (SPI) + 0x000000 (Start Address)
                bleManager.writeCharacteristic(SUOTA_SERVICE, MEM_DEV_CHAR, byteArrayOf(0x13, 0x00, 0x00, 0x00))

                // 4. Transmission (Block-based)
                updateState(OtaState.SENDING_BLOCKS, "Uploading Blocks...")
                val blockSize = 240 // Standard Dialog block size
                val blocks = firmware.toList().chunked(blockSize)

                blocks.forEachIndexed { i, block ->
                    if (!isActive) throw CancellationException("User Cancelled")
                    
                    val blockData = block.toByteArray()
                    
                    // a. Set Block Length
                    bleManager.writeCharacteristic(SUOTA_SERVICE, PATCH_LEN_CHAR, 
                        byteArrayOf((blockData.size and 0xFF).toByte(), ((blockData.size shr 8) and 0xFF).toByte()))
                    
                    // b. Send Block Packets
                    blockData.toList().chunked(packetSize).forEach { packet ->
                        bleManager.writeCharacteristic(SUOTA_SERVICE, PATCH_DATA_CHAR, packet.toByteArray(), 
                            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
                        _bytesSent.value += packet.size
                    }

                    // c. Wait for Block ACK
                    waitForStatus(0x02, "Block $i write error")
                    _progress.value = (i + 1).toFloat() / blocks.size.toFloat()
                }

                // 5. Commit and Reboot
                updateState(OtaState.VERIFYING, "Verifying Update...")
                // SUOTA_END Command (0xFE000000)
                bleManager.writeCharacteristic(SUOTA_SERVICE, MEM_DEV_CHAR, byteArrayOf(0xFE.toByte(), 0x00, 0x00, 0x00))
                
                updateState(OtaState.COMPLETED, "Update Complete. Device Rebooting.")
                bleManager.addLog("OTA: Success. Transferred ${firmware.size} bytes.")

            } catch (e: Exception) {
                Log.e(TAG, "OTA Failure", e)
                updateState(OtaState.FAILED, "Error: ${e.localizedMessage}")
                bleManager.addLog("OTA FAILED: ${e.localizedMessage}")
            } finally {
                _isBusy.value = false
            }
        }
    }

    private suspend fun waitForStatus(expected: Int, errorMsg: String) {
        val status = withTimeoutOrNull(10000) {
            bleManager.characteristicValues
                .map { it[STATUS_CHAR]?.getOrNull(0) ?: 0 }
                .filter { it.toInt() != 0 }
                .first()
        } ?: 0

        if (status.toInt() != expected) {
            throw Exception("$errorMsg (Code: 0x%02X)".format(status))
        }
        bleManager.clearCharacteristicValue(STATUS_CHAR)
    }

    private fun updateState(state: OtaState, text: String) {
        _currentState.value = state
        _statusText.value = text
        Log.d(TAG, "OTA State: $state - $text")
    }
}
