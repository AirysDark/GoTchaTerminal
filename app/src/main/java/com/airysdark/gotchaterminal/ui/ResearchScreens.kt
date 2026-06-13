package com.airysdark.gotchaterminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResearchMenuScreen(onNavigate: (TerminalViewModel.Screen) -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Research & Analysis") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) } }
        )
        
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ResearchMenuItem("Challenge-Response Analysis", "Monitor and log all BLE traffic with timestamps.", Icons.Default.SyncAlt) {
                onNavigate(TerminalViewModel.Screen.CHALLENGE_RESPONSE)
            }
            ResearchMenuItem("Pokemon Authentication Analysis", "Record traffic immediately after connection.", Icons.Default.Security) {
                onNavigate(TerminalViewModel.Screen.AUTH_ANALYSIS)
            }
            ResearchMenuItem("Security Key Challenge Monitor", "Capture first 30s and analyze patterns.", Icons.Default.MonitorHeart) {
                onNavigate(TerminalViewModel.Screen.SECURITY_MONITOR)
            }
            ResearchMenuItem("Identity Service Terminal", "Low-level access to GoTcha Evolve Identity characteristics.", Icons.Default.Terminal) {
                onNavigate(TerminalViewModel.Screen.TERMINAL)
            }
        }
    }
}

@Composable
fun ResearchMenuItem(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChallengeResponseScreen(viewModel: TerminalViewModel, onBack: () -> Unit) {
    val logs by viewModel.researchLogs.collectAsState()
    val sdf = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Challenge-Response") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) } },
            actions = {
                Button(onClick = { viewModel.startChallengeResponseAnalysis() }, modifier = Modifier.padding(end = 8.dp)) {
                    Text("Record")
                }
                IconButton(onClick = { viewModel.exportResearchLogs() }) { Icon(Icons.Default.Save, contentDescription = "Export JSON") }
                IconButton(onClick = { viewModel.clearResearchLogs() }) { Icon(Icons.Default.Delete, contentDescription = "Clear") }
            }
        )

        LazyColumn(modifier = Modifier.weight(1f).background(Color.Black).padding(8.dp)) {
            items(logs) { log ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(
                        text = "${sdf.format(Date(log.timestamp))} [${log.type}] ${log.charUuid.take(4)}",
                        color = if (log.type == "WRITE") Color.Cyan else Color.Green,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "DATA: ${log.data}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityMonitorScreen(viewModel: TerminalViewModel, onBack: () -> Unit) {
    val logs by viewModel.researchLogs.collectAsState()
    
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Security Key Monitor") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) } },
            actions = {
                Button(onClick = { viewModel.startSecurityMonitor() }) {
                    Text("Capture 30s")
                }
            }
        )

        LazyColumn(modifier = Modifier.weight(1f).padding(8.dp)) {
            items(logs) { log ->
                val highlights = analyzePatterns(log.data)
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(
                    containerColor = if (highlights.isNotEmpty()) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface
                )) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("#${log.sequence}", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(log.charUuid, fontSize = 9.sp, color = Color.Gray)
                        }
                        Text(log.data, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                        if (highlights.isNotEmpty()) {
                            Text(highlights.joinToString(" | "), color = MaterialTheme.colorScheme.error, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

fun analyzePatterns(hex: String): List<String> {
    val issues = mutableListOf<String>()
    if (hex.length == 32) issues.add("16-byte Nonce?")
    if (hex.length == 40) issues.add("20-byte Challenge?")
    if (hex.length == 64) issues.add("32-byte Response?")
    
    val bytes = hex.chunked(2)
    if (bytes.distinct().size == 1 && bytes.size > 1) issues.add("Repeating Pattern")
    
    return issues
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BleTerminalScreen(viewModel: TerminalViewModel, onBack: () -> Unit) {
    val serviceUuid by viewModel.terminalServiceUuid.collectAsState()
    val charUuid by viewModel.terminalCharUuid.collectAsState()
    val dataInput by viewModel.terminalDataInput.collectAsState()
    val logs by viewModel.logs.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Identity Terminal") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) } }
        )

        Column(modifier = Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = serviceUuid,
                onValueChange = { viewModel.updateTerminalServiceUuid(it) },
                label = { Text("Service UUID") },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            )
            OutlinedTextField(
                value = charUuid,
                onValueChange = { viewModel.updateTerminalCharUuid(it) },
                label = { Text("Characteristic UUID") },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            )
            
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.readTerminalCharacteristic() }, modifier = Modifier.weight(1f)) { 
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Read") 
                }
                Button(onClick = { viewModel.toggleTerminalNotification() }, modifier = Modifier.weight(1f)) { 
                    Icon(Icons.Default.Notifications, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Notify") 
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = dataInput,
                    onValueChange = { viewModel.updateTerminalDataInput(it) },
                    label = { Text("Hex Data") },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("e.g. 010203") },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )
                IconButton(onClick = { viewModel.writeTerminalCharacteristic() }) {
                    Icon(Icons.Default.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("GoTcha Identity Shortcuts:", style = MaterialTheme.typography.labelLarge)
            
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ShortcutChip("Read MAC") { viewModel.readMac() }
                ShortcutChip("Read Adv") { viewModel.readAdvert() }
                ShortcutChip("Status") { viewModel.readStatus() }
                ShortcutChip("Key") { viewModel.readKey() }
                ShortcutChip("Blob") { viewModel.readBlob() }
                ShortcutChip("Dump") { viewModel.dumpIdentity() }
                ShortcutChip("Save BIN") { viewModel.saveValueToFile("identity", "bin") }
                ShortcutChip("Save JSON") { viewModel.saveValueToFile("identity", "json") }
                ShortcutChip("Connected MAC") { viewModel.readConnectedMac() }
                ShortcutChip("Compare") { viewModel.navigateTo(TerminalViewModel.Screen.COMPARISON) }
            }

            Spacer(modifier = Modifier.height(16.dp))
            LogWindow(logs)
        }
    }
}

@Composable
fun ShortcutChip(label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, fontSize = 10.sp) },
        modifier = Modifier.padding(vertical = 2.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthAnalysisScreen(viewModel: TerminalViewModel, onBack: () -> Unit) {
    val logs by viewModel.researchLogs.collectAsState()
    val sdf = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Auth Analysis") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) } },
            actions = {
                Button(onClick = { viewModel.startAuthAnalysis() }, modifier = Modifier.padding(end = 8.dp)) {
                    Text("Capture Auth")
                }
                IconButton(onClick = { viewModel.clearResearchLogs() }) { Icon(Icons.Default.Delete, contentDescription = "Clear") }
            }
        )

        LazyColumn(modifier = Modifier.weight(1f).background(Color(0xFF1A1A1A)).padding(8.dp)) {
            items(logs) { log ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color.Black)) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(sdf.format(Date(log.timestamp)), color = Color.Gray, fontSize = 10.sp)
                            Text(log.type, color = if (log.type == "WRITE") Color.Cyan else Color.Green, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                        Text(log.charUuid, color = Color.LightGray, fontSize = 9.sp)
                        Text(log.data, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                    }
                }
            }
        }
        
        if (logs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No auth traffic captured. Click 'Capture Auth' and reconnect device.", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
