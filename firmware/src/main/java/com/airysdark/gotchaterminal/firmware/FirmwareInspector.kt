package com.airysdark.gotchaterminal.firmware

import android.util.Log
import java.security.MessageDigest
import java.util.*
import java.util.zip.CRC32

/**
 * Provides deep analysis and comparison of GoTcha firmware binaries.
 */
object FirmwareInspector {

    fun analyze(data: ByteArray): FirmwareStats {
        val crc = CRC32().apply { update(data) }.value
        val sha256 = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
        
        val strings = extractStrings(data, 4)
        val versions = strings.filter { it.contains(Regex("\\d+\\.\\d+\\.\\d+")) }

        return FirmwareStats(
            size = data.size,
            crc32 = "%08X".format(crc),
            sha256 = sha256,
            versionStrings = versions,
        )
    }

    data class FirmwareStats(
        val size: Int,
        val crc32: String,
        val sha256: String,
        val versionStrings: List<String>
    )

    fun compare(old: ByteArray, new: ByteArray): String {
        val report = StringBuilder()
        report.appendLine("Comparison Report:")
        report.appendLine("Old Size: ${old.size}, New Size: ${new.size}")
        
        if (old.size != new.size) {
            report.appendLine("WARNING: Sizes differ!")
        }

        var changedBytes = 0
        val minSize = minOf(old.size, new.size)
        for (i in 0 until minSize) {
            if (old[i] != new[i]) {
                changedBytes++
                if (changedBytes < 20) {
                    report.appendLine("Diff at 0x${"%08X".format(i)}: ${"%02X".format(old[i])} -> ${"%02X".format(new[i])}")
                }
            }
        }

        report.appendLine("Total Bytes Changed: $changedBytes")
        return report.toString()
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
}
