package com.airysdark.gotchaterminal.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.*
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.airysdark.gotchaterminal.ble.*
import com.airysdark.gotchaterminal.core.DeviceModel
import com.airysdark.gotchaterminal.core.ExtendedDeviceModel
import com.airysdark.gotchaterminal.core.PacketModel
import com.airysdark.gotchaterminal.core.sync.SyncConstants
import com.airysdark.gotchaterminal.core.sync.WearSyncManager
import com.airysdark.gotchaterminal.models.ble.BleSession
import com.airysdark.gotchaterminal.models.ble.ComparisonResult
import com.airysdark.gotchaterminal.models.firmware.FirmwareInfo
import com.airysdark.gotchaterminal.models.firmware.FirmwareAnalysisResult
import com.airysdark.gotchaterminal.firmware.FirmwareManager
import com.airysdark.gotchaterminal.firmware.ota.SUOTAUpdater
import com.airysdark.gotchaterminal.protocol.GoTchaProtocol
import com.airysdark.gotchaterminal.protocol.GoTchaUUIDs
import com.airysdark.gotchaterminal.protocol.IdentityProtocol
import com.airysdark.gotchaterminal.protocol.IdentityServiceManager
import com.airysdark.gotchaterminal.storage.AppDatabase
import com.airysdark.gotchaterminal.storage.entities.DeviceEntity
import com.airysdark.gotchaterminal.storage.entities.SessionCapture
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.time.Duration.Companion.seconds

/**
 * TerminalViewModel handles the business logic for GoTcha terminal operations,
 * BLE management, firmware analysis, and Wear OS synchronization.
 */
class TerminalViewModel(application: Application, val bleManager: BLEManager) : AndroidViewModel(application) {

    val orange = Color(0xFFFFA500)

    private val protocol = GoTchaProtocol(bleManager)
    private val identityProtocol = IdentityProtocol(bleManager)
    private val identityManager = IdentityServiceManager(bleManager, identityProtocol)
    private val comparator = PacketComparator()
    private val firmwareManager = FirmwareManager.getInstance(application)
    private val otaUpdater = SUOTAUpdater(bleManager)
    val ota = otaUpdater // Alias for instruction compliance
    
    private val database = AppDatabase.getDatabase(application)
    
    // Wear OS Sync Manager
    private val syncManager = WearSyncManager(application)

    // New Managers for Phone-based Lab
    val advManager = AdvertisementManager(application)
    val gattServerManager = GattServerManager(application)
    val packetRecorder = PacketRecorder(application)
    val replayEngine = PacketReplayEngine(bleManager, gattServerManager)

    // --- State Properties ---
    
    @Deprecated("Use extendedDevices or scanResults instead")
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = bleManager.discoveredDevices
    val isScanning: StateFlow<Boolean> = bleManager.isScanning
    val connectionState: StateFlow<BLEManager.ConnectionState> = bleManager.connectionState
    val discoveredServices: StateFlow<List<BluetoothGattService>> = bleManager.discoveredServices
    val characteristicValues: StateFlow<Map<UUID, ByteArray>> = bleManager.characteristicValues
    val notificationsEnabled: StateFlow<Set<UUID>> = bleManager.notificationsEnabled
    val logs: StateFlow<List<String>> = bleManager.logs
    val currentMtu: StateFlow<Int> = bleManager.mtu

    // CORE Wired properties (Source of Truth)
    val scanResults: StateFlow<List<DeviceModel>> = bleManager.scanResults
    val extendedDevices: StateFlow<List<ExtendedDeviceModel>> = bleManager.extendedDevices

    // --- Synced Data ---
    val syncedSessions: StateFlow<List<SessionCapture>> = database.captureDao().getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedDevices: StateFlow<List<DeviceEntity>> = database.deviceDao().getAllDevices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteDevices: StateFlow<List<DeviceEntity>> = database.deviceDao().getFavoriteDevices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Navigation ---
    private val _currentScreen = MutableStateFlow(Screen.MAIN)
    val currentScreen = _currentScreen.asStateFlow()

