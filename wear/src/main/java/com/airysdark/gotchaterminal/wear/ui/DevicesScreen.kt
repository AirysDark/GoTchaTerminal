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
import androidx.wear.compose.foundation.lazy.items
import com.airysdark.gotchaterminal.core.DeviceModel
import com.airysdark.gotchaterminal.wear.viewmodel.WearTerminalViewModel

@Composable
fun DevicesScreen(
    navController: NavController,
    viewModel: WearTerminalViewModel = viewModel()
) {
    val devices: List<DeviceModel> by viewModel.discoveredDevices.collectAsState(initial = emptyList())
    val isScanning by viewModel.isScanning.collectAsState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = "Nearby Devices",
                style = MaterialTheme.typography.titleMedium
            )
        }
        item {
            Button(
                onClick = { if (isScanning) viewModel.stopScan() else viewModel.startScan() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Text(if (isScanning) "Stop Scan" else "Start Scan")
            }
        }
        
        items(devices) { device: DeviceModel ->
            Card(
                onClick = { 
                    viewModel.connect(device)
                    navController.popBackStack() 
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(device.name ?: "Unknown Device", style = MaterialTheme.typography.bodyMedium)
                    Text(device.address, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
