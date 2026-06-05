package com.airysdark.gotchaterminal.ota

import android.util.Log
import com.airysdark.gotchaterminal.ble.BLEManager
import com.airysdark.gotchaterminal.protocol.GoTchaUUIDs
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*

/**
 * High-fidelity SUOTA Updater for Go-tcha devices.
 * Implements the protocol found in the original application.
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
                _statusText.value = "Starting update..."

                // 1. Negotiation Phase
                readMtu()
                val blockSize = readPatchSize()
                
                // 2. Preparation Phase
                monitorStatus()
                startFlash()
                
                // 3. Transmission Phase
                val totalChunks = (firmware.size + blockSize - 1) / blockSize
                _statusText.value = "Sending $totalChunks chunks..."

                firmware.toList().chunked(blockSize).forEachIndexed { index, chunk ->
                    sendFirmwareChunk(chunk.toByteArray())
                    _progress.value = (index + 1).toFloat() / totalChunks.toFloat()
                    // Throttle for stability
                    delay(15) 
                }

                // 4. Finalization Phase
                finishUpdate()

            } catch (e: Exception) {
                Log.e(TAG, "Update error", e)
                _statusText.value = "Failed: ${e.localizedMessage}"
            } finally {
                _isBusy.value = false
            }
        }
    }

    /**
     * Step 1: Maximize MTU for throughput.
     */
    private suspend fun readMtu() {
        _statusText.value = "Requesting MTU..."
        bleManager.requestMtu(512)
        // Wait for the MTU state to update in BLEManager
        bleManager.mtu.filter { it > 23 }.first()
        Log.d(TAG, "MTU Negotiated: ${bleManager.mtu.value}")
    }

    /**
     * Step 2: Read device's internal buffer size.
     */
    private suspend fun readPatchSize(): Int {
        _statusText.value = "Reading Patch Size..."
        bleManager.readCharacteristic(SUOTA_SERVICE, PATCH_LEN_CHAR)
        
        val data = bleManager.characteristicValues
            .filter { it.containsKey(PATCH_LEN_CHAR) }
            .first()[PATCH_LEN_CHAR] ?: return 20
            
        val size = (data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8)
        Log.d(TAG, "Device Block Size: $size")
        return size
    }

    /**
     * Step 3: Enable notifications on status characteristic.
     */
    private suspend fun monitorStatus() {
        _statusText.value = "Enabling status monitor..."
        bleManager.toggleNotification(SUOTA_SERVICE, STATUS_CHAR)
        delay(300)
    }

    /**
     * Step 4: Initialize the flash memory parameters.
     * Original app writes 0x13000000 to MEM_DEV to select SPI Flash.
     */
    private fun startFlash() {
        _statusText.value = "Initializing Flash..."
        // Commands for Memory Device: 0x13 (SPI) + Start Address (0x000000)
        val cmd = byteArrayOf(0x13, 0x00, 0x00, 0x00)
        bleManager.writeCharacteristic(SUOTA_SERVICE, MEM_DEV_CHAR, cmd)
    }

    /**
     * Step 5: Transfer a single chunk of firmware.
     */
    private fun sendFirmwareChunk(chunk: ByteArray) {
        bleManager.writeCharacteristic(SUOTA_SERVICE, PATCH_DATA_CHAR, chunk)
    }

    /**
     * Step 6: Verify and commit the update.
     * Writes 0xFE000000 to MEM_DEV to trigger reboot/verification.
     */
    private suspend fun finishUpdate() {
        _statusText.value = "Verifying completion..."
        
        // Wait for STATUS = 0x02 (CMP_OK)
        val success = bleManager.characteristicValues
            .map { it[STATUS_CHAR]?.getOrNull(0) ?: 0 }
            .filter { it.toInt() != 0 }
            .first()

        if (success.toInt() == 0x02) {
            _statusText.value = "Update Successful! Rebooting..."
            // Send SUOTA_END command
            val endCmd = byteArrayOf(0xFE.toByte(), 0x00, 0x00, 0x00)
            bleManager.writeCharacteristic(SUOTA_SERVICE, MEM_DEV_CHAR, endCmd)
            _progress.value = 1.0f
        } else {
            _statusText.value = "Update Error Code: 0x%02X".format(success)
        }
    }
}
