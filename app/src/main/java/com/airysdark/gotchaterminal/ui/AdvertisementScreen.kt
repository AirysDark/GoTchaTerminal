package com.airysdark.gotchaterminal.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airysdark.gotchaterminal.ble.AdvertisementManager
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdvertisementScreen(viewModel: TerminalViewModel) {
    val isAdvertising by viewModel.advManager.isAdvertising.collectAsState()
    val logs by viewModel.advManager.logs.collectAsState()
    val presets = viewModel.advManager.presets

    var selectedProfile by remember { mutableStateOf(presets[0]) }
    var customName by remember { mutableStateOf("Custom GoTcha") }
    var customUuid by remember { mutableStateOf("0000fee5-0000-1000-8000-00805f9b34fb") }
    var showCustomBuilder by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Advertisement Lab") },
            navigationIcon = {
                IconButton(onClick = { viewModel.navigateTo(TerminalViewModel.Screen.MAIN) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        Column(modifier = Modifier.padding(16.dp).weight(1f).verticalScroll(rememberScrollState())) {
            Text("Select Profile Preset:", style = MaterialTheme.typography.titleMedium)
            FlowRow(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { profile ->
                    FilterChip(
                        selected = selectedProfile == profile && !showCustomBuilder,
                        onClick = { 
                            selectedProfile = profile 
                            showCustomBuilder = false
                        },
                        label = { Text(profile.name) }
                    )
                }
                FilterChip(
                    selected = showCustomBuilder,
                    onClick = { showCustomBuilder = true },
                    label = { Text("Custom Builder") }
                )
            }

            if (showCustomBuilder) {
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Custom Advertisement", fontWeight = FontWeight.Bold, color = Color.Cyan)
                        OutlinedTextField(
                            value = customName,
                            onValueChange = { customName = it },
                            label = { Text("Device Name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = customUuid,
                            onValueChange = { customUuid = it },
                            label = { Text("Service UUID") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Profile Details", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        DetailRow("Device Name", selectedProfile.deviceName)
                        DetailRow("Connectable", selectedProfile.connectable.toString())
                        DetailRow("Services", selectedProfile.serviceUuids.joinToString(", ") { it.toString().take(8) })
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (isAdvertising) {
                        viewModel.advManager.stopAdvertising()
                    } else {
                        if (showCustomBuilder) {
                            try {
                                val profile = AdvertisementManager.AdvProfile(
                                    "Custom",
                                    customName,
                                    listOf(UUID.fromString(customUuid))
                                )
                                viewModel.advManager.startAdvertising(profile)
                            } catch (e: Exception) {
                                viewModel.bleManager.addLog("Invalid Custom UUID")
                            }
                        } else {
                            viewModel.advManager.startAdvertising(selectedProfile)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isAdvertising) Color.Red else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(if (isAdvertising) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isAdvertising) "STOP ADVERTISING" else "START ADVERTISING")
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Lab Logs:", style = MaterialTheme.typography.titleSmall)
            Box(modifier = Modifier.fillMaxWidth().height(200.dp).padding(top = 8.dp)) {
                LogWindow(logs)
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}
