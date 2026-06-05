package com.airysdark.gotchaterminal.ui

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattService
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.airysdark.gotchaterminal.ble.*
import com.airysdark.gotchaterminal.firmware.FirmwareInfo
import com.airysdark.gotchaterminal.firmware.FirmwareManager
import com.airysdark.gotchaterminal.ota.SUOTAUpdater
import com.airysdark.gotchaterminal.protocol.GoTchaProtocol
import com.airysdark.gotchaterminal.protocol.IdentityProtocol
import com.airysdark.gotchaterminal.protocol.IdentityServiceManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class TerminalViewModel(application: Application, val bleManager: BLEManager) : AndroidViewModel(application) {

    private val protocol = GoTchaProtocol(bleManager)
    private val identityProtocol = IdentityProtocol(bleManager)
    private val identityManager = IdentityServiceManager(bleManager, identityProtocol)
    private val comparator = PacketComparator()
    private val firmwareManager = FirmwareManager(application)

    val discoveredDevices: StateFlow<List<BluetoothDevice>> = bleManager.discoveredDevices
    val isScanning: StateFlow<Boolean> = bleManager.isScanning
    val connectionState: StateFlow<BLEManager.ConnectionState> = bleManager.connectionState
    val discoveredServices: StateFlow<List<BluetoothGattService>> = bleManager.discoveredServices
    val characteristicValues: StateFlow<Map<UUID, ByteArray>> = bleManager.characteristicValues
    val notificationsEnabled: StateFlow<Set<UUID>> = bleManager.notificationsEnabled
    val logs: StateFlow<List<String>> = bleManager.logs

    // --- Navigation ---
    private val _currentScreen = MutableStateFlow(Screen.MAIN)
    val currentScreen = _currentScreen.asStateFlow()

    enum class Screen { 
        MAIN, COMPARISON, FIRMWARE, RESEARCH_MENU, 
        CHALLENGE_RESPONSE, AUTH_ANALYSIS, SECURITY_MONITOR, TERMINAL 
    }
    fun navigateTo(screen: Screen) { _currentScreen.value = screen }

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

    init {
        // Collect from the global packet stream for research logging
        viewModelScope.launch {
            bleManager.packetStream.collect { packet ->
                if (isRecordingResearch) {
                    val log = ResearchLog(
                        timestamp = System.currentTimeMillis(),
                        type = packet.type,
                        serviceUuid = packet.serviceUuid,
                        charUuid = packet.charUuid,
                        data = packet.data,
                        sequence = sequenceCounter++
                    )
                    _researchLogs.value = _researchLogs.value + log
                }
            }
        }
    }

    fun clearResearchLogs() {
        _researchLogs.value = emptyList()
        sequenceCounter = 0
    }

    fun startChallengeResponseAnalysis() {
        clearResearchLogs()
        isRecordingResearch = true
    }

    fun startAuthAnalysis() {
        // Auth analysis specifically targets the start of a connection.
        // If already connected, we'll just start recording now.
        clearResearchLogs()
        isRecordingResearch = true
    }

    fun startSecurityMonitor() {
        clearResearchLogs()
        isRecordingResearch = true
        securityMonitorJob?.cancel()
        securityMonitorJob = viewModelScope.launch {
            delay(30000)
            isRecordingResearch = false
        }
    }

    fun stopResearchRecording() {
        isRecordingResearch = false
    }

    fun exportResearchLogs() {
        val json = JSONArray()
        _researchLogs.value.forEach {
            val obj = JSONObject()
            obj.put("ts", it.timestamp)
            obj.put("type", it.type)
            obj.put("svc", it.serviceUuid)
            obj.put("char", it.charUuid)
            obj.put("data", it.data)
            obj.put("seq", it.sequence)
            json.put(obj)
        }
        val fileName = "research_log_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.json"
        val file = File(getApplication<Application>().getExternalFilesDir(null), fileName)
        file.writeText(json.toString(2))
    }

    // --- Terminal State ---
    private val _terminalServiceUuid = MutableStateFlow("addc3e26-4aa5-4c1a-8a6a-735db4e01c6c")
    val terminalServiceUuid = _terminalServiceUuid.asStateFlow()

    private val _terminalCharUuid = MutableStateFlow("addc3e26-4aa5-4c1a-8a6a-735db4e01c6f")
    val terminalCharUuid = _terminalCharUuid.asStateFlow()

    fun updateTerminalUuids(service: String, char: String) {
        _terminalServiceUuid.value = service
        _terminalCharUuid.value = char
    }

    // --- Identity Service Actions ---
    fun readMac() = identityProtocol.readMacAddress()
    fun readAdvert() = identityProtocol.readAdvertisementData()
    fun readStatus() = identityProtocol.readStatus()
    fun dumpIdentity() {
        viewModelScope.launch {
            identityManager.dumpIdentityData(getApplication())
        }
    }

    // --- Firmware State ---
    private val _selectedFirmware = MutableStateFlow<FirmwareInfo?>(null)
    val selectedFirmware = _selectedFirmware.asStateFlow()

    private val _internalFirmwares = MutableStateFlow<List<String>>(emptyList())
    val internalFirmwares = _internalFirmwares.asStateFlow()

    init {
        loadInternalFirmwareList()
    }

    private fun loadInternalFirmwareList() {
        viewModelScope.launch {
            val assets = getApplication<Application>().assets.list("firmware") ?: emptyArray()
            _internalFirmwares.value = assets.filter { it.endsWith(".bin") }
        }
    }

    fun selectFirmware(uri: Uri) {
        if (firmwareManager.loadFirmware(uri)) {
            updateFirmwareInfo(false)
        }
    }

    fun selectInternalFirmware(fileName: String) {
        if (firmwareManager.loadFromAssets(fileName)) {
            updateFirmwareInfo(true)
        }
    }

    private fun updateFirmwareInfo(isInternal: Boolean) {
        _selectedFirmware.value = FirmwareInfo(
            name = firmwareManager.getFirmwareName(),
            size = firmwareManager.getFirmwareSize(),
            crc32 = firmwareManager.calculateCRC32(),
            sha256 = firmwareManager.calculateSHA256(),
            isInternal = isInternal
        )
    }

    fun startOtaUpdate() {
        val bytes = firmwareManager.getFirmwareBytes() ?: return
        val updater = SUOTAUpdater(bleManager)
        viewModelScope.launch {
            updater.startUpdate(bytes)
        }
    }

    // --- Comparison State ---
    private val _realSession = MutableStateFlow<BleSession?>(null)
    val realSession = _realSession.asStateFlow()
    private val _espSession = MutableStateFlow<BleSession?>(null)
    val espSession = _espSession.asStateFlow()
    private val _comparisonResult = MutableStateFlow<ComparisonResult?>(null)
    val comparisonResult = _comparisonResult.asStateFlow()
    private val _isCapturing = MutableStateFlow(false)
    val isCapturing = _isCapturing.asStateFlow()

    fun startCapture(name: String) {
        bleManager.startSessionCapture(name)
        _isCapturing.value = true
    }

    fun stopCapture(isReal: Boolean) {
        val session = bleManager.stopSessionCapture()
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

    // --- Generic BLE Actions ---
    fun startScan() = bleManager.startScan()
    fun stopScan() = bleManager.stopScan()
    fun connect(device: BluetoothDevice) = bleManager.connect(device)
    fun disconnect() = bleManager.disconnect()
    fun readCharacteristic(serviceUuid: UUID, charUuid: UUID) = bleManager.readCharacteristic(serviceUuid, charUuid)
    fun writeCharacteristic(serviceUuid: UUID, charUuid: UUID, hexData: String) {
        hexToBytes(hexData)?.let { bleManager.writeCharacteristic(serviceUuid, charUuid, it) }
    }
    fun toggleNotification(serviceUuid: UUID, characteristicUuid: UUID) = bleManager.toggleNotification(serviceUuid, characteristicUuid)

    fun readSettings() = protocol.readSettings()
    fun setTime() = protocol.setTime()
    fun readSteps() = protocol.readSteps()
    fun resetDevice() = protocol.resetDevice()

    fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
    fun bytesToAscii(bytes: ByteArray): String = bytes.map { if (it in 32..126) it.toChar() else '.' }.joinToString("")
    fun hexToBytes(hex: String): ByteArray? {
        return try {
            val s = hex.replace(" ", "")
            s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        } catch (e: Exception) { null }
    }
}
