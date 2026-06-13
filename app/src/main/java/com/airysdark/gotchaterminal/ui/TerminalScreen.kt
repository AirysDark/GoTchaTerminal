package com.airysdark.gotchaterminal.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.airysdark.gotchaterminal.ble.BLEManager
import com.airysdark.gotchaterminal.firmware.FirmwareManager
import com.airysdark.gotchaterminal.firmware.ota.SUOTAUpdater
import com.airysdark.gotchaterminal.models.firmware.FirmwareAnalysisResult
import com.airysdark.gotchaterminal.protocol.UUIDDatabase
import java.text.SimpleDateFormat
import java.util.*

val Orange = Color(0xFFFFA500)

@Composable
fun TerminalApp(viewModel: TerminalViewModel) {
    val navController = rememberNavController()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        MainScaffold(viewModel, navController)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(viewModel: TerminalViewModel, navController: NavHostController) {
    val connectionState by viewModel.connectionState.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "home"

    // Primary Tabs
    val tabs = listOf(
        "home" to Pair("Home", Icons.Default.Home),
        "devices" to Pair("Devices", Icons.Default.Bluetooth),
        "gotcha" to Pair("GoTcha", Icons.Default.Watch),
        "research" to Pair("Research", Icons.Default.Science),
        "emulation" to Pair("Emulation", Icons.Default.Sensors),
        "firmware" to Pair("Firmware", Icons.Default.Memory),
        "settings" to Pair("Settings", Icons.Default.Settings)
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("GoTcha Terminal", fontWeight = FontWeight.Bold, color = Color.Cyan) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Black)
            )
        },
        floatingActionButton = {
            if (currentRoute == "home" || currentRoute == "devices") {
                FloatingActionButton(
                    onClick = { navController.navigate("device_scanner") },
                    containerColor = Color.Cyan,
                    contentColor = Color.Black
                ) {
                    Icon(Icons.AutoMirrored.Filled.BluetoothSearching, contentDescription = "Connect")
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF121212)) {
                tabs.forEach { (route, info) ->
                    val (label, icon) = info
                    NavigationBarItem(
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 9.sp, maxLines = 1) },
                        selected = currentRoute.startsWith(route),
                        onClick = {
                            navController.navigate(route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Cyan,
                            selectedTextColor = Color.Cyan,
                            indicatorColor = Color(0xFF1E293B)
                        ),
                        alwaysShowLabel = true
                    )
                }
            }
        },
        containerColor = Color(0xFF0A0A0A)
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            NavHost(navController = navController, startDestination = "home") {
                // Home Graph
                composable("home") { HomeTab(viewModel, navController) }

                // Devices Graph
                composable("devices") { DevicesTab(viewModel, navController) }
                composable("device_scanner") { DeviceScanScreen(viewModel, navController) }

                // GoTcha Graph
                composable("gotcha") { GotchaTab(viewModel, navController) }

                // Research Graph
                composable("research") { ResearchTab(viewModel, navController) }
                composable("gatt_explorer") { BLEExplorerScreen(viewModel, navController) }
                composable("identity_terminal") { BleTerminalScreen(viewModel, onBack = { navController.popBackStack() }) }
                composable("research_menu") {
                    ResearchMenuScreen(
                        onNavigate = { screen ->
                            when(screen) {
                                TerminalViewModel.Screen.CHALLENGE_RESPONSE -> navController.navigate("challenge_response")
                                TerminalViewModel.Screen.AUTH_ANALYSIS -> navController.navigate("auth_analysis")
                                TerminalViewModel.Screen.SECURITY_MONITOR -> navController.navigate("security_monitor")
                                TerminalViewModel.Screen.TERMINAL -> navController.navigate("identity_terminal")
                                else -> {}
                            }
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("challenge_response") { ChallengeResponseScreen(viewModel, onBack = { navController.popBackStack() }) }
                composable("auth_analysis") { AuthAnalysisScreen(viewModel, onBack = { navController.popBackStack() }) }
                composable("security_monitor") { SecurityMonitorScreen(viewModel, onBack = { navController.popBackStack() }) }
                composable("comparison_mode") {
                    val realSession by viewModel.realSession.collectAsState()
                    val espSession by viewModel.espSession.collectAsState()
                    val result by viewModel.comparisonResult.collectAsState()
                    val isCapturing by viewModel.isCapturing.collectAsState()
                    ComparisonScreen(realSession, espSession, result, isCapturing, { navController.popBackStack() }, { isReal -> viewModel.startCapture(if(isReal) "Real GoTcha" else "ESP32") }, { isReal -> viewModel.stopCapture(isReal) }, { viewModel.clearComparison() })
                }

                // Emulation Graph
                composable("emulation") { EmulationTab(viewModel, navController) }
                composable("adv_lab") { AdvertisementScreen(viewModel) }
                composable("gatt_server_lab") { GattServerScreen(viewModel) }

                // Firmware Graph
                composable("firmware") { FirmwareTab(viewModel, navController) }
                composable("firmware_analysis") { FirmwareAnalysisScreen(viewModel, navController) }
                composable("ota_flasher") { FirmwareScreen(viewModel, navController) }

                // Settings Graph
                composable("settings") { SettingsTab(viewModel, navController) }
                composable("debug_console") {
                    val logs by viewModel.logs.collectAsState()
                    Column(modifier = Modifier.fillMaxSize()) {
                        TopAppBar(title = { Text("Debug Console") }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } })
                        LogWindow(logs)
                    }
                }
                composable("synced_sessions") {
                    val sessions by viewModel.syncedSessions.collectAsState()
                    Column(modifier = Modifier.fillMaxSize()) {
                        TopAppBar(title = { Text("Recent Captures") }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } })
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            items(sessions) { session ->
                                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(session.name, fontWeight = FontWeight.Bold)
                                        val dateStr = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(session.startTime))
                                        Text("${session.deviceAddress ?: "Unknown"} | $dateStr", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HomeTab(viewModel: TerminalViewModel, navController: NavController) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsState()
    val sessionStart by viewModel.sessionStartTime.collectAsState()
    val lastActivity by viewModel.lastActivityTime.collectAsState()
    val notifCount by viewModel.notificationCount.collectAsState()
    val packetCount by viewModel.packetCount.collectAsState()
    val serviceCount by viewModel.serviceCount.collectAsState()
    val recentEvents by viewModel.recentEvents.collectAsState()
    val currentRssi by viewModel.currentRssi.collectAsState()
    val savedDevices by viewModel.savedDevices.collectAsState()
    val charValues by viewModel.characteristicValues.collectAsState()
    val batteryLevel by remember(charValues) {
        derivedStateOf { charValues[UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")]?.getOrNull(0)?.toInt()?.let { level -> "$level%" } ?: "Unknown" }
    }

    val sdf = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth().clickable { navController.navigate("device_scanner") },
                colors = CardDefaults.cardColors(containerColor = if (connectionState == BLEManager.ConnectionState.CONNECTED) Color(0xFF003311) else Color(0xFF330000)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (connectionState == BLEManager.ConnectionState.CONNECTED) Icons.Default.BluetoothConnected else Icons.Default.BluetoothDisabled,
                            contentDescription = null,
                            tint = if (connectionState == BLEManager.ConnectionState.CONNECTED) Color.Green else Color.Red,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            val deviceName = if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                                viewModel.bleManager.getConnectedDevice()?.name ?: "Unknown Device"
                            } else {
                                "GoTcha Device"
                            }
                            Text(if (connectionState == BLEManager.ConnectionState.CONNECTED) deviceName else "Disconnected", style = MaterialTheme.typography.titleMedium, color = Color.White)
                            Text(if (connectionState == BLEManager.ConnectionState.CONNECTED) "Active Session" else "No active session", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
                        }
                        if (connectionState == BLEManager.ConnectionState.CONNECTED) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(batteryLevel, style = MaterialTheme.typography.titleLarge, color = Color.Green, fontWeight = FontWeight.Bold)
                                currentRssi?.let { rssiVal ->
                                    Text("$rssiVal dBm", style = MaterialTheme.typography.labelSmall, color = Color.Cyan)
                                }
                            }
                        }
                    }

                    if (connectionState == BLEManager.ConnectionState.CONNECTED) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.Gray.copy(alpha = 0.3f))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            val duration = sessionStart?.let { startTime -> (System.currentTimeMillis() - startTime) / 1000 } ?: 0
                            val durationStr = "%02d:%02d".format(duration / 60, duration % 60)
                            MetricSmall("Session", durationStr)
                            MetricSmall("Last Activity", lastActivity?.let { lastTime -> sdf.format(Date(lastTime)) } ?: "None")
                        }
                    }
                }
            }
        }
        item { SectionHeader("QUICK ACTIONS") }
        item { FeatureGrid(listOf(
            FeatureItem("Scan Devices", Icons.Default.Search, Color.Cyan, "CONNECTED") { navController.navigate("device_scanner") },
            FeatureItem("Connect Device", Icons.Default.Bluetooth, Color.Cyan, "CONNECTED") { navController.navigate("device_scanner") },
            FeatureItem("Reconnect Last", Icons.Default.Replay, Color.Cyan, "CONNECTED") { 
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    viewModel.reconnectLastDevice() 
                }
            },
            FeatureItem("Disconnect", Icons.Default.Close, Color.Cyan, "CONNECTED") { viewModel.disconnect() }
        ))}
        item { SectionHeader("LIVE ACTIVITY") }
        item { FeatureGrid(listOf(
            FeatureItem("Notifications: $notifCount", Icons.Default.Notifications, Color.Green, "CONNECTED") {},
            FeatureItem("Packets: $packetCount", Icons.AutoMirrored.Filled.ListAlt, Color.Green, "CONNECTED") {},
            FeatureItem("Services: $serviceCount", Icons.Default.SettingsApplications, Color.Green, "CONNECTED") {},
            FeatureItem("Recent Events", Icons.Default.Event, Color.Green, "CONNECTED") { /* Show bottom sheet? */ }
        ))}

        if (recentEvents.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("RECENT EVENTS", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        recentEvents.take(3).forEach { event ->
                            Text(event, style = MaterialTheme.typography.bodySmall, color = Color.LightGray, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        item { SectionHeader("RECENT DEVICES") }
        if (savedDevices.isEmpty()) {
            item { Text("No recently connected devices", style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.padding(horizontal = 8.dp)) }
        } else {
            items(savedDevices.take(3)) { device ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { 
                        val bluetoothAdapter = context.getSystemService(BluetoothManager::class.java)?.adapter
                        if (bluetoothAdapter != null && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                            viewModel.connect(bluetoothAdapter.getRemoteDevice(device.address))
                        }
                    },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF181818))
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.History, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(device.name ?: "Unknown", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                            Text(device.address, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }
                    }
                }
            }
        }
        item { SectionHeader("PLACEHOLDERS") }
        item { FeatureGrid(listOf(
            FeatureItem("GoTcha Evolve", Icons.Default.Watch, Color.Gray, "PLACEHOLDER") {},
            FeatureItem("Pokemon GO Plus", Icons.Default.CatchingPokemon, Color.Gray, "PLACEHOLDER") {}
        ))}
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
fun DevicesTab(viewModel: TerminalViewModel, navController: NavController) {
    val context = LocalContext.current
    val savedDevices by viewModel.savedDevices.collectAsState()
    val favoriteDevices by viewModel.favoriteDevices.collectAsState()

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionHeader("SCANNER") }
        item { FeatureGrid(listOf(
            FeatureItem("Scan Devices", Icons.AutoMirrored.Filled.BluetoothSearching, Color.Cyan, "CONNECTED") { navController.navigate("device_scanner") },
            FeatureItem("Reconnect Last", Icons.Default.Replay, Color.Cyan, "CONNECTED") { 
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    viewModel.reconnectLastDevice()
                }
            }
        ))}
        
        item { SectionHeader("FAVORITE DEVICES") }
        if (favoriteDevices.isEmpty()) {
            item { Text("No favorite devices", style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.padding(horizontal = 8.dp)) }
        } else {
            items(favoriteDevices) { device ->
                DeviceManagerCard(device, viewModel)
            }
        }

        item { SectionHeader("SAVED DEVICES") }
        if (savedDevices.isEmpty()) {
            item { Text("No saved devices", style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.padding(horizontal = 8.dp)) }
        } else {
            items(savedDevices) { device ->
                DeviceManagerCard(device, viewModel)
            }
        }

        item { SectionHeader("CONNECTION TOOLS") }
        item { FeatureGrid(listOf(
            FeatureItem("Disconnect", Icons.Default.BluetoothDisabled, Color.Green, "CONNECTED") { viewModel.disconnect() },
            FeatureItem("Auto Reconnect", Icons.Default.Autorenew, Color.Green, "NOT YET IMPLEMENTED") {}
        ))}
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
fun DeviceManagerCard(device: com.airysdark.gotchaterminal.storage.entities.DeviceEntity, viewModel: TerminalViewModel) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { 
            val bluetoothAdapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            if (bluetoothAdapter != null && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                viewModel.connect(bluetoothAdapter.getRemoteDevice(device.address))
            }
        },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF181818))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(device.name ?: "Unknown", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                Text(device.address, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
            IconButton(onClick = { viewModel.toggleFavorite(device.address, !device.isFavorite) }) {
                Icon(
                    if (device.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    tint = if (device.isFavorite) Color.Red else Color.Gray
                )
            }
        }
    }
}