    enum class Screen { 
        MAIN, COMPARISON, FIRMWARE_UPDATE, FIRMWARE_ANALYSIS, FIRMWARE_BROWSER, 
        RESEARCH_MENU, CHALLENGE_RESPONSE, AUTH_ANALYSIS, SECURITY_MONITOR, CHALLENGE_MONITOR, 
        TERMINAL, EXPLORER, ADV_LAB, GATT_SERVER_LAB, SYNCED_SESSIONS
    }

    // --- Research & Analysis State ---
    private val _researchLogs = MutableStateFlow<List<ResearchLog>>(emptyList())
    val researchLogs = _researchLogs.asStateFlow()

    data class ResearchLog(
        val timestamp: Long,
        val type: String, // READ, WRITE, NOTIF
        val serviceUuid: String,
        val charUuid: String,
        val data: String,
        val sequence: Int
    )

    private var sequenceCounter = 0
    private var isRecordingResearch = false
    private var securityMonitorJob: Job? = null

    // --- Terminal State ---
    private val _terminalServiceUuid = MutableStateFlow("addc3e26-4aa5-4c1a-8a6a-735db4e01c6c")
    val terminalServiceUuid = _terminalServiceUuid.asStateFlow()

    private val _terminalCharUuid = MutableStateFlow("addc3e26-4aa5-4c1a-8a6a-735db4e01c6f")
    val terminalCharUuid = _terminalCharUuid.asStateFlow()

    private val _terminalDataInput = MutableStateFlow("")
    val terminalDataInput = _terminalDataInput.asStateFlow()

    // --- Metrics State ---
    private val _currentRssi = MutableStateFlow<Int?>(null)
    val currentRssi = _currentRssi.asStateFlow()

    private val _sessionStartTime = MutableStateFlow<Long?>(null)
    val sessionStartTime = _sessionStartTime.asStateFlow()

    private val _lastActivityTime = MutableStateFlow<Long?>(null)
    val lastActivityTime = _lastActivityTime.asStateFlow()

    private val _notificationCount = MutableStateFlow(0)
    val notificationCount = _notificationCount.asStateFlow()

    private val _packetCount = MutableStateFlow(0)
    val packetCount = _packetCount.asStateFlow()

    private val _serviceCount = MutableStateFlow(0)
    val serviceCount = _serviceCount.asStateFlow()

    private val _recentEvents = MutableStateFlow<List<String>>(emptyList())
    val recentEvents = _recentEvents.asStateFlow()

    private val sharedPrefs = application.getSharedPreferences("gotcha_prefs", Context.MODE_PRIVATE)

    // --- Firmware State ---
    private val _selectedFirmware = MutableStateFlow<FirmwareInfo?>(null)
    val selectedFirmware = _selectedFirmware.asStateFlow()

    private val _internalFirmwares = MutableStateFlow<List<String>>(emptyList())
    val internalFirmwares = _internalFirmwares.asStateFlow()

    private val _firmwareAnalysis = MutableStateFlow<FirmwareAnalysisResult?>(null)
    val firmwareAnalysis = _firmwareAnalysis.asStateFlow()

    // OTA State exposure
    val otaProgress = otaUpdater.progress
    val otaStatusText = otaUpdater.statusText
    val otaIsBusy = otaUpdater.isBusy
    val otaState = otaUpdater.currentState
    val otaBytesSent = otaUpdater.bytesSent

    // --- Comparison State ---
    private val _realSession = MutableStateFlow<BleSession?>(null)
    val realSession = _realSession.asStateFlow()
    private val _espSession = MutableStateFlow<BleSession?>(null)
    val espSession = _espSession.asStateFlow()
    private val _comparisonResult = MutableStateFlow<ComparisonResult?>(null)
    val comparisonResult = _comparisonResult.asStateFlow()
    private val _isCapturing = MutableStateFlow(false)
    val isCapturing = _isCapturing.asStateFlow()

    // --- Generic BLE Actions state ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _sortByRssi = MutableStateFlow(false)
    val sortByRssi = _sortByRssi.asStateFlow()

