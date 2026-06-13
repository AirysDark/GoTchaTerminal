package com.airysdark.gotchaterminal.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*

@SuppressLint("MissingPermission")
class AdvertisementManager(private val context: Context) {
    private val TAG = "AdvManager"
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val advertiser: BluetoothLeAdvertiser? = bluetoothAdapter?.bluetoothLeAdvertiser

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising = _isAdvertising.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs = _logs.asStateFlow()

    private var activeCallback: AdvertiseCallback? = null

    data class AdvProfile(
        val name: String,
        val deviceName: String,
        val serviceUuids: List<UUID>,
        val manufacturerData: Map<Int, ByteArray> = emptyMap(),
        val includeDeviceName: Boolean = true,
        val connectable: Boolean = true
    )

    val presets = listOf(
        AdvProfile(
            "Go-tcha Evolve",
            "Go-tcha Evolve",
            listOf(UUID.fromString("0000fef5-0000-1000-8000-00805f9b34fb")),
            mapOf(0xFFFF to byteArrayOf(0x01, 0x02, 0x03))
        ),
        AdvProfile(
            "Pokemon GO Plus",
            "Pokemon GO Plus",
            listOf(UUID.fromString("0000fee5-0000-1000-8000-00805f9b34fb")),
            mapOf(0x0001 to byteArrayOf(0x00, 0x00, 0x00))
        ),
        AdvProfile(
            "Pokemon GO Plus+",
            "Pokemon GO Plus+",
            listOf(UUID.fromString("0000fee5-0000-1000-8000-00805f9b34fb")),
            mapOf(0x0002 to byteArrayOf(0x01, 0x01, 0x01))
        )
    )

    fun addLog(msg: String) {
        _logs.value = _logs.value + msg
        Log.d(TAG, msg)
    }

    fun startAdvertising(profile: AdvProfile) {
        if (advertiser == null) {
            addLog("Error: BLE Advertising not supported on this device")
            return
        }

        stopAdvertising()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(profile.connectable)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        val dataBuilder = AdvertiseData.Builder()
            .setIncludeDeviceName(profile.includeDeviceName)
        
        profile.serviceUuids.forEach {
            dataBuilder.addServiceUuid(ParcelUuid(it))
        }

        profile.manufacturerData.forEach { (id, data) ->
            dataBuilder.addManufacturerData(id, data)
        }

        val data = dataBuilder.build()

        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                _isAdvertising.value = true
                addLog("Advertising started: ${profile.name}")
            }

            override fun onStartFailure(errorCode: Int) {
                _isAdvertising.value = false
                val errorMsg = when (errorCode) {
                    ADVERTISE_FAILED_ALREADY_STARTED -> "Already started"
                    ADVERTISE_FAILED_DATA_TOO_LARGE -> "Data too large"
                    ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "Feature unsupported"
                    ADVERTISE_FAILED_INTERNAL_ERROR -> "Internal error"
                    ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Too many advertisers"
                    else -> "Unknown error: $errorCode"
                }
                addLog("Advertising failed: $errorMsg")
            }
        }

        activeCallback = callback
        bluetoothAdapter?.name = profile.deviceName
        advertiser.startAdvertising(settings, data, callback)
    }

    fun stopAdvertising() {
        activeCallback?.let {
            advertiser?.stopAdvertising(it)
            activeCallback = null
            _isAdvertising.value = false
            addLog("Advertising stopped")
        }
    }
}
