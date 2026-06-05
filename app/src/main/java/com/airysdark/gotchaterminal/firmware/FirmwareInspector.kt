package com.airysdark.gotchaterminal.firmware

import java.security.MessageDigest
import java.util.zip.CRC32

/**
 * Provides deep analysis and comparison of Go-tcha firmware binaries.
 */
object FirmwareInspector {

    data class FirmwareStats(
        val size: Long,
        val crc32: String,
        val sha1: String,
        val sha256: String,
        val asciiStrings: List<String>,
        val uuidCandidates: List<String>,
        val versionStrings: List<String>
    )

    fun analyze(data: ByteArray): FirmwareStats {
        val crc = CRC32().apply { update(data) }.value
        val sha1 = hash(data, "SHA-1")
        val sha256 = hash(data, "SHA-256")
        
        val ascii = extractStrings(data, 4)
        val versions = ascii.filter { it.matches(Regex(".*\\d+\\.\\d+\\.\\d+\\.\\d+.*")) }
        val uuids = ascii.filter { it.matches(Regex(".*[0-9a-fA-F]{8}-.*")) }

        return FirmwareStats(
            size = data.size.toLong(),
            crc32 = "%08X".format(crc),
            sha1 = sha1,
            sha256 = sha256,
            asciiStrings = ascii.take(100), // Limit for display
            uuidCandidates = uuids,
            versionStrings = versions
        )
    }

    fun compare(dataA: ByteArray, dataB: ByteArray): String {
        val report = StringBuilder()
        val minSize = minOf(dataA.size, dataB.size)
        val maxSize = maxOf(dataA.size, dataB.size)
        
        var changedBytes = 0
        val changedOffsets = mutableListOf<Int>()

        for (i in 0 until minSize) {
            if (dataA[i] != dataB[i]) {
                changedBytes++
                if (changedOffsets.size < 50) {
                    changedOffsets.add(i)
                }
            }
        }
        
        changedBytes += (maxSize - minSize)
        val percentDiff = (changedBytes.toDouble() / maxSize.toDouble()) * 100.0

        report.appendLine("Firmware Comparison Report")
        report.appendLine("--------------------------")
        report.appendLine("Total Bytes Changed: $changedBytes")
        report.appendLine("Difference: %.2f%%".format(percentDiff))
        report.appendLine("First 50 Changed Offsets: ${changedOffsets.joinToString { "0x%04X".format(it) }}")
        
        return report.toString()
    }

    private fun hash(data: ByteArray, algorithm: String): String {
        val digest = MessageDigest.getInstance(algorithm)
        return digest.digest(data).joinToString("") { "%02x".format(it) }
    }

    private fun extractStrings(data: ByteArray, minLength: Int): List<String> {
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
}
