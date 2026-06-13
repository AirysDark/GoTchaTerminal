package com.airysdark.gotchaterminal.wear.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.wear.compose.material.*
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn

@Composable
fun ResearchScreen(navController: NavController) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = "Research Mode",
                style = MaterialTheme.typography.title2
            )
        }
        item {
            Button(
                onClick = { navController.navigate("challenge_monitor") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Text("Challenge Mon")
            }
        }
        item {
            Button(
                onClick = { navController.navigate("uuid_browser") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Text("UUID Browser")
            }
        }
        item {
            Button(
                onClick = { navController.navigate("packet_replay") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Text("Packet Replay")
            }
        }
        item {
            Button(
                onClick = { navController.navigate("service_diff") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Text("Service Diff")
            }
        }
    }
}
