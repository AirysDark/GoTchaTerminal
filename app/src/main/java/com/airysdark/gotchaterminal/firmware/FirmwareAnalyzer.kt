package com.airysdark.gotchaterminal.firmware

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.regex.Pattern

/**
 * FirmwareAnalyzer provides tools for inspecting binary firmware files.
 * It calculates hashes, extracts strings, and searches for protocol-specific identifiers.
 */
class FirmwareAnalyzer(private val file: File) {

    private val data: ByteArray = if (file.exists()) file.readBytes() else ByteArray(0)

    data class AnalysisResult(
        val fileName: String,
        val size: Int,
        val crc32: String,
        val sha1: String,
        val sha256: String,
        val strings: List<String>,
        val uuids: List<String>,
        val bluetoothNames: List<String>
    )

    /**
     * Performs a full analysis of the loaded firmware file.
     */
    fun analyze(): AnalysisResult {
        if (data.isEmpty()) {
            return AnalysisResult(file.name, 0, "", "", "", emptyList(), emptyList(), emptyList())
        }

        val extractedStrings = findStrings()
        
        return AnalysisResult(
            fileName = file.name,
            size = data.size,
            crc32 = calculateCRC32(),
            sha1 = calculateHash("SHA-1"),
            sha256 = calculateHash("SHA-256"),
            strings = extractedStrings,
            uuids = findUUIDs(extractedStrings),
            bluetoothNames = findBluetoothNames(extractedStrings)
        )
    }

    private fun calculateCRC32(): String {
        val crc = CRC32()
        crc.update(data)
        return String.format("%08X", crc.value)
    }

    private fun calculateHash(algorithm: String): String {
        return try {
            val digest = MessageDigest.getInstance(algorithm)
            val hash = digest.digest(data)
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    /**
     * Extracts ASCII strings from the binary data.
     */
    private fun findStrings(minLength: Int = 4): List<String> {
        val strings = mutableListOf<String>()
        val current = StringBuilder()
        
        for (b in data) {
            val char = (b.toInt() and 0xFF).toChar()
            // Printable ASCII range
            if (char in ' '..'~') {
                current.append(char)
            } else {
                if (current.length >= minLength) {
                    strings.add(current.toString())
                }
                current.setLength(0)
            }
        }
        
        // Check last string if file ends with printable chars
        if (current.length >= minLength) {
            strings.add(current.toString())
        }
        
        return strings.distinct()
    }

    /**
     * Searches for 128-bit UUID strings (ASCII) within the firmware.
     */
    private fun findUUIDs(strings: List<String>): List<String> {
        val uuids = mutableListOf<String>()
        val uuidPattern = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
        
        for (s in strings) {
            val matcher = uuidPattern.matcher(s)
            while (matcher.find()) {
                uuids.add(matcher.group())
            }
        }
        
        return uuids.distinct()
    }

    /**
     * Searches for strings that look like Bluetooth device names or related keywords.
     */
    private fun findBluetoothNames(strings: List<String>): List<String> {
        val names = mutableListOf<String>()
        val keywords = listOf(
            "GoTcha", "Pocket", "Auto", "Catch", "Go-Plus", "Datel", 
            "Codejunkies", "Bluetooth", "BLE", "Nordic", "Dialog"
        )
        
        for (s in strings) {
            if (keywords.any { s.contains(it, ignoreCase = true) }) {
                names.add(s)
            }
        }
        
        return names.distinct()
    }

    /**
     * Exports the analysis results to a JSON string.
     */
    fun exportToJson(result: AnalysisResult): String {
        val json = JSONObject()
        json.put("fileName", result.fileName)
        json.put("fileSize", result.size)
        
        val hashes = JSONObject()
        hashes.put("crc32", result.crc32)
        hashes.put("sha1", result.sha1)
        hashes.put("sha256", result.sha256)
        json.put("hashes", hashes)
        
        json.put("uuidsFound", JSONArray(result.uuids))
        json.put("bluetoothKeywords", JSONArray(result.bluetoothNames))
        json.put("extractedStringsCount", result.strings.size)
        json.put("strings", JSONArray(result.strings))
        
        return json.toString(4)
    }
}
