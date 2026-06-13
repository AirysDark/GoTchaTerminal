package com.airysdark.gotchaterminal.wear.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.*
import com.airysdark.gotchaterminal.ble.BLEManager
import com.airysdark.gotchaterminal.wear.viewmodel.WearTerminalViewModel

@Composable
fun GattBrowserScreen(
    navController: NavController,
    viewModel: WearTerminalViewModel = viewModel()
) {
    val services by viewModel.discoveredServices.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = "GATT Browser",
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (services.isEmpty()) {
            item {
                Text(
                    text = if (connectionState == BLEManager.ConnectionState.CONNECTED) 
                        "Discovering services..." else "Not connected.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        items(services) { service ->
            Card(
                onClick = { 
                    navController.navigate("gatt_service_detail/${service.uuid}")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "Svc: ${service.uuid.toString().take(8)}",
                        style = MaterialTheme.typography.labelMedium
                    )
                    @Suppress("DEPRECATION")
                    Text(
                        text = "${service.characteristics.size} chars",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
