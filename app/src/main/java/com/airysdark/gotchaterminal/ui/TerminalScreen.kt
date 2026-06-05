package com.airysdark.gotchaterminal.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airysdark.gotchaterminal.ble.BLEManager
import java.util.*

@Composable
fun TerminalApp(viewModel: TerminalViewModel) {
    val connectionState by viewModel.connectionState.collectAsState()
    val currentScreen by viewModel.currentScreen.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (connectionState == BLEManager.ConnectionState.CONNECTED) {
            when (currentScreen) {
                TerminalViewModel.Screen.COMPARISON -> {
                    val realSession by viewModel.realSession.collectAsState()
                    val espSession by viewModel.espSession.collectAsState()
                    val result by viewModel.comparisonResult.collectAsState()
                    val isCapturing by viewModel.isCapturing.collectAsState()
                    
                    ComparisonScreen(
                        realSession = realSession,
                        espSession = espSession,
                        comparisonResult = result,
                        isCapturing = isCapturing,
                        onBack = { viewModel.navigateTo(TerminalViewModel.Screen.MAIN) },
                        onStartCapture = { viewModel.startCapture(if(it) "Real Go-tcha" else "ESP32") },
                        onStopCapture = { viewModel.stopCapture(it) },
                        onClear = { viewModel.clearComparison() }
                    )
                }
                TerminalViewModel.Screen.FIRMWARE -> FirmwareScreen(viewModel)
                TerminalViewModel.Screen.RESEARCH_MENU -> ResearchMenuScreen(
                    onNavigate = { viewModel.navigateTo(it) },
                    onBack = { viewModel.navigateTo(TerminalViewModel.Screen.MAIN) }
                )
                TerminalViewModel.Screen.CHALLENGE_RESPONSE -> ChallengeResponseScreen(viewModel)
                TerminalViewModel.Screen.AUTH_ANALYSIS -> ChallengeResponseScreen(viewModel) // Reusing for now as they share log structure
                TerminalViewModel.Screen.SECURITY_MONITOR -> SecurityMonitorScreen(viewModel)
                TerminalViewModel.Screen.TERMINAL -> BleTerminalScreen(viewModel)
                else -> BLEExplorerScreen(viewModel)
            }
        } else {
            DeviceScanScreen(viewModel)
        }
    }
}

