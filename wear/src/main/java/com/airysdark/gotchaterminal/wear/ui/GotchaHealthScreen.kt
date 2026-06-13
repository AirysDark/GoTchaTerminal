package com.airysdark.gotchaterminal.wear.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.*
import com.airysdark.gotchaterminal.protocol.GoTchaUUIDs
import com.airysdark.gotchaterminal.wear.viewmodel.WearTerminalViewModel
import java.util.*

@Composable
fun GotchaHealthScreen(
    navController: NavController,
    viewModel: WearTerminalViewModel = viewModel()
) {
    val charValues by viewModel.characteristicValues.collectAsState()
    
    val batteryLevel = remember(charValues) {
        charValues[UUID.fromString(GoTchaUUIDs.BATTERY_LEVEL)]?.getOrNull(0)?.toInt()?.let { "$it%" } ?: "--"
    }
    
    val softwareRevision = remember(charValues) {
        charValues[UUID.fromString(GoTchaUUIDs.SOFTWARE_REVISION)]?.let { String(it) } ?: "Unknown"
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = "Go-tcha Health",
                style = MaterialTheme.typography.titleMedium
            )
        }

        item {
            Card(
                onClick = { viewModel.readBattery() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                    Text("Battery: ", style = MaterialTheme.typography.bodyMedium)
                    Text(batteryLevel, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        item {
            Card(
                onClick = { 
                    viewModel.readSoftwareRevision()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("Firmware:", style = MaterialTheme.typography.labelSmall)
                    Text(softwareRevision, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        item {
            Button(
                onClick = { viewModel.resetDevice() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Reboot Device")
            }
        }
    }
}
