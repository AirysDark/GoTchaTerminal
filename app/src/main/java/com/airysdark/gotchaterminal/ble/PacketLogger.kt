package com.airysdark.gotchaterminal.ble

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class PacketLogger(context: Context) {

    private val logFile: File by lazy {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        File(dir, "ble_packets_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())}.log")
    }

    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    fun log(direction: String, serviceUuid: UUID?, characteristicUuid: UUID, data: ByteArray) {
        val timestamp = timestampFormat.format(Date())
        val hexData = data.joinToString(" ") { "%02x".format(it) }
        
        val logEntry = buildString {
            appendLine(timestamp)
            appendLine(direction)
            appendLine("S: ${serviceUuid ?: "Unknown"}")
            appendLine("C: $characteristicUuid")
            appendLine(hexData)
            appendLine()
        }

        try {
            FileOutputStream(logFile, true).use {
                it.write(logEntry.toByteArray())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
