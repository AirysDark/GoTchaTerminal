package com.airysdark.gotchaterminal.protocol

import com.airysdark.gotchaterminal.ble.BLEManager
import java.util.*

/**
 * Go-tcha Evolve Identity Service Protocol
 * 
 * This class handles the specific logic for reading and writing device identity,
 * including MAC spoofing and advertisement payload management for Pokemon GO Plus compatibility.
 */
class IdentityProtocol(private val bleManager: BLEManager) {

    companion object {
        val SERVICE_UUID = UUID.fromString(GoTchaUUIDs.IDENTITY_SERVICE)
        val ACTION_UUID = UUID.fromString(GoTchaUUIDs.IDENTITY_ACTION)
        val WRITE_ADDR_UUID = UUID.fromString(GoTchaUUIDs.IDENTITY_WRITE_ADDR)
        val READ_ADDR_UUID = UUID.fromString(GoTchaUUIDs.IDENTITY_READ_ADDR)
        val READ_ADVERT_UUID = UUID.fromString(GoTchaUUIDs.IDENTITY_READ_ADVERT)
        val STATUS_UUID = UUID.fromString(GoTchaUUIDs.IDENTITY_STATUS)
        
        // Known Action Commands
        const val ACTION_START_PAIRING: Byte = 0x01
        const val ACTION_RESET_IDENTITY: Byte = 0x02
        const val ACTION_REBOOT: Byte = 0x03
    }

    data class IdentityData(
        val macAddress: String,
        val advertPayload: String,
        val status: Byte,
        val decodedStatus: String
    )

    /**
     * Requirement: Read MAC Address
     */
    fun readMacAddress() {
        bleManager.readCharacteristic(SERVICE_UUID, READ_ADDR_UUID)
    }

    /**
     * Requirement: Read Advertisement Data
     */
    fun readAdvertisementData() {
        bleManager.readCharacteristic(SERVICE_UUID, READ_ADVERT_UUID)
    }

    /**
     * Requirement: Read Status
     */
    fun readStatus() {
        bleManager.readCharacteristic(SERVICE_UUID, STATUS_UUID)
    }

    /**
     * Requirement: Write Device Address
     */
    fun writeDeviceAddress(mac: ByteArray) {
        if (mac.size == 6) {
            bleManager.writeCharacteristic(SERVICE_UUID, WRITE_ADDR_UUID, mac)
        }
    }

    /**
     * Requirement: Trigger Action
     */
    fun triggerAction(action: Byte) {
        bleManager.writeCharacteristic(SERVICE_UUID, ACTION_UUID, byteArrayOf(action))
    }

    /**
     * Decodes the raw identity data into a structured report.
     */
    fun decodeIdentity(
        macData: ByteArray?,
        advertData: ByteArray?,
        statusData: ByteArray?
    ): IdentityData {
        val mac = macData?.joinToString(":") { "%02X".format(it) } ?: "00:00:00:00:00:00"
        val advert = advertData?.joinToString("") { "%02x".format(it) } ?: "N/A"
        val status = statusData?.getOrNull(0) ?: 0
        
        val decodedStatus = when (status.toInt()) {
            0x00 -> "Idle / Unpaired"
            0x01 -> "Advertising (PGP Mode)"
            0x02 -> "Connected to Pokemon GO"
            0x03 -> "Pairing Failed"
            0x04 -> "Identity Corrupt"
            else -> "Unknown (0x%02X)".format(status)
        }

        return IdentityData(mac, advert, status, decodedStatus)
    }

    /**
     * Requirement: Identify Corruption Indicators
     */
    fun checkForCorruption(data: IdentityData): List<String> {
        val issues = mutableListOf<String>()
        
        if (data.status.toInt() == 0x04) {
            issues.add("Critical: Device reports Identity Corruption status.")
        }
        
        if (data.macAddress == "00:00:00:00:00:00" || data.macAddress == "FF:FF:FF:FF:FF:FF") {
            issues.add("Warning: Invalid or Default MAC address detected.")
        }
        
        if (data.advertPayload.length < 10) {
            issues.add("Error: Advertisement payload is too short or missing.")
        }
        
        return issues
    }
}
