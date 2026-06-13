package com.airysdark.gotchaterminal.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.airysdark.gotchaterminal.models.firmware.FirmwareInfo

@Composable
fun FirmwarePicker(
    selectedFirmware: FirmwareInfo?,
    onFirmwareSelected: (Uri) -> Unit,
    onInternalFirmwareSelected: (String) -> Unit,
    internalFirmwares: List<String>,
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { onFirmwareSelected(it) }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Firmware Management", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { launcher.launch(arrayOf("application/octet-stream", "application/x-binary")) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Select Firmware (.bin)")
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("Internal Library:", style = MaterialTheme.typography.labelLarge)
        
        internalFirmwares.forEach { fw ->
            OutlinedButton(
                onClick = { onInternalFirmwareSelected(fw) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
            ) {
                Text(fw)
            }
        }

        selectedFirmware?.let { fw: FirmwareInfo ->
            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Selected: ${fw.name}", style = MaterialTheme.typography.bodyLarge)
                    Text("Size: ${fw.size} bytes", style = MaterialTheme.typography.bodySmall)
                    Text("CRC32: ${fw.crc32}", style = MaterialTheme.typography.bodySmall)
                    Text("SHA256: ${fw.sha256.take(16)}...", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
