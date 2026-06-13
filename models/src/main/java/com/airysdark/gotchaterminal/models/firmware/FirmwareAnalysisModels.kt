package com.airysdark.gotchaterminal.models.firmware

data class FirmwareAnalysisResult(
    val name: String,
    val size: Int,
    val crc32: String,
    val md5: String = "",
    val sha1: String,
    val sha256: String,
    val entropy: Double = 0.0,
    val architecture: String = "Unknown",
    val strings: List<String> = emptyList(),
    val uuids: List<String> = emptyList(),
    val urls: List<String> = emptyList(),
    val builds: List<String> = emptyList(),
    val bluetoothNames: List<String> = emptyList(),
    val headerInfo: Map<String, String> = emptyMap()
)

data class HexLine(val address: String, val hex: String, val ascii: String)

data class FirmwareStats(
    val size: Long,
    val crc32: String,
    val sha1: String,
    val sha256: String,
    val asciiStrings: List<String>,
    val uuidCandidates: List<String>,
    val versionStrings: List<String>
)
