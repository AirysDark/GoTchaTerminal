package com.airysdark.gotchaterminal.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.*

@SuppressLint("MissingPermission")
class BLEManager(private val context: Context) {

    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bleScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
    private val packetLogger = PacketLogger(context)

    private var bluetoothGatt: BluetoothGatt? = null
    private val handler = Handler(Looper.getMainLooper())

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState = _connectionState.asStateFlow()

    private val _discoveredServices = MutableStateFlow<List<BluetoothGattService>>(emptyList())
    val discoveredServices = _discoveredServices.asStateFlow()

    private val _characteristicValues = MutableStateFlow<Map<UUID, ByteArray>>(emptyMap())
    val characteristicValues = _characteristicValues.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow<Set<UUID>>(emptySet())
    val notificationsEnabled = _notificationsEnabled.asStateFlow()

    private val _mtu = MutableStateFlow(23)
    val mtu = _mtu.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs = _logs.asStateFlow()

    // --- Research & Global Packet Stream ---
    private val _packetStream = MutableSharedFlow<PacketInfo>(extraBufferCapacity = 64)
    val packetStream = _packetStream.asSharedFlow()

    // --- Comparison Mode Support ---
    private var activeCaptureSession: BleSession? = null

    enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED
    }

    private fun addLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        _logs.update { it + "[$timestamp] $message" }
        Log.d("BLEManager", "[$timestamp] $message")
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            _discoveredDevices.update { devices ->
                if (devices.none { it.address == device.address }) {
                    devices + device
                } else {
                    devices
                }
            }
            
            activeCaptureSession?.let { session ->
                val advert = AdvertInfo(
                    name = result.scanRecord?.deviceName,
                    address = device.address,
                    rssi = result.rssi,
                    serviceUuids = result.scanRecord?.serviceUuids?.map { it.toString() } ?: emptyList()
                )
                if (session.advertisements.none { it.address == advert.address }) {
                    session.advertisements.add(advert)
                }
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _connectionState.value = ConnectionState.CONNECTED
                addLog("Connected to ${gatt.device.address}")
                
                activeCaptureSession?.let {
                    it.deviceName = gatt.device.name
                    it.deviceAddress = gatt.device.address
                }
                
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _connectionState.value = ConnectionState.DISCONNECTED
                _discoveredServices.value = emptyList()
                _characteristicValues.value = emptyMap()
                _notificationsEnabled.value = emptySet()
                addLog("Disconnected")
                bluetoothGatt?.close()
                bluetoothGatt = null
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _discoveredServices.value = gatt.services
                addLog("Services discovered")
                
                activeCaptureSession?.let { session ->
                    session.services.clear()
                    gatt.services.forEach { service ->
                        session.services.add(ServiceInfo(
                            uuid = service.uuid.toString(),
                            characteristics = service.characteristics.map { 
                                CharInfo(it.uuid.toString(), it.properties)
                            }
                        ))
                    }
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _mtu.value = mtu
                addLog("MTU changed to $mtu")
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val packet = PacketInfo(
                    type = "READ",
                    serviceUuid = characteristic.service.uuid.toString(),
                    charUuid = characteristic.uuid.toString(),
                    data = value.toHexString()
                )
                packetLogger.log("RX (READ)", characteristic.uuid, value)
                _characteristicValues.update { it + (characteristic.uuid to value) }
                addLog("READ ${characteristic.uuid.toString().take(8)}: ${value.toHexString()}")
                
                _packetStream.tryEmit(packet)
                activeCaptureSession?.packets?.add(packet)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            val packet = PacketInfo(
                type = "NOTIF",
                serviceUuid = characteristic.service.uuid.toString(),
                charUuid = characteristic.uuid.toString(),
                data = value.toHexString()
            )
            packetLogger.log("RX (NOTIF)", characteristic.uuid, value)
            _characteristicValues.update { it + (characteristic.uuid to value) }
            addLog("NOTIF ${characteristic.uuid.toString().take(8)}: ${value.toHexString()}")
            
            _packetStream.tryEmit(packet)
            activeCaptureSession?.packets?.add(packet)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                addLog("WRITE SUCCESS: ${characteristic.uuid.toString().take(8)}")
            }
        }
    }

    fun startSessionCapture(name: String) {
        activeCaptureSession = BleSession(name = name)
        addLog("Started session capture: $name")
    }

    fun stopSessionCapture(): BleSession? {
        val session = activeCaptureSession
        activeCaptureSession = null
        addLog("Stopped session capture")
        return session
    }

    fun startScan() {
        if (_isScanning.value) return
        _discoveredDevices.value = emptyList()
        _isScanning.value = true
        addLog("Starting scan")
        bleScanner?.startScan(scanCallback)
        handler.postDelayed({ stopScan() }, 10000)
    }

    fun stopScan() {
        if (!_isScanning.value) return
        _isScanning.value = false
        addLog("Stopping scan")
        bleScanner?.stopScan(scanCallback)
    }

    fun connect(device: BluetoothDevice) {
        stopScan()
        _connectionState.value = ConnectionState.CONNECTING
        addLog("Connecting to ${device.address}")
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    fun disconnect() {
        addLog("Disconnecting...")
        bluetoothGatt?.disconnect()
    }

    fun requestMtu(mtu: Int) {
        bluetoothGatt?.requestMtu(mtu)
    }

    fun readCharacteristic(serviceUuid: UUID, characteristicUuid: UUID) {
        val service = bluetoothGatt?.getService(serviceUuid)
        val char = service?.getCharacteristic(characteristicUuid)
        if (char != null) {
            bluetoothGatt?.readCharacteristic(char)
        }
    }

    fun writeCharacteristic(serviceUuid: UUID, characteristicUuid: UUID, data: ByteArray) {
        val service = bluetoothGatt?.getService(serviceUuid)
        val char = service?.getCharacteristic(characteristicUuid)
        if (char != null) {
            val packet = PacketInfo(
                type = "WRITE",
                serviceUuid = serviceUuid.toString(),
                charUuid = characteristicUuid.toString(),
                data = data.toHexString()
            )
            packetLogger.log("TX", characteristicUuid, data)
            _packetStream.tryEmit(packet)
            activeCaptureSession?.packets?.add(packet)
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                bluetoothGatt?.writeCharacteristic(char, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            } else {
                char.value = data
                bluetoothGatt?.writeCharacteristic(char)
            }
        }
    }

    fun toggleNotification(serviceUuid: UUID, characteristicUuid: UUID) {
        val gatt = bluetoothGatt ?: return
        val service = gatt.getService(serviceUuid)
        val char = service?.getCharacteristic(characteristicUuid) ?: return

        val currentlyEnabled = _notificationsEnabled.value.contains(char.uuid)
        val enable = !currentlyEnabled

        if (gatt.setCharacteristicNotification(char, enable)) {
            val descriptor = char.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
            if (descriptor != null) {
                val value = if (enable) {
                    if (char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    } else if (char.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) {
                        BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                    } else null
                } else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE

                if (value != null) {
                    descriptor.value = value
                    gatt.writeDescriptor(descriptor)
                    
                    if (enable) _notificationsEnabled.update { it + char.uuid }
                    else _notificationsEnabled.update { it - char.uuid }
                }
            }
        }
    }

    private fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }
}