    @SuppressLint("MissingPermission")
    val filteredDevices: StateFlow<List<ExtendedDeviceModel>> = combine(bleManager.extendedDevices, _searchQuery, _sortByRssi) { devices: List<ExtendedDeviceModel>, query: String, sort: Boolean ->
        var list = if (query.isEmpty()) {
            devices
        } else {
            devices.filter { extendedDevice -> 
                extendedDevice.device.name?.contains(query, ignoreCase = true) == true || extendedDevice.device.address.contains(query, ignoreCase = true) 
            }
        }
        
        if (sort) {
            list.sortedByDescending { it.device.rssi }
        } else {
            list.sortedBy { it.device.name ?: "Unknown" }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- GoTcha Features (TID: GTxx) ---
    private val _settingsBitmask = MutableStateFlow(0x1F.toByte()) // Default: all on
    val settingsBitmask = _settingsBitmask.asStateFlow()

    init {
        // Collect from the global packet stream for research logging and recording
        viewModelScope.launch {
            bleManager.packetStreamCore.collect { packet: PacketModel ->
                _lastActivityTime.value = System.currentTimeMillis()
                _packetCount.value++
                if (packet.type == "NOTIF") {
                    _notificationCount.value++
                }

                if (isRecordingResearch) {
                    val log = ResearchLog(
                        timestamp = System.currentTimeMillis(),
                        type = packet.type,
                        serviceUuid = packet.serviceUuid ?: "N/A",
                        charUuid = packet.uuid,
                        data = packet.data,
                        sequence = sequenceCounter++
                    )
                    _researchLogs.update { it + log }
                }
                // Also record for packet recorder if active
                packetRecorder.recordPacket(packet)
            }
        }

        // Observe Connection State for Session Timing and Events
        viewModelScope.launch {
            bleManager.connectionState.collect { state ->
                when (state) {
                    BLEManager.ConnectionState.CONNECTED -> {
                        _sessionStartTime.value = System.currentTimeMillis()
                        _notificationCount.value = 0
                        _packetCount.value = 0
                        val device = bleManager.getConnectedDevice()
                        device?.let { connectedDevice ->
                            val deviceName = if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                                try { connectedDevice.name } catch (e: SecurityException) { null }
                            } else null
                            addEvent("Connected to ${deviceName ?: connectedDevice.address}")
                            saveLastDevice(connectedDevice.address)
                            viewModelScope.launch {
                                database.deviceDao().insertDevice(DeviceEntity(connectedDevice.address, deviceName))
                            }
                        }
                    }
                    BLEManager.ConnectionState.DISCONNECTED -> {
                        if (_sessionStartTime.value != null) {
                            addEvent("Disconnected")
                        }
                        _sessionStartTime.value = null
                        _notificationCount.value = 0
                        _packetCount.value = 0
                        _serviceCount.value = 0
                        _currentRssi.value = null
                    }
                    BLEManager.ConnectionState.CONNECTING -> {
                        addEvent("Attempting connection...")
                    }
                }
            }
        }

        // Observe Services for Counter
        viewModelScope.launch {
            bleManager.discoveredServices.collect { services ->
                _serviceCount.value = services.size
            }
        }

        // Observe Scan Results for RSSI of connected device (Unified core model usage)
        viewModelScope.launch {
            bleManager.scanResults.collect { results: List<DeviceModel> ->
                val connected = bleManager.getConnectedDevice()?.address
                results.find { it.address == connected }?.let {
                    _currentRssi.value = it.rssi
                }
            }
        }

        loadInternalFirmwareList()
    }

    private fun addEvent(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _recentEvents.update { (listOf("[$timestamp] $message") + it).take(10) }
    }

    private fun saveLastDevice(address: String) {
        sharedPrefs.edit { putString("last_device_address", address) }
    }

    fun reconnectLastDevice() {
        val address = sharedPrefs.getString("last_device_address", null)
        if (address != null) {
            connect(address)
        } else {
            bleManager.addLog("No last device found to reconnect")
        }
    }

    fun toggleFavorite(address: String, isFavorite: Boolean) {
        viewModelScope.launch {
            database.deviceDao().setFavorite(address, isFavorite)
        }
    }

    private fun loadInternalFirmwareList() {
        viewModelScope.launch {
            val assets = getApplication<Application>().assets.list("firmware") ?: emptyArray()
            _internalFirmwares.value = assets.filter { it.endsWith(".bin") || it.endsWith(".img") }
        }
    }

    fun selectFirmware(uri: Uri) {
        if (firmwareManager.loadFirmware(uri)) {
            updateFirmwareInfo(isInternal = false)
            runAnalysis()
        }
    }

    fun selectInternalFirmware(fileName: String) {
        if (firmwareManager.loadFromAssets(fileName)) {
            updateFirmwareInfo(isInternal = true)
            runAnalysis()
        }
    }

    private fun updateFirmwareInfo(isInternal: Boolean) {
        val analysis = firmwareManager.analyze()
        _selectedFirmware.value = FirmwareInfo(
            name = firmwareManager.getFirmwareName(),
            size = firmwareManager.getFirmwareSize(),
            crc32 = analysis?.crc32 ?: "",
            sha256 = analysis?.sha256 ?: "",
            isInternal = isInternal
        )
    }

    fun runAnalysis() {
        _firmwareAnalysis.value = firmwareManager.analyze()
    }

    fun startOtaUpdate() {
        val bytes = firmwareManager.getFirmwareBytes()
        if (bytes == null) {
            bleManager.addLog("OTA: No firmware file loaded")
            return
        }
        viewModelScope.launch {
            otaUpdater.startUpdate(bytes)
        }
    }

    fun startCapture(name: String) {
        bleManager.startSessionCapture(name)
        _isCapturing.value = true
    }

    fun stopCapture(isReal: Boolean) {
        val session: BleSession? = bleManager.stopSessionCapture()
        if (isReal) _realSession.value = session else _espSession.value = session
        _isCapturing.value = false
        checkAndCompare()
    }

    private fun checkAndCompare() {
        val real = _realSession.value
        val esp = _espSession.value
        if (real != null && esp != null) {
            _comparisonResult.value = comparator.compare(real, esp)
        }
    }

    fun clearComparison() {
        _realSession.value = null
        _espSession.value = null
        _comparisonResult.value = null
    }

    fun updateSearchQuery(query: String) { _searchQuery.value = query }
    fun toggleSortByRssi() { _sortByRssi.value = !_sortByRssi.value }

    fun updateSetting(bit: Int, enabled: Boolean) {
        var current = _settingsBitmask.value.toInt()
        current = if (enabled) {
            current or (1 shl bit)
        } else {
            current and (1 shl bit).inv()
        }
        _settingsBitmask.value = current.toByte()
        try {
            if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                protocol.writeSettings(_settingsBitmask.value)
            } else {
                bleManager.addLog("Permission denied: BLUETOOTH_CONNECT")
            }
        } catch (e: SecurityException) {
            bleManager.addLog("Security error: ${e.message}")
        }
    }