@Composable
fun GotchaTab(viewModel: TerminalViewModel, navController: NavController) {
    val context = LocalContext.current
    val bitmask by viewModel.settingsBitmask.collectAsState()
    val charValues by viewModel.characteristicValues.collectAsState()
    
    val batteryLevel = remember(charValues) {
        charValues[UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")]?.getOrNull(0)?.toInt()?.let { level -> "$level%" } ?: "--"
    }
    
    val softwareRevision = remember(charValues) {
        charValues[UUID.fromString("00002a28-0000-1000-8000-00805f9b34fb")]?.let { data -> String(data) } ?: "Unknown"
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionHeader("DEVICE STATUS") }
        item { FeatureGrid(listOf(
            FeatureItem("Battery: $batteryLevel", Icons.Default.BatteryFull, Color.Green, "CONNECTED") { 
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    viewModel.bleManager.readCharacteristic(UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb"), UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")) 
                }
            },
            FeatureItem("Rev: $softwareRevision", Icons.Default.Memory, Color.Green, "CONNECTED") { 
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    viewModel.bleManager.readCharacteristic(UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb"), UUID.fromString("00002a28-0000-1000-8000-00805f9b34fb")) 
                }
            },
            FeatureItem("Reset Device", Icons.Default.Refresh, Color.Red, "CONNECTED") { 
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    viewModel.resetDevice() 
                }
            },
            FeatureItem("Sync Time", Icons.Default.AccessTime, Color.Cyan, "CONNECTED") { 
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    viewModel.setTime() 
                }
            }
        ))}

        item { SectionHeader("AUTOMATION") }
        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF121212))) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingToggle("Auto Catch (New)", (bitmask.toInt() and 0x02) != 0) { enabled -> viewModel.toggleAutoCatchNew(enabled) }
                    SettingToggle("Auto Catch (Known)", (bitmask.toInt() and 0x01) != 0) { enabled -> viewModel.toggleAutoCatchKnown(enabled) }
                    SettingToggle("Auto Spin PokéStops", (bitmask.toInt() and 0x04) != 0) { enabled -> viewModel.toggleAutoSpin(enabled) }
                }
            }
        }

        item { SectionHeader("FEEDBACK") }
        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF121212))) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingToggle("Vibration", (bitmask.toInt() and 0x08) != 0) { enabled -> viewModel.toggleVibration(enabled) }
                    SettingToggle("Screen On", (bitmask.toInt() and 0x10) != 0) { enabled -> viewModel.toggleScreen(enabled) }
                }
            }
        }

        item { SectionHeader("DIAGNOSTICS") }
        item { FeatureGrid(listOf(
            FeatureItem("Conn Health", Icons.Default.MonitorHeart, Color.Green, "CONNECTED") { /* Add logic in VM */ },
            FeatureItem("Cmd Monitor", Icons.Default.Terminal, Color.Yellow, "NOT YET IMPLEMENTED") {},
            FeatureItem("Error Logs", Icons.Default.Report, Color.Red, "CONNECTED") { navController.navigate("debug_console") }
        ))}

        item { SectionHeader("REVERSE ENGINEERING") }
        item { FeatureGrid(listOf(
            FeatureItem("Char Mapping", Icons.Default.Map, Color.Magenta, "CONNECTED") { navController.navigate("gatt_explorer") },
            FeatureItem("Packet Analysis", Icons.Default.Analytics, Color.Magenta, "CONNECTED") { navController.navigate("research_menu") },
            FeatureItem("Cmd Explorer", Icons.Default.Explore, Color.Magenta, "NOT YET IMPLEMENTED") {},
            FeatureItem("Unknown Cmd", Icons.Default.QuestionMark, Color.Magenta, "NOT YET IMPLEMENTED") {}
        ))}

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
fun SettingToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.White)
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = Color.Cyan, checkedTrackColor = Color.Cyan.copy(alpha = 0.5f)))
    }
}

