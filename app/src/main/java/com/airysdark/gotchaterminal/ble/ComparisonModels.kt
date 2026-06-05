package com.airysdark.gotchaterminal.ble

import java.util.*

data class BleSession(
    val name: String,
    var deviceName: String? = null,
    var deviceAddress: String? = null,
    val advertisements: MutableList<AdvertInfo> = mutableListOf(),
    val services: MutableList<ServiceInfo> = mutableListOf(),
    val packets: MutableList<PacketInfo> = mutableListOf()
)

data class AdvertInfo(
    val name: String?,
    val address: String,
    val rssi: Int,
    val serviceUuids: List<String>,
    val timestamp: Long = System.currentTimeMillis()
)

data class ServiceInfo(
    val uuid: String,
    val characteristics: List<CharInfo>
)

data class CharInfo(
    val uuid: String,
    val properties: Int
)

data class PacketInfo(
    val type: String, // "READ", "WRITE", "NOTIF"
    val serviceUuid: String,
    val charUuid: String,
    val data: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class ComparisonResult(
    val sessionA: BleSession,
    val sessionB: BleSession,
    val diffs: List<String>
)
