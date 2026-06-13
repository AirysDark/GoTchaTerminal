package com.airysdark.gotchaterminal.ble

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class PacketRecorder(private val context: Context) {
    private val TAG = "PacketRecorder"

    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    private val _recordedPackets = MutableStateFlow<List<PacketInfo>>(emptyList())
    val recordedPackets = _recordedPackets.asStateFlow()

    fun startRecording() {
        _recordedPackets.value = emptyList()
        _isRecording.value = true
        Log.d(TAG, "Recording started")
    }

    fun stopRecording(): List<PacketInfo> {
        _isRecording.value = false
        Log.d(TAG, "Recording stopped. Captured ${_recordedPackets.value.size} packets")
        return _recordedPackets.value
    }

    fun recordPacket(packet: PacketInfo) {
        if (_isRecording.value) {
            _recordedPackets.value = _recordedPackets.value + packet
        }
    }

    fun saveSession(name: String): String? {
        val packets = _recordedPackets.value
        if (packets.isEmpty()) return null

        val root = JSONObject()
        root.put("sessionName", name)
        root.put("timestamp", System.currentTimeMillis())
        
        val array = JSONArray()
        packets.forEach { p ->
            val obj = JSONObject()
            obj.put("type", p.type)
            obj.put("svc", p.serviceUuid)
            obj.put("char", p.charUuid)
            obj.put("data", p.data)
            obj.put("ts", p.timestamp)
            array.put(obj)
        }
        root.put("packets", array)

        val fileName = "session_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.json"
        val file = File(context.getExternalFilesDir("sessions"), fileName)
        file.writeText(root.toString(2))
        return file.absolutePath
    }

    fun loadSession(file: File): List<PacketInfo> {
        val json = JSONObject(file.readText())
        val array = json.getJSONArray("packets")
        val packets = mutableListOf<PacketInfo>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            packets.add(PacketInfo(
                type = obj.getString("type"),
                serviceUuid = obj.getString("svc"),
                charUuid = obj.getString("char"),
                data = obj.getString("data"),
                timestamp = obj.getLong("ts")
            ))
        }
        _recordedPackets.value = packets
        return packets
    }
}
