package com.airysdark.gotchaterminal.wear.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.wear.compose.material3.*
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import com.airysdark.gotchaterminal.wear.viewmodel.WearTerminalViewModel

@Composable
fun TerminalScreen(
    navController: NavController,
    viewModel: WearTerminalViewModel = viewModel()
) {
    val stream by viewModel.terminalStream.collectAsState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = "Pocket Terminal",
                style = MaterialTheme.typography.titleMedium
            )
        }

        // Live Stream Console
        item {
            Card(
                onClick = { },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black)
            ) {
                val scrollState = rememberScrollState()
                LaunchedEffect(stream.size) {
                    scrollState.animateScrollTo(scrollState.maxValue)
                }
                
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                ) {
                    if (stream.isEmpty()) {
                        Text(
                            "Waiting for data...",
                            color = Color.DarkGray,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace
                        )
                    } else {
                        stream.forEach { line ->
                            Text(
                                line,
                                color = if (line.contains("WRITE")) Color.Cyan else Color.Green,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                "Quick Actions", 
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), 
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Button(
                    onClick = { viewModel.readSettings() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Settings", style = MaterialTheme.typography.labelSmall)
                }
                Button(
                    onClick = { viewModel.readBattery() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Battery", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), 
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Button(
                    onClick = { viewModel.enableFlashNotifications() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Notify", style = MaterialTheme.typography.labelSmall)
                }
                Button(
                    onClick = { viewModel.resetDevice() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Reset", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        item {
            Text(
                "Advanced", 
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        item {
            Button(
                onClick = { viewModel.setTime() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Text("Sync Time")
            }
        }

        item {
            Button(
                onClick = { viewModel.readSteps() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Text("Read Steps")
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
