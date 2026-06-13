package com.airysdark.gotchaterminal.wear.viewmodel

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.airysdark.gotchaterminal.ble.BLEManager
import com.airysdark.gotchaterminal.ble.GattServerManager
import com.airysdark.gotchaterminal.ble.PacketComparator
import com.airysdark.gotchaterminal.ble.PacketReplayEngine
import com.airysdark.gotchaterminal.core.DeviceModel
import com.airysdark.gotchaterminal.core.PacketModel
import com.airysdark.gotchaterminal.core.sync.SyncConstants
import com.airysdark.gotchaterminal.core.sync.WearSyncManager
import com.airysdark.gotchaterminal.models.ble.AdvertInfo
import com.airysdark.gotchaterminal.models.ble.BleSession
import com.airysdark.gotchaterminal.models.ble.PacketInfo
import com.airysdark.gotchaterminal.protocol.GoTchaProtocol
import com.airysdark.gotchaterminal.protocol.GoTchaUUIDs
import com.airysdark.gotchaterminal.storage.AppDatabase
import com.airysdark.gotchaterminal.storage.entities.SessionCapture
import com.airysdark.gotchaterminal.wear.service.CaptureService
import com.airysdark.gotchaterminal.wear.sync.worker.SyncWorker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.TimeUnit

class WearTerminalViewModel(application: Application) : AndroidViewModel(application) {

    private val bleManager = BLEManager.getInstance(application)
    private val gattServerManager = GattServerManager(application)
    private val replayEngine = PacketReplayEngine(bleManager, gattServerManager)
    private val database = AppDatabase.getDatabase(application)
    private val syncManager = WearSyncManager(application)
    private val workManager = WorkManager.getInstance(application)
    private val protocol = GoTchaProtocol(bleManager)
    private val packetComparator = PacketComparator()

    // Observed States (Consolidated)
    val scanResults: StateFlow<List<DeviceModel>> = bleManager.scanResults
    val discoveredDevices: StateFlow<List<DeviceModel>> = bleManager.scanResults // Alias for UI compatibility
    val isScanning: StateFlow<Boolean> = bleManager.isScanning
    val connectionState: StateFlow<BLEManager.ConnectionState> = bleManager.connectionState
    val discoveredServices = bleManager.discoveredServices
    val characteristicValues = bleManager.characteristicValues
    val logs = bleManager.logs

    // Live terminal stream
    private val _terminalStream = MutableStateFlow<List<String>>(emptyList())
    val terminalStream = _terminalStream.asStateFlow()
    
    // Live Advertisements for Tool Screen (Source: AdvertInfo for serviceUuids)
    private val _recentAdverts = MutableStateFlow<List<AdvertInfo>>(emptyList())
    val recentAdverts = _recentAdverts.asStateFlow()

    // Track recording state via database/service
    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    private val _packetCount = MutableStateFlow(0)
    val packetCount = _packetCount.asStateFlow()

    // Terminal settings
    private val _targetServiceUuid = MutableStateFlow(GoTchaUUIDs.FLASH_SERVICE)
    val targetServiceUuid = _targetServiceUuid.asStateFlow()

    private val _targetCharUuid = MutableStateFlow(GoTchaUUIDs.FLASH_COMMAND)
    val targetCharUuid = _targetCharUuid.asStateFlow()

    // Session Data
    val allSessions: StateFlow<List<SessionCapture>> = database.captureDao().getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Replay State
    val isReplaying = replayEngine.isReplaying
    val replayProgress = replayEngine.replayProgress

    // Diff State
    private val _diffResults = MutableStateFlow<List<String>>(emptyList())
    val diffResults = _diffResults.asStateFlow()

    init {
        observeActiveSession()
        observePacketStream()
        observeScanResults()
    }

    private fun observeActiveSession() {
        viewModelScope.launch {
            allSessions.collect { sessions ->
                val active = sessions.firstOrNull { it.endTime == null }
                _isRecording.value = active != null
                if (active != null) {
                    database.captureDao().getPacketsForSession(active.id).collect { packets ->
                        _packetCount.value = packets.size
                    }
                } else {
                    _packetCount.value = 0
                }
            }
        }
    }

    private fun observePacketStream() {
        viewModelScope.launch {
            bleManager.packetStreamCore.collect { packet: PacketModel ->
                val type = packet.type
                val data = packet.data
                val char = packet.uuid.take(4)
                val entry = "[$type] $char: $data"
                _terminalStream.update { (it + entry).takeLast(20) }
            }
        }
    }

    private fun observeScanResults() {
        viewModelScope.launch {
            bleManager.scanResultStream.collect { result: android.bluetooth.le.ScanResult ->
                val info = AdvertInfo(
                    name = try { result.scanRecord?.deviceName } catch (e: SecurityException) { null },
                    address = result.device.address,
                    rssi = result.rssi,
                    serviceUuids = result.scanRecord?.serviceUuids?.map { it.toString() } ?: emptyList()
                )
                _recentAdverts.update { (listOf(info) + it).distinctBy { it.address }.take(10) }
            }
        }
    }

    fun startScan() = bleManager.startScan()
    fun stopScan() = bleManager.stopScan()

