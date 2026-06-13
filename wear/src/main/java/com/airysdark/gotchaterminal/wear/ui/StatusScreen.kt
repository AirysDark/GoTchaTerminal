package com.airysdark.gotchaterminal.wear.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.wear.compose.material3.*
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import com.airysdark.gotchaterminal.ble.BLEManager
import com.airysdark.gotchaterminal.wear.viewmodel.WearTerminalViewModel

@Composable
fun StatusScreen(
    navController: NavController,
    viewModel: WearTerminalViewModel = viewModel()
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = "GoTcha Status",
                style = MaterialTheme.typography.titleMedium
            )
        }
        item {
            Card(
                onClick = { },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    val statusText = when (connectionState) {
                        BLEManager.ConnectionState.CONNECTED -> "Connected"
                        BLEManager.ConnectionState.CONNECTING -> "Connecting..."
                        else -> "Disconnected"
                    }
                    Text("Device: $statusText", style = MaterialTheme.typography.bodyMedium)
                    if (isRecording) {
                        Text(
                            text = "REC: Active Session", 
                            color = MaterialTheme.colorScheme.error, 
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
        item {
            Button(
                onClick = { navController.navigate("devices") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Text("Devices")
            }
        }
        item {
            Button(
                onClick = { viewModel.triggerSync() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Text("Sync To Phone")
            }
        }
        item {
            Button(
                onClick = { navController.navigate("terminal") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Text("Terminal")
            }
        }
        item {
            Button(
                onClick = { navController.navigate("capture") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Text("Capture")
            }
        }
        item {
            Button(
                onClick = { navController.navigate("tools") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Text("Tools")
            }
        }
        item {
            Button(
                onClick = { navController.navigate("research") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Text("Research")
            }
        }
    }
}