@Composable
fun ResearchTab(viewModel: TerminalViewModel, navController: NavController) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionHeader("GATT TOOLS") }
        item { FeatureGrid(listOf(
            FeatureItem("GATT Explorer", Icons.Default.AccountTree, Color.Cyan, "CONNECTED") { navController.navigate("gatt_explorer") },
            FeatureItem("Service Browser", Icons.AutoMirrored.Filled.List, Color.Cyan, "NOT YET IMPLEMENTED") {},
            FeatureItem("Characteristic Browser", Icons.AutoMirrored.Filled.ViewList, Color.Cyan, "NOT YET IMPLEMENTED") {}
        ))}
        item { SectionHeader("ANALYSIS") }
        item { FeatureGrid(listOf(
            FeatureItem("Research Menu", Icons.Default.Science, Color(0xFFFFA500), "CONNECTED") { navController.navigate("research_menu") },
            FeatureItem("Identity Terminal", Icons.Default.Badge, Color(0xFFFFA500), "CONNECTED") { navController.navigate("identity_terminal") },
            FeatureItem("Comparison Mode", Icons.AutoMirrored.Filled.CompareArrows, Color(0xFFFFA500), "CONNECTED") { navController.navigate("comparison_mode") },
            FeatureItem("GATT Explorer", Icons.Default.Explore, Color(0xFFFFA500), "CONNECTED") { navController.navigate("gatt_explorer") }
        ))}

        item { SectionHeader("ADVANCED TOOLS") }
        item { FeatureGrid(listOf(
            FeatureItem("Hex Viewer", Icons.Default.DataObject, Color.LightGray, "NOT YET IMPLEMENTED") {},
            FeatureItem("Packet Decoder", Icons.Default.Code, Color.LightGray, "NOT YET IMPLEMENTED") {},
            FeatureItem("Session Recorder", Icons.Default.FiberManualRecord, Color.LightGray, "NOT YET IMPLEMENTED") {}
        ))}

        item { SectionHeader("LOGGING") }
        item { FeatureGrid(listOf(
            FeatureItem("Export Logs", Icons.Default.SaveAlt, Color.Cyan, "CONNECTED") { viewModel.exportResearchLogs() },
            FeatureItem("Import Logs", Icons.Default.UploadFile, Color.Cyan, "NOT YET IMPLEMENTED") {},
            FeatureItem("Recent Capture", Icons.Default.History, Color.Cyan, "CONNECTED") { navController.navigate("synced_sessions") }
        ))}

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
fun EmulationTab(viewModel: TerminalViewModel, navController: NavController) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionHeader("ADVERTISEMENT LAB") }
        item { FeatureGrid(listOf(
            FeatureItem("Advertisement Builder", Icons.Default.Build, Color(0xFF00FFCC), "CONNECTED") { navController.navigate("adv_lab") },
            FeatureItem("Advertisement Monitor", Icons.Default.Sensors, Color(0xFF00FFCC), "NOT YET IMPLEMENTED") {},
            FeatureItem("Advertisement Replay", Icons.Default.ReplayCircleFilled, Color(0xFF00FFCC), "NOT YET IMPLEMENTED") {}
        ))}
        item { SectionHeader("PERIPHERAL LAB") }
        item { FeatureGrid(listOf(
            FeatureItem("GATT Server Lab", Icons.Default.SettingsRemote, Color.Magenta, "CONNECTED") { navController.navigate("gatt_server_lab") },
            FeatureItem("Service Generator", Icons.Default.AddCircle, Color.Magenta, "NOT YET IMPLEMENTED") {},
            FeatureItem("Char Generator", Icons.Default.PostAdd, Color.Magenta, "NOT YET IMPLEMENTED") {}
        ))}
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
fun FirmwareTab(viewModel: TerminalViewModel, navController: NavController) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionHeader("ANALYSIS") }
        item { FeatureGrid(listOf(
            FeatureItem("Firmware Analysis", Icons.Default.Analytics, Color.Cyan, "CONNECTED") { navController.navigate("firmware_analysis") },
            FeatureItem("Firmware Explorer", Icons.Default.Folder, Color.Cyan, "NOT YET IMPLEMENTED") {},
            FeatureItem("Binary Viewer", Icons.Default.DataArray, Color.Cyan, "NOT YET IMPLEMENTED") {}
        ))}
        item { SectionHeader("OTA") }
        item { FeatureGrid(listOf(
            FeatureItem("OTA Flasher", Icons.Default.SystemUpdate, Color.Red, "CONNECTED") { navController.navigate("ota_flasher") },
            FeatureItem("OTA Capture", Icons.Default.CellWifi, Color.Red, "NOT YET IMPLEMENTED") {},
            FeatureItem("OTA Logger", Icons.AutoMirrored.Filled.ListAlt, Color.Red, "NOT YET IMPLEMENTED") {}
        ))}
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
fun SettingsTab(viewModel: TerminalViewModel, navController: NavController) {
    val logs by viewModel.logs.collectAsState()

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionHeader("APPLICATION") }
        item { FeatureGrid(listOf(
            FeatureItem("Theme", Icons.Default.Palette, Color.Gray, "CONNECTED") { /* Toggle theme logic */ },
            FeatureItem("Clear Database", Icons.Default.DeleteForever, Color.Red, "CONNECTED") { /* Clear DB logic */ },
            FeatureItem("Export Logs", Icons.Default.SaveAlt, Color.Cyan, "CONNECTED") { viewModel.exportResearchLogs() }
        ))}

        item { SectionHeader("BLUETOOTH SETTINGS") }
        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF121212))) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingToggle("Auto-Reconnect (DB05)", false) { }
                    SettingToggle("Filter Unknown Devices", true) { }
                    SettingToggle("High Power Scan", false) { }
                }
            }
        }

        item { SectionHeader("DEVELOPER") }
        item {
            Card(modifier = Modifier.fillMaxWidth().clickable { navController.navigate("debug_console") }, colors = CardDefaults.cardColors(containerColor = Color(0xFF331100))) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Terminal, contentDescription = null, tint = Orange)
                    Spacer(Modifier.width(16.dp))
                    Text("View Debug Console", style = MaterialTheme.typography.titleMedium, color = Color.White)
                }
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
        item {
            Text("GoTcha Terminal v2.0.0-gemini", style = MaterialTheme.typography.labelSmall, color = Color.Gray, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

data class FeatureItem(val title: String, val icon: ImageVector, val iconTint: Color, val stateInfo: String, val onClick: () -> Unit)

@Composable
fun MetricSmall(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Text(value, style = MaterialTheme.typography.bodySmall, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = Color(0xFF888888),
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
fun FeatureGrid(items: List<FeatureItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (i in items.indices step 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FeatureCard(item = items[i], modifier = Modifier.weight(1f))
                if (i + 1 < items.size) {
                    FeatureCard(item = items[i + 1], modifier = Modifier.weight(1f))
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureCard(item: FeatureItem, modifier: Modifier = Modifier) {
    val alpha = if (item.stateInfo == "CONNECTED") 1f else 0.5f
    Card(
        onClick = item.onClick,
        modifier = modifier.height(110.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF181818).copy(alpha = alpha)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF2A2A2A).copy(alpha = alpha))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            Icon(item.icon, contentDescription = item.title, tint = item.iconTint.copy(alpha = alpha), modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(item.title, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = alpha), fontWeight = FontWeight.SemiBold, lineHeight = 16.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(if (item.stateInfo == "CONNECTED") "Active" else "TBD", style = MaterialTheme.typography.labelSmall, color = if (item.stateInfo == "CONNECTED") Color.Green else Color.Gray)
        }
    }
}

@Composable
fun FirmwareScreen(viewModel: TerminalViewModel, navController: NavController) {
    val selectedFirmware by viewModel.selectedFirmware.collectAsState()
    val internalFirmwares by viewModel.internalFirmwares.collectAsState()
    val otaProgress by viewModel.otaProgress.collectAsState()
    val otaStatus by viewModel.otaStatusText.collectAsState()
    val isBusy by viewModel.otaIsBusy.collectAsState()
    val otaState by viewModel.otaState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Firmware Update") },
            navigationIcon = {
                IconButton(onClick = { if (!isBusy) navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            FirmwarePicker(
                selectedFirmware = selectedFirmware,
                onFirmwareSelected = { viewModel.selectFirmware(it) },
                onInternalFirmwareSelected = { viewModel.selectInternalFirmware(it) },
                internalFirmwares = internalFirmwares
            )

            if (selectedFirmware != null || isBusy || otaState != SUOTAUpdater.OtaState.IDLE) {
                Spacer(modifier = Modifier.height(24.dp))
                OtaStatusCard(
                    progress = otaProgress,
                    statusText = otaStatus,
                    state = otaState,
                    mtu = viewModel.currentMtu.collectAsState().value,
                    bytesSent = viewModel.otaBytesSent.collectAsState().value,
                    totalBytes = selectedFirmware?.size ?: 0
                )
            }
        }

        Button(
            onClick = { viewModel.startOtaUpdate() },
            enabled = selectedFirmware != null && !isBusy,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Text(if (isBusy) "UPDATING..." else "START UPDATE")
        }
    }
}

@Composable
fun OtaStatusCard(progress: Float, statusText: String, state: com.airysdark.gotchaterminal.firmware.ota.SUOTAUpdater.OtaState, mtu: Int, bytesSent: Int, totalBytes: Int) {
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("OTA Progress", fontWeight = FontWeight.Bold)
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            Text(statusText, style = MaterialTheme.typography.bodySmall)
            Text("State: ${state.name} | MTU: $mtu | Sent: $bytesSent / $totalBytes", fontSize = 10.sp, color = Color.Gray)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirmwareAnalysisScreen(viewModel: TerminalViewModel, navController: NavController) {
    val analysis by viewModel.firmwareAnalysis.collectAsState()
    val selectedFirmware by viewModel.selectedFirmware.collectAsState()
    val internalFirmwares by viewModel.internalFirmwares.collectAsState()
    var tabIndex by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Firmware Analysis") },
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) } }
        )

        FirmwarePicker(selectedFirmware, { uri -> viewModel.selectFirmware(uri) }, { name -> viewModel.selectInternalFirmware(name) }, internalFirmwares)

        if (analysis != null) {
            TabRow(selectedTabIndex = tabIndex) {
                Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("Info") })
                Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("Strings") })
                Tab(selected = tabIndex == 2, onClick = { tabIndex = 2 }, text = { Text("Hex") })
            }
            when (tabIndex) {
                0 -> AnalysisInfoTab(analysis!!)
                1 -> AnalysisStringsTab(analysis!!)
                2 -> AnalysisHexTab(viewModel)
            }
        }
    }
}

