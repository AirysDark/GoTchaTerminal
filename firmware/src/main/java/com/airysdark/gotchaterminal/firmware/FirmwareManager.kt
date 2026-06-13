package com.airysdark.gotchaterminal.firmware

import android.content.Context
import android.net.Uri
import com.airysdark.gotchaterminal.models.firmware.FirmwareAnalysisResult
import com.airysdark.gotchaterminal.models.firmware.HexLine
import java.security.MessageDigest
import java.util.zip.CRC32
import kotlin.math.ln

/**
 * Advanced Firmware Analysis Engine for GoTcha (DA14697) and other BLE devices.
 */
class FirmwareManager(context: Context) {

    private val applicationContext = context.applicationContext

    companion object {
        @Volatile
        private var INSTANCE: FirmwareManager? = null

        fun getInstance(context: Context): FirmwareManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FirmwareManager(context).also { INSTANCE = it }
            }
        }
    }

    private var currentFirmware: ByteArray? = null
    private var firmwareName: String = "None"

    fun loadFirmware(uri: Uri): Boolean {
        return try {
            applicationContext.contentResolver.openInputStream(uri)?.use { input ->
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
            applicationContext.assets.open("firmware/$fileName").use { input ->
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

    fun calculateHashes(): Map<String, String> {
        val data = currentFirmware ?: return emptyMap()
        return mapOf(
            "CRC32" to calculateCRC32(data),
            "MD5" to calculateHash(data, "MD5"),
            "SHA1" to calculateHash(data, "SHA-1"),
            "SHA256" to calculateHash(data, "SHA-256")
        )
    }

    fun analyze(): FirmwareAnalysisResult? {
        val data = currentFirmware ?: return null
        
        return FirmwareAnalysisResult(
            name = firmwareName,
            size = data.size,
            crc32 = calculateCRC32(data),
            md5 = calculateHash(data, "MD5"),
            sha1 = calculateHash(data, "SHA-1"),
            sha256 = calculateHash(data, "SHA-256"),
            entropy = calculateEntropy(data),
            architecture = detectArchitecture(data),
            strings = extractStrings(data, 4),
            uuids = extractUuids(data),
            urls = extractUrls(data),
            builds = extractBuildDates(data),
            headerInfo = extractHeaderInfo(data)
        )
    }

    private fun extractHeaderInfo(data: ByteArray): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        if (data.size < 64) return headers

        // SUOTA / Dialog Image Header (64 bytes)
        // Offset 0x00: Signature "SD" (0x53 0x44)
        if (data[0] == 0x53.toByte() && data[1] == 0x44.toByte()) {
            headers["Type"] = "Dialog SUOTA Image"
            headers["Identifier"] = data.sliceArray(0..1).joinToString("") { (it.toInt() and 0xFF).toChar().toString() }
            headers["Timestamp"] = "%08X".format(readInt32LE(data, 2))
            headers["Version"] = extractStrings(data.sliceArray(6..21), 1).firstOrNull() ?: "Unknown"
            headers["Data Size"] = "${readInt32LE(data, 22)} bytes"
            headers["CRC"] = "%08X".format(readInt32LE(data, 26))
        }

        // Nordic nRF52 DFU Header
        if (data.size > 100 && data[0] == 0x12.toByte() && data[1] == 0x34.toByte()) {
            headers["Type"] = "Nordic DFU Image"
        }

        return headers
    }

    private fun readInt32LE(data: ByteArray, offset: Int): Long {
        if (offset + 3 >= data.size) return 0
        return (data[offset].toLong() and 0xFF) or
                ((data[offset + 1].toLong() and 0xFF) shl 8) or
                ((data[offset + 2].toLong() and 0xFF) shl 16) or
                ((data[offset + 3].toLong() and 0xFF) shl 24)
    }

    private fun calculateCRC32(data: ByteArray): String {
        val crc = CRC32()
        crc.update(data)
        return "%08X".format(crc.value)
    }

    private fun calculateHash(data: ByteArray, algorithm: String): String {
        val digest = MessageDigest.getInstance(algorithm)
        return digest.digest(data).joinToString("") { "%02x".format(it) }
    }

    private fun calculateEntropy(data: ByteArray): Double {
        if (data.isEmpty()) return 0.0
        val counts = IntArray(256)
        for (b in data) counts[b.toInt() and 0xFF]++
        var entropy = 0.0
        for (count in counts) {
            if (count > 0) {
                val p = count.toDouble() / data.size
                entropy -= p * (ln(p) / ln(2.0))
            }
        }
        return entropy
    }

    private fun detectArchitecture(data: ByteArray): String {
        if (data.size < 16) return "Unknown"
        val hex = data.take(16).joinToString("") { "%02x".format(it) }
        
        return when {
            // Dialog DA1469x Bootloader / ARM Cortex-M33
            hex.startsWith("70500020") || hex.startsWith("00000120") -> "Dialog DA1469x (ARM Cortex-M33)"
            // Dialog DA14580
            hex.startsWith("00000020") && hex.contains("c1000000") -> "Dialog DA1458x (ARM Cortex-M0)"
            // Nordic nRF52
            hex.contains("00040020") -> "Nordic nRF52 (ARM Cortex-M4)"
            // ESP32 Image
            hex.startsWith("e9") -> "ESP32 (Xtensa/RISC-V)"
            else -> "Unknown (ARM/Binary)"
        }
    }

    private fun extractStrings(data: ByteArray, minLength: Int): List<String> {
        val strings = mutableListOf<String>()
        var current = StringBuilder()
        for (b in data) {
            val intVal = b.toInt() and 0xFF
            if (intVal in 32..126) {
                current.append(intVal.toChar())
            } else {
                if (current.length >= minLength) {
                    strings.add(current.toString())
                }
                current = StringBuilder()
            }
        }
        return strings.distinct()
    }

    private fun extractUuids(data: ByteArray): List<String> {
        val strings = extractStrings(data, 16)
        val uuidRegex = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
        return strings.filter { uuidRegex.matches(it) }.distinct()
    }

    private fun extractUrls(data: ByteArray): List<String> {
        val strings = extractStrings(data, 10)
        val urlRegex = Regex("(https?|ftp)://[^\\s/$.?#].[^\\s]*")
        return strings.filter { urlRegex.containsMatchIn(it) }.distinct()
    }

    private fun extractBuildDates(data: ByteArray): List<String> {
        val strings = extractStrings(data, 8)
        val dateRegex = Regex("\\d{4}/\\d{2}/\\d{2}|\\w{3} \\d{1,2} \\d{4}")
        return strings.filter { dateRegex.containsMatchIn(it) }.distinct()
    }

    fun getHexView(offset: Int, length: Int): List<HexLine> {
        val data = currentFirmware ?: return emptyList()
        val end = (offset + length).coerceAtMost(data.size)
        val lines = mutableListOf<HexLine>()
        
        for (i in offset until end step 16) {
            val chunkEnd = (i + 16).coerceAtMost(end)
            val chunk = data.sliceArray(i until chunkEnd)
            lines.add(HexLine(
                address = "%08X".format(i),
                hex = chunk.joinToString(" ") { "%02X".format(it) }.padEnd(47),
                ascii = chunk.map { val intVal = it.toInt() and 0xFF; if (intVal in 32..126) intVal.toChar() else '.' }.joinToString("")
            ))
        }
        return lines
    }

    /**
     * Applies a byte patch to the current firmware in memory.
     * @return true if patch was applied successfully.
     */
    fun applyPatch(offset: Int, patchData: ByteArray): Boolean {
        val data = currentFirmware ?: return false
        if (offset < 0 || offset + patchData.size > data.size) return false
        
        System.arraycopy(patchData, 0, data, offset, patchData.size)
        return true
    }

    /**
     * Common GoTcha Evolve Patches (Placeholder Offsets)
     */
    fun applyVibrationPatch(enable: Boolean): Boolean {
        // Placeholder offset: 0x24000
        return applyPatch(0x24000, if (enable) byteArrayOf(0x01) else byteArrayOf(0x00))
    }

    fun compare(otherData: ByteArray): Map<String, Any> {
        val current = currentFirmware ?: return emptyMap()
        val minSize = minOf(current.size, otherData.size)
        val maxSize = maxOf(current.size, otherData.size)
        
        var diffCount = 0
        val diffs = mutableListOf<Pair<Int, Byte>>() // Only first few
        
        for (i in 0 until minSize) {
            if (current[i] != otherData[i]) {
                diffCount++
                if (diffs.size < 100) diffs.add(i to otherData[i])
            }
        }
        
        diffCount += (maxSize - minSize)
        
        return mapOf(
            "diffCount" to diffCount,
            "percent" to (diffCount.toDouble() / maxSize.toDouble() * 100.0),
            "match" to (diffCount == 0)
        )
    }

    fun exportReport(): String {
        val res = analyze() ?: return "No firmware loaded."
        return buildString {
            appendLine("=== GoTcha Firmware Analysis Report ===")
            appendLine("File: ${res.name}")
            appendLine("Size: ${res.size} bytes")
            appendLine("CRC32: ${res.crc32}")
            appendLine("SHA256: ${res.sha256}")
            appendLine("Entropy: ${"%.4f".format(res.entropy)}")
            appendLine("Detected Architecture: ${res.architecture}")
            appendLine("\n--- URLs ---")
            res.urls.forEach { appendLine(it) }
            appendLine("\n--- UUIDs ---")
            res.uuids.forEach { appendLine(it) }
            appendLine("\n--- Build Strings ---")
            res.builds.forEach { appendLine(it) }
        }
    }
}
