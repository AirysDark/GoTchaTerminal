package com.airysdark.gotchaterminal.wear.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.*
import com.airysdark.gotchaterminal.wear.viewmodel.WearTerminalViewModel

@Composable
fun DebugConsoleScreen(
    navController: NavController,
    viewModel: WearTerminalViewModel = viewModel()
) {
    val logs by viewModel.logs.collectAsState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = "Debug Console",
                style = MaterialTheme.typography.titleMedium
            )
        }

        items(logs.reversed()) { log ->
            Card(
                onClick = { },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = log,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = Color.LightGray
                )
            }
        }
    }
}
