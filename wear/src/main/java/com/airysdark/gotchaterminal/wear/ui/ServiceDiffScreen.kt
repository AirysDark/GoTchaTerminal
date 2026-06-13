package com.airysdark.gotchaterminal.wear.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
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
fun ServiceDiffScreen(
    navController: NavController,
    viewModel: WearTerminalViewModel = viewModel()
) {
    val sessions by viewModel.allSessions.collectAsState()
    val diffResults by viewModel.diffResults.collectAsState()
    
    var selectedSessionId by remember { mutableLongStateOf(-1L) }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = "Service Diff",
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (selectedSessionId == -1L) {
            item {
                Text(
                    text = "Select session to compare",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(8.dp)
                )
            }
            items(sessions) { session ->
                Card(
                    onClick = { 
                        selectedSessionId = session.id 
                        viewModel.performServiceDiff(session.id)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(session.name, style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            item {
                Button(
                    onClick = { 
                        selectedSessionId = -1L
                        viewModel.clearDiff()
                    }, 
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Text("Back")
                }
            }
            items(diffResults) { res ->
                Text(
                    text = res, 
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Cyan,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}