    fun toggleVibration(enabled: Boolean) = updateSetting(3, enabled)
    fun toggleAutoCatchNew(enabled: Boolean) = updateSetting(1, enabled)
    fun toggleAutoCatchKnown(enabled: Boolean) = updateSetting(0, enabled)
    fun toggleAutoSpin(enabled: Boolean) = updateSetting(2, enabled)
    fun toggleScreen(enabled: Boolean) = updateSetting(4, enabled)

    fun startScan() {
        if (hasPermission(Manifest.permission.BLUETOOTH_SCAN) && hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            bleManager.startScan()
        } else {
            bleManager.addLog("Scan failed: Missing permissions")
        }
    }

    fun stopScan() = bleManager.stopScan()

    fun connect(device: BluetoothDevice) {
        if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            try {
                bleManager.connect(device)
            } catch (e: SecurityException) {
                bleManager.addLog("Connection failed: SecurityException")
            }
        } else {
            bleManager.addLog("Connection failed: Missing permission")
        }
    }

    fun connect(address: String) {
        val bluetoothAdapter = getApplication<Application>().getSystemService(BluetoothManager::class.java)?.adapter
        if (bluetoothAdapter != null) {
            try {
                if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                    val device = bluetoothAdapter.getRemoteDevice(address)
                    connect(device)
                } else {
                    bleManager.addLog("Permission denied: BLUETOOTH_CONNECT")
                }
            } catch (e: SecurityException) {
                bleManager.addLog("Security error: ${e.message}")
            } catch (e: Exception) {
                bleManager.addLog("Failed to get remote device: $address")
            }
        }
    }

    fun disconnect() = bleManager.disconnect()
    
    fun readTerminalCharacteristic() {
        try {
            val sUuid = UUID.fromString(_terminalServiceUuid.value)
            val cUuid = UUID.fromString(_terminalCharUuid.value)
            if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                bleManager.readCharacteristic(sUuid, cUuid)
            } else {
                bleManager.addLog("Read failed: Missing permission")
            }
        } catch (e: SecurityException) {
            bleManager.addLog("Read failed: SecurityException")
        } catch (e: Exception) {
            bleManager.addLog("ERROR: Invalid UUID format")
        }
    }

    fun writeTerminalCharacteristic() {
        try {
            val sUuid = UUID.fromString(_terminalServiceUuid.value)
            val cUuid = UUID.fromString(_terminalCharUuid.value)
            val data = hexToBytes(_terminalDataInput.value)
            if (data != null) {
                if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                    bleManager.writeCharacteristic(sUuid, cUuid, data)
                } else {
                    bleManager.addLog("Write failed: Missing permission")
                }
            } else {
                bleManager.addLog("ERROR: Invalid Hex Data")
            }
        } catch (e: SecurityException) {
            bleManager.addLog("Write failed: SecurityException")
        } catch (e: Exception) {
            bleManager.addLog("ERROR: Invalid UUID or Data format")
        }
    }

    fun toggleTerminalNotification() {
        try {
            val sUuid = UUID.fromString(_terminalServiceUuid.value)
            val cUuid = UUID.fromString(_terminalCharUuid.value)
            if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                bleManager.toggleNotification(sUuid, cUuid)
            } else {
                bleManager.addLog("Notify failed: Missing permission")
            }
        } catch (e: SecurityException) {
            bleManager.addLog("Notify failed: SecurityException")
        } catch (e: Exception) {
            bleManager.addLog("ERROR: Invalid UUID format")
        }
    }

    fun updateTerminalServiceUuid(uuid: String) { _terminalServiceUuid.value = uuid }
    fun updateTerminalCharUuid(uuid: String) { _terminalCharUuid.value = uuid }
    fun updateTerminalDataInput(data: String) { _terminalDataInput.value = data }

    fun navigateTo(screen: Screen) { _currentScreen.value = screen }

    fun clearResearchLogs() {
        _researchLogs.value = emptyList()
        sequenceCounter = 0
    }

    fun startChallengeResponseAnalysis() {
        clearResearchLogs()
        isRecordingResearch = true
    }

    fun startAuthAnalysis() {
        clearResearchLogs()
        isRecordingResearch = true
    }

    fun startSecurityMonitor() {
        clearResearchLogs()
        isRecordingResearch = true
        securityMonitorJob?.cancel()
        securityMonitorJob = viewModelScope.launch {
            delay(30.seconds)
            isRecordingResearch = false
        }
    }

    fun stopResearchRecording() {
        isRecordingResearch = false
    }

    fun exportResearchLogs() {
        try {
            val json = JSONArray()
            _researchLogs.value.forEach { log ->
                val obj = JSONObject()
                obj.put("ts", log.timestamp)
                obj.put("type", log.type)
                obj.put("svc", log.serviceUuid)
                obj.put("char", log.charUuid)
                obj.put("data", log.data)
                obj.put("seq", log.sequence)
                json.put(obj)
            }
            val fileName = "research_log_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.json"
            val file = File(getApplication<Application>().getExternalFilesDir(null), fileName)
            file.writeText(json.toString(2))
            bleManager.addLog("Exported research logs to $fileName")
        } catch (e: Exception) {
            Log.e("TerminalViewModel", "Failed to export logs", e)
        }
    }

    fun readMac() {
        updateTerminalServiceUuid(IdentityProtocol.SERVICE_UUID.toString())
        updateTerminalCharUuid(IdentityProtocol.READ_ADDR_UUID.toString())
        if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            identityProtocol.readMacAddress()
        }
    }

    fun readAdvert() {
        updateTerminalServiceUuid(IdentityProtocol.SERVICE_UUID.toString())
        updateTerminalCharUuid(IdentityProtocol.READ_ADVERT_UUID.toString())
        if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            identityProtocol.readAdvertisementData()
        }
    }

    fun readStatus() {
        updateTerminalServiceUuid(IdentityProtocol.SERVICE_UUID.toString())
        updateTerminalCharUuid(IdentityProtocol.STATUS_UUID.toString())
        if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            identityProtocol.readStatus()
        }
    }

    fun readKey() {
        updateTerminalServiceUuid(IdentityProtocol.SERVICE_UUID.toString())
        updateTerminalCharUuid(IdentityProtocol.KEY_UUID.toString())
        if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            identityProtocol.readKey()
        }
    }

    fun readBlob() {
        updateTerminalServiceUuid(IdentityProtocol.SERVICE_UUID.toString())
        updateTerminalCharUuid(IdentityProtocol.BLOB_UUID.toString())
        if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            identityProtocol.readBlob()
        }
    }

    fun dumpIdentity() {
        viewModelScope.launch {
            if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                identityManager.dumpIdentityData(getApplication())
            }
        }
    }

    fun readConnectedMac() {
        val device = bleManager.getConnectedDevice()
        if (device != null) {
            if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                try {
                    bleManager.addLog("CONNECTED DEVICE MAC: ${device.address}")
                } catch (e: SecurityException) {
                    bleManager.addLog("Security error: missing BLUETOOTH_CONNECT")
                }
            }
        }
    }

    fun saveValueToFile(name: String, format: String) {
        val charUuid = UUID.fromString(_terminalCharUuid.value)
        val value = characteristicValues.value[charUuid]
        if (value == null) {
            bleManager.addLog("No value found for $charUuid to save")
            return
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "${name}_$timestamp.$format"
        val file = File(getApplication<Application>().getExternalFilesDir(null), fileName)
        
        when (format) {
            "bin" -> file.writeBytes(value)
            "txt" -> file.writeText(bytesToHex(value))
            "json" -> {
                val obj = JSONObject()
                obj.put("uuid", charUuid.toString())
                obj.put("hex", bytesToHex(value))
                obj.put("ts", System.currentTimeMillis())
                file.writeText(obj.toString(2))
            }
        }
        bleManager.addLog("Saved $name to $fileName")
    }

    fun readCharacteristic(serviceUuid: UUID, charUuid: UUID) {
        if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            bleManager.readCharacteristic(serviceUuid, charUuid)
        }
    }

    fun writeCharacteristic(serviceUuid: UUID, charUuid: UUID, hexData: String) {
        if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            hexToBytes(hexData)?.let { bleManager.writeCharacteristic(serviceUuid, charUuid, it) }
        }
    }

    fun toggleNotification(serviceUuid: UUID, characteristicUuid: UUID) {
        if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            bleManager.toggleNotification(serviceUuid, characteristicUuid)
        }
    }

    fun readSettings() {
        if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            protocol.readSettings()
        }
    }

    fun setTime() {
        if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            protocol.setTime()
        }
    }

    fun readSteps() {
        if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            protocol.readSteps()
        }
    }

    fun resetDevice() {
        if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            protocol.resetDevice()
        }
    }

    fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
    fun bytesToAscii(bytes: ByteArray): String = bytes.map { byteVal -> 
        val intVal = byteVal.toInt() and 0xFF
        if (intVal in 32..126) intVal.toChar() else '.' 
    }.joinToString("")

    fun hexToBytes(hex: String): ByteArray? {
        return try {
            val s = hex.replace(" ", "").replace(":", "")
            s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        } catch (e: Exception) { null }
    }

    fun getHexView(offset: Int, length: Int) = firmwareManager.getHexView(offset, length)
    
    fun exportAnalysisReport() {
        val report = firmwareManager.exportReport()
        val fileName = "firmware_report_${System.currentTimeMillis()}.txt"
        val file = File(getApplication<Application>().getExternalFilesDir(null), fileName)
        file.writeText(report)
        bleManager.addLog("Analysis report exported to $fileName")
    }

    fun replayCurrentSession() {
        val packets: List<PacketModel> = packetRecorder.recordedPackets.value
        val target = bleManager.getConnectedDevice()
        replayEngine.replaySession(packets, target)
    }

    fun emulateProfile(name: String) {
        gattServerManager.clearServices()
        when (name) {
            "GoTcha Evolve" -> {
                // Identity Service
                val idService = BluetoothGattService(UUID.fromString(GoTchaUUIDs.IDENTITY_SERVICE), BluetoothGattService.SERVICE_TYPE_PRIMARY)
                idService.addCharacteristic(BluetoothGattCharacteristic(UUID.fromString(GoTchaUUIDs.IDENTITY_READ_ADDR), BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ))
                idService.addCharacteristic(BluetoothGattCharacteristic(UUID.fromString(GoTchaUUIDs.IDENTITY_READ_ADVERT), BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ))
                idService.addCharacteristic(BluetoothGattCharacteristic(UUID.fromString(GoTchaUUIDs.IDENTITY_STATUS), BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY, BluetoothGattCharacteristic.PERMISSION_READ))
                idService.addCharacteristic(BluetoothGattCharacteristic(UUID.fromString(GoTchaUUIDs.IDENTITY_KEY), BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ))
                idService.addCharacteristic(BluetoothGattCharacteristic(UUID.fromString(GoTchaUUIDs.IDENTITY_BLOB), BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ))
                gattServerManager.addService(idService)

                // Flash Service
                val flashService = BluetoothGattService(UUID.fromString(GoTchaUUIDs.FLASH_SERVICE), BluetoothGattService.SERVICE_TYPE_PRIMARY)
                flashService.addCharacteristic(BluetoothGattCharacteristic(UUID.fromString(GoTchaUUIDs.FLASH_COMMAND), BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_WRITE))
                flashService.addCharacteristic(BluetoothGattCharacteristic(UUID.fromString(GoTchaUUIDs.FLASH_WRITE), BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or BluetoothGattCharacteristic.PROPERTY_NOTIFY, BluetoothGattCharacteristic.PERMISSION_WRITE))
                flashService.addCharacteristic(BluetoothGattCharacteristic(UUID.fromString(GoTchaUUIDs.FLASH_STATUS), BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY, BluetoothGattCharacteristic.PERMISSION_READ))
                gattServerManager.addService(flashService)

                // Battery Service
                val battService = BluetoothGattService(UUID.fromString(GoTchaUUIDs.BATTERY_SERVICE), BluetoothGattService.SERVICE_TYPE_PRIMARY)
                battService.addCharacteristic(BluetoothGattCharacteristic(UUID.fromString(GoTchaUUIDs.BATTERY_LEVEL), BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY, BluetoothGattCharacteristic.PERMISSION_READ))
                gattServerManager.addService(battService)
            }
            "Pokemon GO Plus" -> {
                // Standard PGP Service
                val pgpService = BluetoothGattService(UUID.fromString("0000fee5-0000-1000-8000-00805f9b34fb"), BluetoothGattService.SERVICE_TYPE_PRIMARY)
                // PGP Central characteristic for writes/notifs
                pgpService.addCharacteristic(BluetoothGattCharacteristic(UUID.fromString("00002a06-0000-1000-8000-00805f9b34fb"), BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_NOTIFY, BluetoothGattCharacteristic.PERMISSION_WRITE))
                gattServerManager.addService(pgpService)
            }
        }
        bleManager.addLog("Emulating Profile: $name")
    }

    fun getPacketsForSession(sessionId: Long): Flow<List<com.airysdark.gotchaterminal.storage.entities.PacketCapture>> {
        return database.captureDao().getPacketsForSession(sessionId)
    }

    fun sendStartScanToWatch() {
        viewModelScope.launch {
            syncManager.sendMessage(SyncConstants.CAPABILITY_WEAR_APP, SyncConstants.PATH_START_SCAN)
        }
    }

    fun sendStopScanToWatch() {
        viewModelScope.launch {
            syncManager.sendMessage(SyncConstants.CAPABILITY_WEAR_APP, SyncConstants.PATH_STOP_SCAN)
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            getApplication(),
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }
}
