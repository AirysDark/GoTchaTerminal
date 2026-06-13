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
import com.airysdark.gotchaterminal.wear.viewmodel.WearTerminalViewModel

@Composable
fun CaptureScreen(
    navController: NavController,
    viewModel: WearTerminalViewModel = viewModel()
) {
    val isRecording by viewModel.isRecording.collectAsState()
    val packetCount by viewModel.packetCount.collectAsState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = "Session Capture",
                style = MaterialTheme.typography.titleMedium
            )
        }
        item {
            Text(
                text = if (isRecording) "RECORDING" else "READY",
                color = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium
            )
        }
        item {
            Card(
                onClick = { 
                    if (isRecording) viewModel.stopRecording() 
                    else viewModel.startRecording("Watch_Session_${System.currentTimeMillis()}")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isRecording) "Stop Recording" else "Start Recording", modifier = Modifier.padding(8.dp))
            }
        }
        item {
            Text("Monitor", style = MaterialTheme.typography.labelSmall)
        }
        item {
            Text("Packets: $packetCount", style = MaterialTheme.typography.bodyMedium)
        }
        item {
            Button(
                onClick = { /* Add marker logic in VM if needed */ },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                enabled = isRecording
            ) {
                Text("Add Marker")
            }
        }
    }
}
