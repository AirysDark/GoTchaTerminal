package com.airysdark.gotchaterminal.core

data class SessionModel(
    val id: String,
    val deviceAddress: String,
    val packets: MutableList<PacketModel> = mutableListOf(),
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null
)
