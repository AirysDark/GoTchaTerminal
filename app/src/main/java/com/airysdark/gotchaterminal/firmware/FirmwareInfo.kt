package com.airysdark.gotchaterminal.firmware

data class FirmwareInfo(
    val name: String,
    val size: Int,
    val crc32: String,
    val sha256: String,
    val isInternal: Boolean = false,
    val path: String = ""
)
