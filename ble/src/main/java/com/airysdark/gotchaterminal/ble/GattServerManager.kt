package com.airysdark.gotchaterminal.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.*

/**
 * Advanced GATT Server Manager for Go-tcha/DA14697 Emulation.
 */
@SuppressLint("MissingPermission")
class GattServerManager(private val context: Context) {
    private val TAG = "GattServerManager"
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private var gattServer: BluetoothGattServer? = null

    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning = _isServerRunning.asStateFlow()

    private val _connectedDevices = MutableStateFlow<Set<BluetoothDevice>>(emptySet())
    val connectedDevices = _connectedDevices.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs = _logs.asStateFlow()

    private val _serverServices = MutableStateFlow<List<BluetoothGattService>>(emptyList())
    val serverServices = _serverServices.asStateFlow()

    private val serverCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _connectedDevices.update { it + device }
                addLog("Client Connected: ${device.address}")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _connectedDevices.update { it - device }
                addLog("Client Disconnected: ${device.address}")
            }
        }

        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            addLog("Service Added: ${service.uuid.toString().take(8)}, Status: $status")
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice, 
            requestId: Int, 
            offset: Int, 
            characteristic: BluetoothGattCharacteristic
        ) {
            addLog("Read Request: ${characteristic.uuid.toString().take(8)}")
            val value = characteristic.value ?: byteArrayOf()
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice, 
            requestId: Int, 
            characteristic: BluetoothGattCharacteristic, 
            preparedWrite: Boolean, 
            responseNeeded: Boolean, 
            offset: Int, 
            value: ByteArray
        ) {
            addLog("Write Request: ${characteristic.uuid.toString().take(8)}, Value: ${value.toHexString()}")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                characteristic.value = value
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = value
            }

            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
        }

        override fun onDescriptorReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, descriptor: BluetoothGattDescriptor) {
            addLog("Descriptor Read: ${descriptor.uuid.toString().take(8)}")
            val value = descriptor.value ?: byteArrayOf()
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
        }

        override fun onDescriptorWriteRequest(device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray) {
            addLog("Descriptor Write: ${descriptor.uuid.toString().take(8)}, Value: ${value.toHexString()}")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                descriptor.value = value
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = value
            }

            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
        }
    }

    private fun addLog(msg: String) {
        val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        _logs.update { it + "[$ts] $msg" }
        Log.d(TAG, msg)
    }

    fun startServer() {
        if (gattServer != null) return
        gattServer = bluetoothManager.openGattServer(context, serverCallback)
        _isServerRunning.value = (gattServer != null)
        if (gattServer != null) {
            addLog("GATT Server Started")
        } else {
            addLog("GATT Server Start FAILED")
        }
    }

    fun stopServer() {
        gattServer?.close()
        gattServer = null
        _isServerRunning.value = false
        _connectedDevices.value = emptySet()
        addLog("GATT Server Stopped")
    }

    fun addService(service: BluetoothGattService) {
        if (gattServer?.addService(service) == true) {
            _serverServices.update { it + service }
        } else {
            addLog("Failed to add service: ${service.uuid}")
        }
    }

    fun clearServices() {
        gattServer?.clearServices()
        _serverServices.value = emptyList()
        addLog("GATT Services Cleared")
    }

    fun notifyCharacteristic(device: BluetoothDevice, characteristic: BluetoothGattCharacteristic, confirm: Boolean) {
        val value = characteristic.value ?: byteArrayOf()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gattServer?.notifyCharacteristicChanged(device, characteristic, confirm, value)
        } else {
            @Suppress("DEPRECATION")
            gattServer?.notifyCharacteristicChanged(device, characteristic, confirm)
        }
    }

    private fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }
}
