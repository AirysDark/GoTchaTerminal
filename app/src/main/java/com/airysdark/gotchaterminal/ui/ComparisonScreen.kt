package com.airysdark.gotchaterminal.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airysdark.gotchaterminal.ble.BleSession
import com.airysdark.gotchaterminal.ble.ComparisonResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComparisonScreen(
    realSession: BleSession?,
    espSession: BleSession?,
    comparisonResult: ComparisonResult?,
    isCapturing: Boolean,
    onBack: () -> Unit,
    onStartCapture: (Boolean) -> Unit,
    onStopCapture: (Boolean) -> Unit,
    onClear: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Comparison Mode") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                TextButton(onClick = onClear) {
                    Text("Clear All")
                }
            }
        )

        Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            CaptureCard(
                modifier = Modifier.weight(1f).padding(4.dp),
                title = "Real Go-tcha",
                session = realSession,
                isCapturing = isCapturing && realSession?.name == "Real Go-tcha",
                onStart = { onStartCapture(true) },
                onStop = { onStopCapture(true) }
            )

            CaptureCard(
                modifier = Modifier.weight(1f).padding(4.dp),
                title = "ESP32 Clone",
                session = espSession,
                isCapturing = isCapturing && espSession?.name == "ESP32",
                onStart = { onStartCapture(false) },
                onStop = { onStopCapture(false) }
            )
        }

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        if (comparisonResult != null) {
            ComparisonResultView(comparisonResult)
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Capture packets from both devices to compare protocol parity",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    modifier = Modifier.padding(32.dp)
                )
            }
        }
    }
}

@Composable
fun CaptureCard(
    modifier: Modifier,
    title: String,
    session: BleSession?,
    isCapturing: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (isCapturing) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onStop, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("Stop", fontSize = 12.sp)
                }
            } else {
                Button(
                    onClick = onStart,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (session != null) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(if (session != null) Icons.Default.Refresh else Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(if (session != null) "Recapture" else "Start", fontSize = 12.sp)
                }
            }
            
            if (session != null && !isCapturing) {
                Text("${session.packets.size} packets captured", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
fun ComparisonResultView(result: ComparisonResult) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        // Match/Mismatch Status
        Surface(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            color = if (result.diffs.isEmpty()) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, if (result.diffs.isEmpty()) Color(0xFF4CAF50) else Color(0xFFD32F2F))
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (result.diffs.isEmpty()) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (result.diffs.isEmpty()) Color(0xFF2E7D32) else Color(0xFFC62828)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        if (result.diffs.isEmpty()) "PROTOCOL MATCH" else "PROTOCOL DEVIATION",
                        fontWeight = FontWeight.ExtraBold,
                        color = if (result.diffs.isEmpty()) Color(0xFF1B5E20) else Color(0xFFB71C1C)
                    )
                    Text(
                        if (result.diffs.isEmpty()) "Both devices behave identically" else "${result.diffs.size} issues found",
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Detailed Diffs
        if (result.diffs.isNotEmpty()) {
            Text("Inconsistency List", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
            Spacer(modifier = Modifier.height(8.dp))
            result.diffs.forEach { diff ->
                Text("• $diff", fontSize = 13.sp, color = Color(0xFFB71C1C), fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 4.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Session Comparison Grid
        Text("Detailed Session Data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            SessionSpecColumn(Modifier.weight(1f), "REAL GOTCHA", result.sessionA)
            Spacer(modifier = Modifier.width(8.dp))
            SessionSpecColumn(Modifier.weight(1f), "ESP32 CLONE", result.sessionB)
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun SessionSpecColumn(modifier: Modifier, label: String, session: BleSession) {
    Column(modifier = modifier.background(Color.LightGray.copy(alpha = 0.1f)).padding(8.dp)) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.Gray)
        Text("Dev Name: ${session.deviceName ?: "Unknown"}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        
        Spacer(modifier = Modifier.height(8.dp))
        Text("Advertised Services:", fontSize = 10.sp, fontWeight = FontWeight.Bold)
        session.advertisements.forEach { adv ->
            adv.serviceUuids.forEach { uuid ->
                Text("• ${uuid.take(8)}", fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("GATT Services:", fontSize = 10.sp, fontWeight = FontWeight.Bold)
        session.services.forEach { s ->
            Text("• ${s.uuid.take(8)}...", fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            s.characteristics.forEach { c ->
                Text("  └ ${c.uuid.take(4)}...", fontSize = 8.sp, color = Color.Gray)
            }
        }
    }
}
