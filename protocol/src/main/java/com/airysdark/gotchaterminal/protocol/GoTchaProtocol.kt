package com.airysdark.gotchaterminal.protocol

import com.airysdark.gotchaterminal.ble.BLEManager
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

/**
 * GoTcha Protocol Implementation
 *
 * Implements standard GoTcha commands and decodes characteristic data
 * into human-readable strings.
 */
class GoTchaProtocol(private val bleManager: BLEManager) {

    companion object {
        // Command Opcodes
        const val CMD_READ_SETTINGS: Byte = 0x01
        const val CMD_WRITE_SETTINGS: Byte = 0x02
        const val CMD_READ_STEPS: Byte = 0x03
        const val CMD_WRITE_STEPS: Byte = 0x04
        const val CMD_SET_TOD: Byte = 0x05
        const val CMD_RESET: Byte = 0x06

        // UUIDs from GoTchaUUIDs
        private val SERVICE_UUID = UUID.fromString(GoTchaUUIDs.FLASH_SERVICE)
        private val COMMAND_UUID = UUID.fromString(GoTchaUUIDs.FLASH_COMMAND)
    }

    // --- Commands ---

    /**
     * Request the current configuration settings from the device.
     */
    fun readSettings() {
        sendCommand(byteArrayOf(CMD_READ_SETTINGS))
    }

    /**
     * Write settings bitmask to the device.
     * @param config The settings byte (e.g., 0x1F for all enabled).
     */
    fun writeSettings(config: Byte) {
        sendCommand(byteArrayOf(CMD_WRITE_SETTINGS, config))
    }

    /**
     * Request the current accumulated step count.
     */
    fun readSteps() {
        sendCommand(byteArrayOf(CMD_READ_STEPS))
    }

    /**
     * Clear the step counter (sets it to 0).
     */
    fun clearSteps() {
        // Writes 0 as a 4-byte little-endian integer.
        sendCommand(byteArrayOf(CMD_WRITE_STEPS, 0x00, 0x00, 0x00, 0x00))
    }

    /**
     * Synchronize the GoTcha's internal clock with the current system time.
     */
    fun setTime() {
        val now = Calendar.getInstance()
        val payload = byteArrayOf(
            CMD_SET_TOD,
            (now.get(Calendar.YEAR) - 2000).toByte(),
            (now.get(Calendar.MONTH) + 1).toByte(),
            now.get(Calendar.DAY_OF_MONTH).toByte(),
            now.get(Calendar.HOUR_OF_DAY).toByte(),
            now.get(Calendar.MINUTE).toByte(),
            now.get(Calendar.SECOND).toByte(),
            ((now.get(Calendar.DAY_OF_WEEK) + 5) % 7).toByte() // 0 = Monday, 6 = Sunday
        )
        sendCommand(payload)
    }

    /**
     * Perform a hardware reboot of the GoTcha device.
     */
    fun resetDevice() {
        sendCommand(byteArrayOf(CMD_RESET))
    }

    private fun sendCommand(data: ByteArray) {
        bleManager.writeCharacteristic(SERVICE_UUID, COMMAND_UUID, data)
    }

    // --- Decoding Functions ---

    /**
     * Decodes the settings bitmask into a human-readable summary.
     */
    fun decodeSettings(data: ByteArray): String {
        if (data.isEmpty()) return "Settings: No Data"
        val b = data[0].toInt()
        val flags = mutableListOf<String>()
        
        if (b and 0x01 != 0) flags.add("Auto-Dup") else flags.add("!Auto-Dup")
        if (b and 0x02 != 0) flags.add("Auto-New") else flags.add("!Auto-New")
        if (b and 0x04 != 0) flags.add("Auto-Spin") else flags.add("!Auto-Spin")
        if (b and 0x08 != 0) flags.add("Vibration") else flags.add("!Vibration")
        if (b and 0x10 != 0) flags.add("Screen On") else flags.add("!Screen On")
        
        return "Settings: [${flags.joinToString(", ")}]"
    }

    /**
     * Decodes the 4-byte step count payload.
     */
    fun decodeSteps(data: ByteArray): String {
        if (data.size < 4) return "Steps: 0 (Malformed)"
        val steps = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).int
        return "Step Count: $steps"
    }

    /**
     * Decodes battery level percentage (0-100).
     */
    fun decodeBattery(data: ByteArray): String {
        if (data.isEmpty()) return "Battery: --%"
        val level = data[0].toInt() and 0xFF
        return "Battery Level: $level%"
    }

    /**
     * Decodes the software version string.
     */
    fun decodeVersion(data: ByteArray): String {
        if (data.isEmpty()) return "Version: Unknown"
        return "Firmware Version: ${String(data).trim()}"
    }
}
