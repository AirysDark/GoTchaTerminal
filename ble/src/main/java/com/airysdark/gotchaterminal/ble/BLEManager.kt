package com.airysdark.gotchaterminal.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.airysdark.gotchaterminal.core.DeviceModel
import com.airysdark.gotchaterminal.core.ExtendedDeviceModel
import com.airysdark.gotchaterminal.core.PacketModel
import com.airysdark.gotchaterminal.models.ble.*
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Advanced BLE Manager for GoTcha Reverse Engineering.
 * Optimized for Dialog DA14697 (GoTcha Evolve/Gen2) and DA14580 (Gen1).
 */
@SuppressLint("MissingPermission")
class BLEManager private constructor(context: Context) {

    private val applicationContext = context.applicationContext

    companion object {
        @Volatile
        private var INSTANCE: BLEManager? = null

        fun getInstance(context: Context): BLEManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BLEManager(context).also { INSTANCE = it }
            }
        }
    }

    private val bluetoothManager: BluetoothManager =
        applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bleScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
    private val packetLogger = PacketLogger(applicationContext)

    private var bluetoothGatt: BluetoothGatt? = null
    private val handler = Handler(Looper.getMainLooper())

    @Deprecated("Use core.ExtendedDeviceModel instead")
    data class ExtendedBluetoothDevice(
        val device: BluetoothDevice,
        val rssi: Int,
        val lastSeen: Long = System.currentTimeMillis()
    )

    @Deprecated("Use extendedDevices instead")
    private val _extendedBluetoothDevices = MutableStateFlow<List<ExtendedBluetoothDevice>>(emptyList())
    @Deprecated("Use extendedDevices instead")
    val extendedBluetoothDevices = _extendedBluetoothDevices.asStateFlow()

    private val _extendedDevices = MutableStateFlow<List<ExtendedDeviceModel>>(emptyList())
    val extendedDevices: StateFlow<List<ExtendedDeviceModel>> = _extendedDevices.asStateFlow()

    @Deprecated("Use extendedDevices or scanResults instead")
    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    @Deprecated("Use extendedDevices or scanResults instead")
    val discoveredDevices = _discoveredDevices.asStateFlow()

    @Deprecated("Use scanResults instead")
    private val _scanResultStream = MutableSharedFlow<ScanResult>(extraBufferCapacity = 64)
    @Deprecated("Use scanResults instead")
    val scanResultStream = _scanResultStream.asSharedFlow()

    private val _scanResults = MutableStateFlow<List<DeviceModel>>(emptyList())
    val scanResults: StateFlow<List<DeviceModel>> = _scanResults.asStateFlow()

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

    // --- Global Packet Stream ---
    private val _packetStream = MutableSharedFlow<PacketInfo>(extraBufferCapacity = 128)
    val packetStream = _packetStream.asSharedFlow()

    private val _packetStreamCore = MutableSharedFlow<PacketModel>(extraBufferCapacity = 128)
    val packetStreamCore = _packetStreamCore.asSharedFlow()

    private var activeCaptureSession: BleSession? = null

    enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED
    }

    fun addLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        _logs.update { it + "[$timestamp] $message" }
        Log.d("BLEManager", "[$timestamp] $message")
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            
            @Suppress("DEPRECATION")
            _extendedBluetoothDevices.update { devices ->
                val existing = devices.find { it.device.address == device.address }
                if (existing != null) {
                    devices.map { if (it.device.address == device.address) ExtendedBluetoothDevice(device, result.rssi) else it }
                } else {
                    devices + ExtendedBluetoothDevice(device, result.rssi)
                }
            }

            // Update Core Model List (Source of Truth)
            val model = DeviceModel(
                name = try { device.name } catch (e: SecurityException) { null },
                address = device.address,
                rssi = result.rssi,
                manufacturerData = result.scanRecord?.manufacturerSpecificData?.toString()
            )

            _extendedDevices.update { devices ->
                val existing = devices.find { it.device.address == device.address }
                if (existing != null) {
                    devices.map { if (it.device.address == device.address) ExtendedDeviceModel(model) else it }
                } else {
                    devices + ExtendedDeviceModel(model)
                }
            }

            @Suppress("DEPRECATION")
            _discoveredDevices.update { devices ->
                if (devices.none { it.address == device.address }) {
                    devices + device
                } else {
                    devices
                }
            }
            
            @Suppress("DEPRECATION")
            _scanResultStream.tryEmit(result)

            // Update Scan Results Core (Cumulative list)
            _scanResults.update { devices ->
                if (devices.none { it.address == device.address }) {
                    devices + model
                } else {
                    devices.map { if (it.address == device.address) model else it }
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
                addLog("CONNECTED: ${gatt.device.address}")
                
                activeCaptureSession?.let {
                    it.deviceName = try { gatt.device.name } catch (e: SecurityException) { null }
                    it.deviceAddress = gatt.device.address
                }
                
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _connectionState.value = ConnectionState.DISCONNECTED
                _discoveredServices.value = emptyList()
                _characteristicValues.value = emptyMap()
                _notificationsEnabled.value = emptySet()
                _mtu.value = 23
                addLog("DISCONNECTED (Status: $status)")
                bluetoothGatt?.close()
                bluetoothGatt = null
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _discoveredServices.value = gatt.services
                addLog("SERVICES DISCOVERED (${gatt.services.size})")

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
            } else {
                addLog("SERVICE DISCOVERY FAILED: $status")
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _mtu.value = mtu
                addLog("MTU CHANGED: $mtu")
            } else {
                addLog("MTU CHANGE FAILED: $status")
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            val value = characteristic.value
            handleCharacteristicRead(characteristic, value, status)
        }

        // Modern callback for API 33+
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            handleCharacteristicRead(characteristic, value, status)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val value = characteristic.value ?: return
            handleCharacteristicChanged(characteristic, value)
        }

        // Modern callback for API 33+
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleCharacteristicChanged(characteristic, value)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // WRITE SUCCESS
            } else {
                addLog("WRITE FAILED [${characteristic.uuid.toString().take(8)}]: status $status")
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            val charUuid = descriptor.characteristic.uuid
            if (status == BluetoothGatt.GATT_SUCCESS) {
                @Suppress("DEPRECATION")
                val isEnable = descriptor.value?.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == true ||
                               descriptor.value?.contentEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE) == true
                
                addLog("CCCD WRITE SUCCESS [${charUuid.toString().take(8)}]: ${if (isEnable) "ENABLED" else "DISABLED"}")
                
                if (isEnable) {
                    _notificationsEnabled.update { it + charUuid }
                } else {
                    _notificationsEnabled.update { it - charUuid }
                }
            } else {
                addLog("CCCD WRITE FAILED [${charUuid.toString().take(8)}]: status $status")
            }
        }
    }

    private fun handleCharacteristicRead(characteristic: BluetoothGattCharacteristic, value: ByteArray?, status: Int) {
        if (status == BluetoothGatt.GATT_SUCCESS && value != null) {
            val packet = PacketInfo(
                type = "READ",
                serviceUuid = characteristic.service.uuid.toString(),
                charUuid = characteristic.uuid.toString(),
                data = value.toHexString()
            )
            val corePacket = PacketModel(
                type = "READ",
                serviceUuid = characteristic.service.uuid.toString(),
                uuid = characteristic.uuid.toString(),
                data = value.toHexString()
            )
            packetLogger.log("RX (READ)", characteristic.service.uuid, characteristic.uuid, value)
            _characteristicValues.update { it + (characteristic.uuid to value) }
            addLog("READ RESPONSE [${characteristic.uuid.toString().take(8)}]: ${value.toHexString()}")

            _packetStream.tryEmit(packet)
            _packetStreamCore.tryEmit(corePacket)
            activeCaptureSession?.packets?.add(packet)
        } else {
            addLog("READ FAILED [${characteristic.uuid.toString().take(8)}]: status $status")
        }
    }

    private fun handleCharacteristicChanged(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        val packet = PacketInfo(
            type = "NOTIF",
            serviceUuid = characteristic.service.uuid.toString(),
            charUuid = characteristic.uuid.toString(),
            data = value.toHexString()
        )
        val corePacket = PacketModel(
            type = "NOTIF",
            serviceUuid = characteristic.service.uuid.toString(),
            uuid = characteristic.uuid.toString(),
            data = value.toHexString()
        )
        packetLogger.log("RX (NOTIF)", characteristic.service.uuid, characteristic.uuid, value)
        _characteristicValues.update { it + (characteristic.uuid to value) }
        addLog("NOTIF RECEIVED [${characteristic.uuid.toString().take(8)}]: ${value.toHexString()}")

        _packetStream.tryEmit(packet)
        _packetStreamCore.tryEmit(corePacket)
        activeCaptureSession?.packets?.add(packet)
    }

    fun startSessionCapture(name: String) {
        activeCaptureSession = BleSession(name = name)
        addLog("STARTED CAPTURE SESSION: $name")
    }

    fun stopSessionCapture(): BleSession? {
        val session = activeCaptureSession
        activeCaptureSession = null
        addLog("STOPPED CAPTURE SESSION")
        return session
    }

    fun startScan() {
        if (_isScanning.value) return
        @Suppress("DEPRECATION")
        _discoveredDevices.value = emptyList()
        @Suppress("DEPRECATION")
        _extendedBluetoothDevices.value = emptyList()
        _extendedDevices.value = emptyList()
        _scanResults.value = emptyList()
        _isScanning.value = true
        addLog("STARTING BLE SCAN")
        bleScanner?.startScan(scanCallback)
        handler.postDelayed({ stopScan() }, 10000)
    }

    fun stopScan() {
        if (!_isScanning.value) return
        _isScanning.value = false
        addLog("STOPPING BLE SCAN")
        bleScanner?.stopScan(scanCallback)
    }

    fun connect(device: BluetoothDevice) {
        stopScan()
        _connectionState.value = ConnectionState.CONNECTING
        addLog("CONNECTING TO ${device.address}")
        bluetoothGatt = device.connectGatt(applicationContext, false, gattCallback)
    }

    fun disconnect() {
        addLog("DISCONNECTING...")
        bluetoothGatt?.disconnect()
    }

    fun requestMtu(mtu: Int) {
        addLog("REQUESTING MTU: $mtu")
        bluetoothGatt?.requestMtu(mtu)
    }

    fun clearCharacteristicValue(uuid: UUID) {
        _characteristicValues.update { it - uuid }
    }

    fun readCharacteristic(serviceUuid: UUID, characteristicUuid: UUID) {
        val gatt = bluetoothGatt ?: run { addLog("READ FAILED: Not connected"); return }
        val service = gatt.getService(serviceUuid) ?: run { addLog("READ FAILED: Service $serviceUuid not found"); return }
        val char = service.getCharacteristic(characteristicUuid) ?: run { addLog("READ FAILED: Char $characteristicUuid not found"); return }
        
        addLog("READ REQUEST [${characteristicUuid.toString().take(8)}]")
        gatt.readCharacteristic(char)
    }

    fun writeCharacteristic(
        serviceUuid: UUID,
        characteristicUuid: UUID,
        data: ByteArray,
        writeType: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
    ) {
        val gatt = bluetoothGatt ?: run { addLog("WRITE FAILED: Not connected"); return }
        val service = gatt.getService(serviceUuid) ?: run { addLog("WRITE FAILED: Service not found"); return }
        val char = service.getCharacteristic(characteristicUuid) ?: run { addLog("WRITE FAILED: Char not found"); return }

        // Filter OTA data logs
        if (characteristicUuid != UUID.fromString("457871e8-d516-4ca1-9116-57d0b17b9cb2")) {
            addLog("WRITE REQUEST [${characteristicUuid.toString().take(8)}]: ${data.toHexString()}")
        }
        
        val packet = PacketInfo(
            type = "WRITE",
            serviceUuid = serviceUuid.toString(),
            charUuid = characteristicUuid.toString(),
            data = data.toHexString()
        )
        val corePacket = PacketModel(
            type = "WRITE",
            serviceUuid = serviceUuid.toString(),
            uuid = characteristicUuid.toString(),
            data = data.toHexString()
        )
        packetLogger.log("TX", serviceUuid, characteristicUuid, data)
        _packetStream.tryEmit(packet)
        _packetStreamCore.tryEmit(corePacket)
        activeCaptureSession?.packets?.add(packet)

        writeCharacteristicSafe(gatt, char, data, writeType)
    }

    /**
     * Modern safe wrapper for writing characteristics.
     */
    private fun writeCharacteristicSafe(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        data: ByteArray,
        writeType: Int
    ) {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            gatt.writeCharacteristic(characteristic, data, writeType)
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = data
            characteristic.writeType = writeType
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(characteristic)
        }
    }

    fun toggleNotification(serviceUuid: UUID, characteristicUuid: UUID) {
        val gatt = bluetoothGatt ?: run { addLog("NOTIFY FAILED: Not connected"); return }
        val service = gatt.getService(serviceUuid) ?: run { addLog("NOTIFY FAILED: Service not found"); return }
        val char = service.getCharacteristic(characteristicUuid) ?: run { addLog("NOTIFY FAILED: Char not found"); return }

        val currentlyEnabled = _notificationsEnabled.value.contains(char.uuid)
        val enable = !currentlyEnabled

        addLog("${if (enable) "ENABLING" else "DISABLING"} NOTIFICATIONS [${characteristicUuid.toString().take(8)}]")

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
                    writeDescriptorSafe(gatt, descriptor, value)
                }
            } else {
                addLog("CCCD NOT FOUND for ${characteristicUuid.toString().take(8)}")
            }
        }
    }

    private fun writeDescriptorSafe(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, value: ByteArray) {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            gatt.writeDescriptor(descriptor, value)
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = value
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }

    fun getConnectedDevice(): BluetoothDevice? = bluetoothGatt?.device

    private fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }
}
