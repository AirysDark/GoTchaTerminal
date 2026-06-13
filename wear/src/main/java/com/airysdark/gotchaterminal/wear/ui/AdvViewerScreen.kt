package com.airysdark.gotchaterminal.wear.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.*
import com.airysdark.gotchaterminal.models.ble.AdvertInfo
import com.airysdark.gotchaterminal.wear.viewmodel.WearTerminalViewModel

@Composable
fun AdvViewerScreen(
    navController: NavController,
    viewModel: WearTerminalViewModel = viewModel(),
) {
    val adverts by viewModel.recentAdverts.collectAsState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = "Adv Viewer",
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (adverts.isEmpty()) {
            item {
                Text(
                    text = "No data. Start scan in Devices menu.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        items(adverts) { info: AdvertInfo ->
            Card(
                onClick = { },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(info.name ?: "Unknown", style = MaterialTheme.typography.bodyMedium)
                    Text(info.address, style = MaterialTheme.typography.labelSmall)
                    Text("RSSI: ${info.rssi} dBm", style = MaterialTheme.typography.labelSmall)
                    
                    val serviceUuids = info.serviceUuids ?: emptyList()
                    if (serviceUuids.isNotEmpty()) {
                        Text("Services: ${serviceUuids.size}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
