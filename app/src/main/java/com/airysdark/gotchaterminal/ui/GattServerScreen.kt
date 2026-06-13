package com.airysdark.gotchaterminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.airysdark.gotchaterminal.ble.GattServerManager

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GattServerScreen(viewModel: TerminalViewModel) {
    val isRunning by viewModel.gattServerManager.isServerRunning.collectAsState()
    val connectedDevices by viewModel.gattServerManager.connectedDevices.collectAsState()
    val logs by viewModel.gattServerManager.logs.collectAsState()
    val services by viewModel.gattServerManager.serverServices.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("GATT Server Lab") },
            navigationIcon = {
                IconButton(onClick = { viewModel.navigateTo(TerminalViewModel.Screen.MAIN) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(onClick = { viewModel.replayCurrentSession() }) {
                    Icon(Icons.Default.Replay, contentDescription = "Replay Session")
                }
            }
        )

        Column(modifier = Modifier.padding(16.dp).weight(1f).verticalScroll(rememberScrollState())) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (isRunning) viewModel.gattServerManager.stopServer()
                        else viewModel.gattServerManager.startServer()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = if (isRunning) Color.Red else MaterialTheme.colorScheme.primary)
                ) {
                    Text(if (isRunning) "STOP SERVER" else "START SERVER")
                }

                OutlinedButton(onClick = { viewModel.gattServerManager.clearServices() }, modifier = Modifier.weight(1f)) {
                    Text("CLEAR SERVICES")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Emulation Profiles:", style = MaterialTheme.typography.titleSmall)
            FlowRow(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Go-tcha Evolve", "Pokemon GO Plus", "Custom").forEach { profile ->
                    AssistChip(onClick = { viewModel.emulateProfile(profile) }, label = { Text(profile) })
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Active Services (${services.size}):", style = MaterialTheme.typography.titleSmall)
            services.forEach { service ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("UUID: ${service.uuid}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Text("${service.characteristics.size} Characteristics", fontSize = 10.sp, color = Color.Gray)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Connected Clients (${connectedDevices.size}):", style = MaterialTheme.typography.titleSmall)
            connectedDevices.forEach { device ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(device.address, modifier = Modifier.padding(8.dp), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Server Activity Logs:", style = MaterialTheme.typography.titleSmall)
            Box(modifier = Modifier.fillMaxWidth().height(250.dp).padding(top = 8.dp)) {
                LogWindow(logs)
            }
        }
    }
}
