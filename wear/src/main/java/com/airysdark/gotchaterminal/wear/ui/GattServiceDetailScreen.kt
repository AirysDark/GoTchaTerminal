package com.airysdark.gotchaterminal.wear.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.*
import com.airysdark.gotchaterminal.wear.viewmodel.WearTerminalViewModel

@Composable
fun GattServiceDetailScreen(
    serviceUuid: String,
    navController: NavController,
    viewModel: WearTerminalViewModel = viewModel()
) {
    val services by viewModel.discoveredServices.collectAsState()
    val service = remember(services) {
        services.find { it.uuid.toString() == serviceUuid }
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = "Service Details",
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (service == null) {
            item { 
                Text(
                    text = "Service not found", 
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                ) 
            }
        } else {
            item {
                Text(
                    text = "UUID: ${service.uuid.toString().take(12)}...",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            
            item {
                Text(
                    text = "Characteristics",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(service.characteristics) { char ->
                Card(
                    onClick = { 
                        viewModel.setTarget(service.uuid.toString(), char.uuid.toString())
                        navController.navigate("terminal")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text(
                            text = char.uuid.toString().take(8),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = decodeProperties(char.properties),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

private fun decodeProperties(props: Int): String {
    val list = mutableListOf<String>()
    if (props and 0x02 != 0) list.add("READ")
    if (props and 0x08 != 0) list.add("WRITE")
    if (props and 0x04 != 0) list.add("WRITE_NR")
    if (props and 0x10 != 0) list.add("NOTIFY")
    if (props and 0x20 != 0) list.add("INDICATE")
    return if (list.isEmpty()) "NONE" else list.joinToString("|")
}
