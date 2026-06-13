package com.airysdark.gotchaterminal.core

data class PacketModel(
    val type: String,
    val serviceUuid: String? = null,
    val uuid: String,
    val data: String,
    val timestamp: Long = System.currentTimeMillis()
)
