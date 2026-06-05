package com.airysdark.gotchaterminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResearchMenuScreen(onNavigate: (TerminalViewModel.Screen) -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Research & Analysis") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = null) } }
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
            ResearchMenuItem("Identity Service Terminal", "Low-level access to Go-tcha Evolve Identity characteristics.", Icons.Default.Terminal) {
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
fun ChallengeResponseScreen(viewModel: TerminalViewModel) {
    val logs by viewModel.researchLogs.collectAsState()
    val sdf = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Challenge-Response") },
            navigationIcon = { IconButton(onClick = { viewModel.navigateTo(TerminalViewModel.Screen.RESEARCH_MENU) }) { Icon(Icons.Default.ArrowBack, contentDescription = null) } },
            actions = {
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
fun SecurityMonitorScreen(viewModel: TerminalViewModel) {
    val logs by viewModel.researchLogs.collectAsState()
    
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Security Key Monitor") },
            navigationIcon = { IconButton(onClick = { viewModel.navigateTo(TerminalViewModel.Screen.RESEARCH_MENU) }) { Icon(Icons.Default.ArrowBack, contentDescription = null) } },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BleTerminalScreen(viewModel: TerminalViewModel) {
    var serviceInput by remember { mutableStateOf("addc3e26-4aa5-4c1a-8a6a-735db4e01c6c") }
    var charInput by remember { mutableStateOf("addc3e26-4aa5-4c1a-8a6a-735db4e01c6f") }
    var dataInput by remember { mutableStateOf("") }
    val charValues by viewModel.characteristicValues.collectAsState()
    val logs by viewModel.logs.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Identity Terminal") },
            navigationIcon = { IconButton(onClick = { viewModel.navigateTo(TerminalViewModel.Screen.RESEARCH_MENU) }) { Icon(Icons.Default.ArrowBack, contentDescription = null) } }
        )

        Column(modifier = Modifier.padding(16.dp)) {
            OutlinedTextField(value = serviceInput, onValueChange = { serviceInput = it }, label = { Text("Service UUID") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = charInput, onValueChange = { charInput = it }, label = { Text("Char UUID") }, modifier = Modifier.fillMaxWidth())
            
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.readCharacteristic(UUID.fromString(serviceInput), UUID.fromString(charInput)) }, modifier = Modifier.weight(1f)) { Text("Read") }
                Button(onClick = { viewModel.toggleNotification(UUID.fromString(serviceInput), UUID.fromString(charInput)) }, modifier = Modifier.weight(1f)) { Text("Notify") }
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = dataInput, onValueChange = { dataInput = it }, label = { Text("Hex Data") }, modifier = Modifier.weight(1f))
                IconButton(onClick = { viewModel.writeCharacteristic(UUID.fromString(serviceInput), UUID.fromString(charInput), dataInput) }) {
                    Icon(Icons.Default.Send, contentDescription = null)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Go-tcha Identity Shortcuts:", style = MaterialTheme.typography.labelLarge)
            
            FlowRow(modifier = Modifier.fillMaxWidth(), maxItemsInEachRow = 3) {
                ShortcutChip("Read MAC") { charInput = "addc3e26-4aa5-4c1a-8a6a-735db4e01c6f"; viewModel.readMac() }
                ShortcutChip("Read Adv") { charInput = "addc3e26-4aa5-4c1a-8a6a-735db4e01c70"; viewModel.readAdvert() }
                ShortcutChip("Status") { charInput = "addc3e26-4aa5-4c1a-8a6a-735db4e01c71"; viewModel.readStatus() }
                ShortcutChip("Dump") { viewModel.dumpIdentity() }
                ShortcutChip("Compare") { viewModel.navigateTo(TerminalViewModel.Screen.COMPARISON) }
            }

            Spacer(modifier = Modifier.height(16.dp))
            LogWindow(logs)
        }
    }
}

@Composable
fun ShortcutChip(label: String, onClick: () -> Unit) {
    AssistChip(onClick = onClick, label = { Text(label, fontSize = 10.sp) }, modifier = Modifier.padding(2.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRow(modifier: Modifier = Modifier, maxItemsInEachRow: Int = Int.MAX_VALUE, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        content()
    }
}