    fun connect(device: DeviceModel) {
        val bluetoothAdapter = getApplication<Application>().getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter
        if (bluetoothAdapter != null) {
            try {
                val remoteDevice = bluetoothAdapter.getRemoteDevice(device.address)
                bleManager.connect(remoteDevice)
            } catch (e: Exception) {
                bleManager.addLog("Error: Failed to connect to ${device.address}")
            }
        }
    }

    fun disconnect() = bleManager.disconnect()

    fun startRecording(name: String) {
        val intent = Intent(getApplication(), CaptureService::class.java).apply {
            action = CaptureService.ACTION_START_RECORDING
            putExtra(CaptureService.EXTRA_SESSION_NAME, name)
        }
        getApplication<Application>().startForegroundService(intent)
    }

    fun stopRecording() {
        val intent = Intent(getApplication(), CaptureService::class.java).apply {
            action = CaptureService.ACTION_STOP_RECORDING
        }
        getApplication<Application>().startService(intent)
        triggerSync()
    }

    // --- BLE Actions ---
    fun readCharacteristic(serviceUuid: UUID, characteristicUuid: UUID) {
        bleManager.readCharacteristic(serviceUuid, characteristicUuid)
    }

    // --- Terminal Actions ---

    fun sendHex(hex: String) {
        val data = hexToBytes(hex) ?: return
        try {
            bleManager.writeCharacteristic(
                UUID.fromString(_targetServiceUuid.value),
                UUID.fromString(_targetCharUuid.value),
                data
            )
        } catch (e: Exception) {
            bleManager.addLog("Error: Invalid UUID")
        }
    }

    fun sendAscii(text: String) {
        val data = text.toByteArray()
        try {
            bleManager.writeCharacteristic(
                UUID.fromString(_targetServiceUuid.value),
                UUID.fromString(_targetCharUuid.value),
                data
            )
        } catch (e: Exception) {
            bleManager.addLog("Error: Invalid UUID")
        }
    }

    fun setTarget(service: String, characteristic: String) {
        _targetServiceUuid.value = service
        _targetCharUuid.value = characteristic
    }

    // --- Presets ---

    fun readSettings() = protocol.readSettings()
    fun readSteps() = protocol.readSteps()
    fun resetDevice() = protocol.resetDevice()
    fun setTime() = protocol.setTime()

    fun readBattery() {
        bleManager.readCharacteristic(
            UUID.fromString(GoTchaUUIDs.BATTERY_SERVICE),
            UUID.fromString(GoTchaUUIDs.BATTERY_LEVEL)
        )
    }

    fun readSoftwareRevision() {
        bleManager.readCharacteristic(
            UUID.fromString(GoTchaUUIDs.DEVICE_INFO),
            UUID.fromString(GoTchaUUIDs.SOFTWARE_REVISION)
        )
    }

    fun enableFlashNotifications() {
        bleManager.toggleNotification(
            UUID.fromString(GoTchaUUIDs.FLASH_SERVICE),
            UUID.fromString(GoTchaUUIDs.FLASH_WRITE)
        )
    }

    // --- Replay ---

    fun replaySession(sessionId: Long) {
        viewModelScope.launch {
            val packets = database.captureDao().getPacketsForSessionList(sessionId).map {
                PacketModel(it.type, it.serviceUuid, it.charUuid, it.data, it.timestamp)
            }
            val target = bleManager.getConnectedDevice() // Replay to connected peripheral
            replayEngine.replaySession(packets, target)
        }
    }

    // --- Research ---
    
    fun performServiceDiff(sessionId: Long) {
        viewModelScope.launch {
            _diffResults.value = listOf("Analyzing...")
            val session = database.captureDao().getSessionById(sessionId) ?: return@launch
            val packets = database.captureDao().getPacketsForSessionList(sessionId).map {
                PacketInfo(it.type, it.serviceUuid, it.charUuid, it.data, it.timestamp)
            }
            
            // Reconstruct a BleSession for the comparison
            val capturedSession = BleSession(
                name = session.name,
                deviceName = session.deviceName,
                deviceAddress = session.deviceAddress,
                packets = packets.toMutableList()
            )
            
            // Reconstruct current session
            val currentSession = BleSession(
                name = "Current",
                deviceName = try { bleManager.getConnectedDevice()?.name } catch (e: SecurityException) { null },
                deviceAddress = bleManager.getConnectedDevice()?.address,
                packets = mutableListOf() // Ideally we'd have a way to get current packets
            )
            
            val result = packetComparator.compare(capturedSession, currentSession)
            _diffResults.value = result.diffs.ifEmpty { listOf("No significant differences found.") }
        }
    }
    
    fun clearDiff() {
        _diffResults.value = emptyList()
    }

    // --- Helpers ---

    private fun hexToBytes(hex: String): ByteArray? {
        return try {
            val s = hex.replace(" ", "").replace(":", "")
            s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        } catch (e: Exception) { null }
    }

    fun triggerSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniqueWork(
            "GotchaSync",
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }

    fun sendSyncStatus(status: String) {
        viewModelScope.launch {
            syncManager.sendMessage(
                SyncConstants.CAPABILITY_PHONE_APP,
                SyncConstants.PATH_SYNC_STATUS,
                status.toByteArray()
            )
        }
    }
}
