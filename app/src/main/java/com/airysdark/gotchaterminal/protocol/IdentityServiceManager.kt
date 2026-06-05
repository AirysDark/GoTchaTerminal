package com.airysdark.gotchaterminal.protocol

import android.content.Context
import android.util.Log
import com.airysdark.gotchaterminal.ble.BLEManager
import kotlinx.coroutines.delay
import java.io.File
import java.util.*

/**
 * Advanced Identity Manager for Go-tcha Evolve Spoofing Analysis.
 */
class IdentityServiceManager(
    private val bleManager: BLEManager,
    private val protocol: IdentityProtocol
) {
    private val TAG = "IdentityManager"

    /**
     * Requirement: Dump All Identity Data and save to Log.
     */
    suspend fun dumpIdentityData(context: Context) {
        val result = StringBuilder()
        result.appendLine("=== Go-tcha Evolve Identity Dump ===")
        result.appendLine("Timestamp: ${Date()}")

        // 1. Read all Identity Characteristics
        protocol.readMacAddress()
        delay(300)
        protocol.readAdvertisementData()
        delay(300)
        protocol.readStatus()
        delay(300)

        // 2. Collect values from BLEManager state
        val values = bleManager.characteristicValues.value
        val macRaw = values[IdentityProtocol.READ_ADDR_UUID]
        val advertRaw = values[IdentityProtocol.READ_ADVERT_UUID]
        val statusRaw = values[IdentityProtocol.STATUS_UUID]

        // 3. Decode
        val identity = protocol.decodeIdentity(macRaw, advertRaw, statusRaw)
        
        result.appendLine("MAC Address: ${identity.macAddress}")
        result.appendLine("Advert Hex: ${identity.advertPayload}")
        result.appendLine("Status: ${identity.decodedStatus} (0x${"%02X".format(identity.status)})")

        // 4. Identify Issues
        val issues = protocol.checkForCorruption(identity)
        if (issues.isNotEmpty()) {
            result.appendLine("\n[!] WARNINGS DETECTED:")
            issues.forEach { result.appendLine(" - $it") }
        }

        // 5. Save to Log File
        saveToFile(context, result.toString())
        Log.d(TAG, result.toString())
    }

    private fun saveToFile(context: Context, data: String) {
        val file = File(context.getExternalFilesDir(null), "identity_dump.txt")
        file.writeText(data)
    }

    /**
     * Requirement: Recovery Method
     * Resets the identity service to factory defaults if corruption is detected.
     */
    fun performIdentityRecovery() {
        Log.w(TAG, "Triggering Identity Recovery...")
        protocol.triggerAction(IdentityProtocol.ACTION_RESET_IDENTITY)
        protocol.triggerAction(IdentityProtocol.ACTION_REBOOT)
    }
}
