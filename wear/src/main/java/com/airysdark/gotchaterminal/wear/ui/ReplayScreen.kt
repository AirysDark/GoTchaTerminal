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
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ReplayScreen(
    navController: NavController,
    viewModel: WearTerminalViewModel = viewModel()
) {
    val sessions by viewModel.allSessions.collectAsState()
    val isReplaying by viewModel.isReplaying.collectAsState()
    val progress by viewModel.replayProgress.collectAsState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = "Packet Replay",
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (isReplaying) {
            item {
                Card(onClick = {}) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Replaying...", style = MaterialTheme.typography.labelMedium)
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                    }
                }
            }
        }

        if (sessions.isEmpty()) {
            item {
                Text(
                    text = "No sessions recorded.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        items(sessions) { session ->
            Card(
                onClick = { viewModel.replaySession(session.id) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isReplaying
            ) {
                Column {
                    Text(session.name, style = MaterialTheme.typography.bodyMedium)
                    val dateStr = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(session.startTime))
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
