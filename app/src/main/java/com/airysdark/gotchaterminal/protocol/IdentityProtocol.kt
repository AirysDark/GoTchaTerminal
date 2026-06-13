package com.airysdark.gotchaterminal.protocol

import com.airysdark.gotchaterminal.ble.BLEManager
import java.util.*

/**
 * Go-tcha Evolve Identity Service Protocol
 * 
 * This class handles the specific logic for reading and writing device identity,
 * including MAC spoofing and advertisement payload management for Pokemon GO Plus compatibility.
 * Updated for DA14697/Evolve Gen2 specific characteristics.
 */
class IdentityProtocol(private val bleManager: BLEManager) {

    companion object {
        val SERVICE_UUID = UUID.fromString(GoTchaUUIDs.IDENTITY_SERVICE)
        val ACTION_UUID = UUID.fromString(GoTchaUUIDs.IDENTITY_ACTION)
        val WRITE_ADDR_UUID = UUID.fromString(GoTchaUUIDs.IDENTITY_WRITE_ADDR)
        val READ_ADDR_UUID = UUID.fromString(GoTchaUUIDs.IDENTITY_READ_ADDR)
        val READ_ADVERT_UUID = UUID.fromString(GoTchaUUIDs.IDENTITY_READ_ADVERT)
        val STATUS_UUID = UUID.fromString(GoTchaUUIDs.IDENTITY_STATUS)
        
        // DA14697 Specific
        val KEY_UUID = UUID.fromString(GoTchaUUIDs.IDENTITY_KEY)
        val BLOB_UUID = UUID.fromString(GoTchaUUIDs.IDENTITY_BLOB)
        
        // Known Action Commands
        const val ACTION_START_PAIRING: Byte = 0x01
        const val ACTION_RESET_IDENTITY: Byte = 0x02
        const val ACTION_REBOOT: Byte = 0x03
    }

    data class IdentityData(
        val macAddress: String,
        val advertPayload: String,
        val status: Byte,
        val decodedStatus: String,
        val key: String = "N/A",
        val blob: String = "N/A"
    )

    fun readMacAddress() {
        bleManager.readCharacteristic(SERVICE_UUID, READ_ADDR_UUID)
    }

    fun readAdvertisementData() {
        bleManager.readCharacteristic(SERVICE_UUID, READ_ADVERT_UUID)
    }

    fun readStatus() {
        bleManager.readCharacteristic(SERVICE_UUID, STATUS_UUID)
    }

    fun readKey() {
        bleManager.readCharacteristic(SERVICE_UUID, KEY_UUID)
    }

    fun readBlob() {
        bleManager.readCharacteristic(SERVICE_UUID, BLOB_UUID)
    }

    fun writeDeviceAddress(mac: ByteArray) {
        if (mac.size == 6) {
            bleManager.writeCharacteristic(SERVICE_UUID, WRITE_ADDR_UUID, mac)
        }
    }

    fun triggerAction(action: Byte) {
        bleManager.writeCharacteristic(SERVICE_UUID, ACTION_UUID, byteArrayOf(action))
    }

    fun decodeIdentity(
        macData: ByteArray?,
        advertData: ByteArray?,
        statusData: ByteArray?,
        keyData: ByteArray? = null,
        blobData: ByteArray? = null
    ): IdentityData {
        val mac = macData?.joinToString(":") { "%02X".format(it) } ?: "00:00:00:00:00:00"
        val advert = advertData?.joinToString("") { "%02x".format(it) } ?: "N/A"
        val status = statusData?.getOrNull(0) ?: 0
        val key = keyData?.joinToString("") { "%02x".format(it) } ?: "N/A"
        val blob = blobData?.joinToString("") { "%02x".format(it) } ?: "N/A"
        
        val decodedStatus = when (status.toInt()) {
            0x00 -> "Idle / Unpaired"
            0x01 -> "Advertising (PGP Mode)"
            0x02 -> "Connected to Pokemon GO"
            0x03 -> "Pairing Failed"
            0x04 -> "Identity Corrupt"
            else -> "Unknown (0x%02X)".format(status)
        }

        return IdentityData(mac, advert, status, decodedStatus, key, blob)
    }

    fun checkForCorruption(data: IdentityData): List<String> {
        val issues = mutableListOf<String>()
        if (data.status.toInt() == 0x04) issues.add("Critical: Device reports Identity Corruption status.")
        if (data.macAddress == "00:00:00:00:00:00" || data.macAddress == "FF:FF:FF:FF:FF:FF") issues.add("Warning: Invalid MAC address.")
        if (data.advertPayload.length < 10) issues.add("Error: Advertisement payload too short.")
        return issues
    }
}
