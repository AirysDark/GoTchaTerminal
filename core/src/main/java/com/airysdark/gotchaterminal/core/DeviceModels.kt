package com.airysdark.gotchaterminal.core

data class DeviceModel(
    val name: String? = null,
    val address: String,
    val rssi: Int = 0,
    val manufacturerData: String? = null
)

data class ExtendedDeviceModel(
    val device: DeviceModel,
    val services: List<String> = emptyList(),
    val characteristics: List<String> = emptyList()
)
