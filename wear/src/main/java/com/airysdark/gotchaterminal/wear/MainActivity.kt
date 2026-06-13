package com.airysdark.gotchaterminal.wear

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.airysdark.gotchaterminal.wear.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val permissions = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    add(Manifest.permission.BLUETOOTH_SCAN)
                    add(Manifest.permission.BLUETOOTH_CONNECT)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            val launcher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { /* Handle results if needed */ }

            LaunchedEffect(Unit) {
                launcher.launch(permissions.toTypedArray())
            }

            WearApp()
        }
    }
}

@Composable
fun WearApp() {
    val navController = rememberSwipeDismissableNavController()
    MaterialTheme {
        SwipeDismissableNavHost(
            navController = navController,
            startDestination = "status",
            modifier = Modifier.fillMaxSize()
        ) {
            composable("status") { StatusScreen(navController) }
            composable("devices") { DevicesScreen(navController) }
            composable("terminal") { TerminalScreen(navController) }
            composable("capture") { CaptureScreen(navController) }
            composable("tools") { ToolsScreen(navController) }
            composable("research") { ResearchScreen(navController) }
            
            // Phase 4 Tools
            composable("adv_viewer") { AdvViewerScreen(navController) }
            composable("gatt_browser") { GattBrowserScreen(navController) }
            composable(
                route = "gatt_service_detail/{serviceUuid}",
                arguments = listOf(navArgument("serviceUuid") { type = NavType.StringType })
            ) { backStackEntry ->
                val serviceUuid = backStackEntry.arguments?.getString("serviceUuid") ?: ""
                GattServiceDetailScreen(serviceUuid, navController)
            }
            composable("gotcha_health") { GotchaHealthScreen(navController) }
            composable("debug_console") { DebugConsoleScreen(navController) }
            
            // Phase 4 Research
            composable("challenge_monitor") { ChallengeMonitorScreen(navController) }
            composable("uuid_browser") { UuidBrowserScreen(navController) }
            composable("packet_replay") { ReplayScreen(navController) }
            composable("service_diff") { ServiceDiffScreen(navController) }
        }
    }
}
