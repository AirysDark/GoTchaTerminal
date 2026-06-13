package com.airysdark.gotchaterminal.wear.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.wear.compose.material3.*
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn

@Composable
fun ToolsScreen(navController: NavController) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = "Field Tools",
                style = MaterialTheme.typography.titleMedium
            )
        }
        item {
            Button(
                onClick = { navController.navigate("adv_viewer") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Text("Adv Viewer")
            }
        }
        item {
            Button(
                onClick = { navController.navigate("gatt_browser") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Text("GATT Browser")
            }
        }
        item {
            Button(
                onClick = { navController.navigate("gotcha_health") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Text("Go-tcha Health")
            }
        }
        item {
            Button(
                onClick = { navController.navigate("debug_console") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Text("Debug Console")
            }
        }
    }
}
