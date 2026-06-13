package com.airysdark.gotchaterminal.wear.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.*
import com.airysdark.gotchaterminal.wear.viewmodel.WearTerminalViewModel

@Composable
fun ChallengeMonitorScreen(
    navController: NavController,
    viewModel: WearTerminalViewModel = viewModel()
) {
    val stream by viewModel.terminalStream.collectAsState()
    // Filter for challenge-like packets (usually writes to specific chars)
    val challenges = remember(stream) {
        stream.filter { it.contains("WRITE") && (it.contains("addc") || it.contains("b695")) }
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = "Challenge Mon",
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (challenges.isEmpty()) {
            item {
                Text(
                    text = "No auth packets detected.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        items(challenges) { packet ->
            Card(
                onClick = { },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = packet,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Yellow,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}
