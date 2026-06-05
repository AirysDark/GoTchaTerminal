package com.airysdark.gotchaterminal.firmware

import android.content.Context
import android.net.Uri
import java.security.MessageDigest
import java.util.zip.CRC32

/**
 * Manages firmware loading, metadata calculation, and reporting.
 */
class FirmwareManager(private val context: Context) {

    private var currentFirmware: ByteArray? = null
    private var firmwareName: String = "None"

    fun loadFirmware(uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                currentFirmware = input.readBytes()
                firmwareName = uri.path?.substringAfterLast('/') ?: "Unknown"
                true
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    fun loadFromAssets(fileName: String): Boolean {
        return try {
            context.assets.open("firmware/$fileName").use { input ->
                currentFirmware = input.readBytes()
                firmwareName = fileName
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    fun getFirmwareBytes(): ByteArray? = currentFirmware
    fun getFirmwareName(): String = firmwareName
    fun getFirmwareSize(): Int = currentFirmware?.size ?: 0

    fun calculateCRC32(): String {
        val data = currentFirmware ?: return "00000000"
        val crc = CRC32()
        crc.update(data)
        return "%08X".format(crc.value)
    }

    fun calculateSHA256(): String {
        val data = currentFirmware ?: return ""
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data).joinToString("") { "%02x".format(it) }
    }

    fun searchAsciiStrings(minLength: Int = 4): List<String> {
        val data = currentFirmware ?: return emptyList()
        val strings = mutableListOf<String>()
        var current = StringBuilder()
        for (b in data) {
            if (b in 32..126) {
                current.append(b.toChar())
            } else {
                if (current.length >= minLength) {
                    strings.add(current.toString())
                }
                current = StringBuilder()
            }
        }
        return strings
    }

    fun exportReport(): String {
        return buildString {
            appendLine("Firmware Report")
            appendLine("Name: ${getFirmwareName()}")
            appendLine("Size: ${getFirmwareSize()} bytes")
            appendLine("CRC32: ${calculateCRC32()}")
            appendLine("SHA256: ${calculateSHA256()}")
        }
    }
}