@Composable
fun AnalysisInfoTab(res: com.airysdark.gotchaterminal.models.firmware.FirmwareAnalysisResult) {
    Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Architecture: ${res.architecture}", fontWeight = FontWeight.Bold)
        Text("Size: ${res.size} bytes")
        Text("Entropy: ${"%.4f".format(res.entropy)}")
        Text("CRC32: ${res.crc32}")
        Text("SHA256: ${res.sha256}", fontSize = 10.sp)

        if (res.headerInfo.isNotEmpty()) {
            Text("Header Information:", modifier = Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
            res.headerInfo.forEach { (k, v) ->
                Text("$k: $v", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }

        Text("URLs Found:", modifier = Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.primary)
        res.urls.forEach { url -> Text(url, fontSize = 11.sp) }

        Text("UUIDs Found:", modifier = Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.primary)
        res.uuids.forEach { uuid -> Text(uuid, fontSize = 11.sp) }
    }
}

@Composable
fun AnalysisStringsTab(res: com.airysdark.gotchaterminal.models.firmware.FirmwareAnalysisResult) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, res.strings) {
        if (query.isEmpty()) res.strings else res.strings.filter { str -> str.contains(query, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { newQuery -> query = newQuery },
            label = { Text("Filter Strings") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            singleLine = true
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(filtered) { str ->
                Text(str, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.White)
                HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
fun AnalysisHexTab(viewModel: TerminalViewModel) {
    val hexLines = remember { viewModel.getHexView(0, 1024) }
    LazyColumn(modifier = Modifier.fillMaxSize().background(Color.Black).padding(8.dp)) {
        items(hexLines) { line ->
            Row {
                Text(line.address, color = Color.Yellow, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.width(8.dp))
                Text(line.hex, color = Color.Cyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                Text(line.ascii, color = Color.Green, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun DeviceScanScreen(viewModel: TerminalViewModel, navController: NavController) {
    val context = LocalContext.current
    val devices by viewModel.filteredDevices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortByRssi by viewModel.sortByRssi.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        TopAppBar(
            title = { Text("BLE Client") },
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            actions = {
                IconButton(onClick = { viewModel.toggleSortByRssi() }) {
                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort", tint = if (sortByRssi) Color.Cyan else Color.White)
                }
            }
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.updateSearchQuery(it) },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            placeholder = { Text("Search devices...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Cyan)
        )

        Button(onClick = { 
            if (isScanning) {
                viewModel.stopScan()
            } else {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                    viewModel.startScan()
                }
            }
        }, modifier = Modifier.fillMaxWidth()) {
            Text(if (isScanning) "Stop Scan" else "Start Scan")
        }

        if (connectionState == BLEManager.ConnectionState.CONNECTING) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(devices) { extended ->
                val device = extended.device
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { 
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        viewModel.connect(device.address) 
                    }
                }) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            val deviceName = if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                                device.name ?: "Unknown"
                            } else {
                                "GoTcha"
                            }
                            Text(deviceName, fontWeight = FontWeight.Bold)
                            Text(device.address, style = MaterialTheme.typography.bodySmall)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${device.rssi} dBm", color = Color.Cyan, fontWeight = FontWeight.Bold)
                            val signalIcon = when {
                                device.rssi > -60 -> Icons.Default.SignalCellular4Bar
                                device.rssi > -80 -> Icons.Default.SignalCellularConnectedNoInternet4Bar
                                else -> Icons.Default.SignalCellular0Bar
                            }
                            Icon(signalIcon, contentDescription = null, tint = Color.Cyan, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BLEExplorerScreen(viewModel: TerminalViewModel, navController: NavController) {
    val context = LocalContext.current
    val services by viewModel.discoveredServices.collectAsState()
    val charValues by viewModel.characteristicValues.collectAsState()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val logs by viewModel.logs.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("GATT Explorer") }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } })
        LazyColumn(modifier = Modifier.weight(1f).padding(8.dp)) {
            items(services) { service ->
                val serviceName = UUIDDatabase.getName(service.uuid.toString()) ?: "Unknown Service"
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text("SERVICE: $serviceName", style = MaterialTheme.typography.labelSmall, color = Color.Cyan, fontWeight = FontWeight.ExtraBold)
                    Text(service.uuid.toString(), fontSize = 9.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)

                    service.characteristics.forEach { char ->
                        val charName = UUIDDatabase.getName(char.uuid.toString()) ?: "Unknown Characteristic"
                        Card(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF111111))) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(charName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text(char.uuid.toString(), fontSize = 8.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)

                                if (charValues.containsKey(char.uuid)) {
                                    val data = charValues[char.uuid]!!
                                    Text("HEX: ${viewModel.bytesToHex(data)}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color.Green)
                                    Text("ASCII: ${viewModel.bytesToAscii(data)}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color.Yellow)
                                }

                                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                    if (char.properties and 2 != 0) IconButton(onClick = { 
                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                                            viewModel.readCharacteristic(service.uuid, char.uuid) 
                                        }
                                    }) { Icon(Icons.Default.Download, null, tint = Color.Cyan, modifier = Modifier.size(18.dp)) }
                                    if (char.properties and 8 != 0 || char.properties and 4 != 0) IconButton(onClick = {
                                        viewModel.updateTerminalServiceUuid(service.uuid.toString())
                                        viewModel.updateTerminalCharUuid(char.uuid.toString())
                                        navController.navigate("identity_terminal")
                                    }) { Icon(Icons.Default.Edit, null, tint = Color.Cyan, modifier = Modifier.size(18.dp)) }
                                    if (char.properties and 16 != 0) IconButton(onClick = { 
                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                                            viewModel.toggleNotification(service.uuid, char.uuid) 
                                        }
                                    }) { Icon(if (notificationsEnabled.contains(char.uuid)) Icons.Default.NotificationsActive else Icons.Default.Notifications, null, tint = if (notificationsEnabled.contains(char.uuid)) Color.Green else Color.Gray, modifier = Modifier.size(18.dp)) }
                                }
                            }
                        }
                    }
                }
            }
        }
        LogWindow(logs)
    }
}

@Composable
fun LogWindow(logs: List<String>) {
    val listState = rememberLazyListState()
    LaunchedEffect(logs.size) { if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1) }
    Box(modifier = Modifier.fillMaxWidth().height(180.dp).background(Color.Black)) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
            items(logs) { Text(it, color = Color.Green, fontFamily = FontFamily.Monospace, fontSize = 10.sp) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBar(title: @Composable () -> Unit, navigationIcon: @Composable (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    CenterAlignedTopAppBar(title = title, navigationIcon = navigationIcon ?: {}, actions = actions, colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Black, titleContentColor = Color.Cyan))
}