@Composable
fun FirmwareScreen(viewModel: TerminalViewModel) {
    val selectedFirmware by viewModel.selectedFirmware.collectAsState()
    val internalFirmwares by viewModel.internalFirmwares.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Firmware Update") },
            navigationIcon = {
                IconButton(onClick = { viewModel.navigateTo(TerminalViewModel.Screen.MAIN) }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )

        FirmwarePicker(
            selectedFirmware = selectedFirmware,
            onFirmwareSelected = { viewModel.selectFirmware(it) },
            onInternalFirmwareSelected = { viewModel.selectInternalFirmware(it) },
            internalFirmwares = internalFirmwares
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { viewModel.startOtaUpdate() },
            enabled = selectedFirmware != null,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("START UPDATE")
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun DeviceScanScreen(viewModel: TerminalViewModel) {
    val devices by viewModel.discoveredDevices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "GoTcha Terminal", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { if (isScanning) viewModel.stopScan() else viewModel.startScan() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (isScanning) "Stop Scan" else "Start Scan")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (connectionState == BLEManager.ConnectionState.CONNECTING) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text("Connecting...", modifier = Modifier.align(Alignment.CenterHorizontally))
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(devices) { device ->
                DeviceItem(device = device, onConnect = { viewModel.connect(device) })
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun DeviceItem(device: BluetoothDevice, onConnect: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onConnect() },
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = device.name ?: "Unknown Device", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(text = device.address, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Button(onClick = onConnect) {
                Text("Connect")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BLEExplorerScreen(viewModel: TerminalViewModel) {
    val services by viewModel.discoveredServices.collectAsState()
    val charValues by viewModel.characteristicValues.collectAsState()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val logs by viewModel.logs.collectAsState()
    
    var hexInput by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("GoTcha Explorer") },
            actions = {
                IconButton(onClick = { viewModel.navigateTo(TerminalViewModel.Screen.RESEARCH_MENU) }) {
                    Icon(Icons.Default.Science, contentDescription = "Research Mode")
                }
                IconButton(onClick = { viewModel.navigateTo(TerminalViewModel.Screen.FIRMWARE) }) {
                    Icon(Icons.Default.SystemUpdate, contentDescription = "Firmware Update")
                }
                IconButton(onClick = { viewModel.navigateTo(TerminalViewModel.Screen.COMPARISON) }) {
                    Icon(Icons.Default.CompareArrows, contentDescription = "Comparison Mode")
                }
                TextButton(onClick = { viewModel.disconnect() }) {
                    Text("Disconnect", color = MaterialTheme.colorScheme.error)
                }
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(onClick = { viewModel.readSettings() }, label = { Text("Read Settings") })
            AssistChip(onClick = { viewModel.setTime() }, label = { Text("Sync Time") })
            AssistChip(onClick = { viewModel.readSteps() }, label = { Text("Read Steps") })
            AssistChip(onClick = { viewModel.resetDevice() }, label = { Text("Reset") })
        }

        Divider()

        LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
            items(services) { service ->
                ServiceItem(service, viewModel, charValues, notificationsEnabled, hexInput) { hexInput = it }
            }
        }

        LogWindow(logs)
    }
}

@Composable
fun ServiceItem(
    service: BluetoothGattService,
    viewModel: TerminalViewModel,
    charValues: Map<UUID, ByteArray>,
    notificationsEnabled: Set<UUID>,
    hexInput: String,
    onHexInputChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = "SERVICE: ${service.uuid}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.ExtraBold
        )
        
        service.characteristics.forEach { characteristic ->
            CharacteristicItem(characteristic, service.uuid, viewModel, charValues, notificationsEnabled, hexInput, onHexInputChange)
        }
        Divider(modifier = Modifier.padding(top = 8.dp), thickness = 0.5.dp)
    }
}

@Composable
fun CharacteristicItem(
    char: BluetoothGattCharacteristic,
    serviceUuid: UUID,
    viewModel: TerminalViewModel,
    charValues: Map<UUID, ByteArray>,
    notificationsEnabled: Set<UUID>,
    hexInput: String,
    onHexInputChange: (String) -> Unit
) {
    val value = charValues[char.uuid]
    val isNotifying = notificationsEnabled.contains(char.uuid)
    
    Card(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(text = "CHAR: ${char.uuid}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            
            Row(modifier = Modifier.padding(vertical = 4.dp).horizontalScroll(rememberScrollState())) {
                PropertyTag("READ", char.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0)
                PropertyTag("WRITE", char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0)
                PropertyTag("NOTIFY", char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0)
            }

            if (value != null) {
                Column(modifier = Modifier.fillMaxWidth().background(Color.DarkGray.copy(alpha = 0.05f)).padding(4.dp)) {
                    Text("HEX: ${viewModel.bytesToHex(value)}", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    Text("ASCII: ${viewModel.bytesToAscii(value)}", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (char.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) {
                    IconButton(onClick = { viewModel.readCharacteristic(serviceUuid, char.uuid) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Read")
                    }
                }
                if (char.properties and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
                    IconButton(onClick = { viewModel.toggleNotification(serviceUuid, char.uuid) }) {
                        Icon(if (isNotifying) Icons.Default.NotificationsActive else Icons.Default.Notifications, contentDescription = null)
                    }
                }
                if (char.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                    IconButton(onClick = { viewModel.writeCharacteristic(serviceUuid, char.uuid, hexInput) }) {
                        Icon(Icons.Default.Send, contentDescription = "Write")
                    }
                }
            }
        }
    }
}

@Composable
fun PropertyTag(label: String, enabled: Boolean) {
    Surface(
        modifier = Modifier.padding(end = 4.dp),
        color = if (enabled) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        shape = RoundedCornerShape(4.dp),
        border = if (!enabled) BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f)) else null
    ) {
        Text(text = label, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), fontSize = 8.sp)
    }
}

@Composable
fun LogWindow(logs: List<String>) {
    val listState = rememberLazyListState()
    LaunchedEffect(logs.size) { if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1) }

    Column(modifier = Modifier.fillMaxWidth().height(180.dp).background(Color.Black)) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
            items(logs) { log ->
                Text(text = log, color = Color.Green, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBar(title: @Composable () -> Unit, navigationIcon: @Composable (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    CenterAlignedTopAppBar(title = title, navigationIcon = navigationIcon ?: {}, actions = actions)
}
