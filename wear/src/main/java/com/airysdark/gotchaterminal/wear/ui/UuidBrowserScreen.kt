package com.airysdark.gotchaterminal.wear.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.airysdark.gotchaterminal.protocol.GoTchaUUIDs

@Composable
fun UuidBrowserScreen(navController: NavController) {
    val uuids = remember {
        listOf(
            "Flash Svc" to GoTchaUUIDs.FLASH_SERVICE,
            "Flash Cmd" to GoTchaUUIDs.FLASH_COMMAND,
            "Flash Write" to GoTchaUUIDs.FLASH_WRITE,
            "Identity Svc" to GoTchaUUIDs.IDENTITY_SERVICE,
            "Identity Status" to GoTchaUUIDs.IDENTITY_STATUS,
            "SUOTA Svc" to GoTchaUUIDs.SUOTA_SERVICE,
            "Battery Svc" to GoTchaUUIDs.BATTERY_SERVICE,
            "Dev Info" to GoTchaUUIDs.DEVICE_INFO
        )
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = "UUID Lookup",
                style = MaterialTheme.typography.titleMedium
            )
        }

        items(uuids) { (name, uuid) ->
            Card(
                onClick = { },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(name, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = uuid.take(18) + "...",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}
