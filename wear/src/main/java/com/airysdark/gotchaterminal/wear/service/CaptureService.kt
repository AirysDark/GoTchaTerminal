package com.airysdark.gotchaterminal.wear.service

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.airysdark.gotchaterminal.ble.BLEManager
import com.airysdark.gotchaterminal.core.PacketModel
import com.airysdark.gotchaterminal.storage.AppDatabase
import com.airysdark.gotchaterminal.storage.entities.AdvertisementCapture
import com.airysdark.gotchaterminal.storage.entities.ChallengeCapture
import com.airysdark.gotchaterminal.storage.entities.PacketCapture
import com.airysdark.gotchaterminal.storage.entities.SessionCapture
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class CaptureService : Service() {
    private val TAG = "CaptureService"
    private val notificationId = 1001
    private val channelId = "capture_service_channel"

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var bleManager: BLEManager
    private lateinit var database: AppDatabase

    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    private var currentSessionId: Long? = null
    private var packetCollectionJob: Job? = null
    private var advertCollectionJob: Job? = null

    inner class LocalBinder : Binder() {
        fun getService(): CaptureService = this@CaptureService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        bleManager = BLEManager.getInstance(this)
        database = AppDatabase.getDatabase(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_START_RECORDING -> {
                val sessionName = intent.getStringExtra(EXTRA_SESSION_NAME) ?: "Wear_Capture"
                startRecording(sessionName)
            }
            ACTION_STOP_RECORDING -> stopRecording()
        }
        return START_STICKY
    }

    private fun startRecording(name: String) {
        if (_isRecording.value) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Notification permission not granted")
                stopSelf()
                return
            }
        }

        startForeground(notificationId, createNotification("Starting capture..."))

        serviceScope.launch {
            val device = bleManager.getConnectedDevice()
            val deviceName = if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                try { device?.name } catch (e: SecurityException) { null }
            } else null
            
            val sessionId = database.captureDao().insertSession(
                SessionCapture(
                    name = name,
                    deviceName = deviceName,
                    deviceAddress = device?.address,
                    isSynced = false
                )
            )
            currentSessionId = sessionId
            _isRecording.value = true

            updateNotification("Recording session: $name")

            packetCollectionJob = serviceScope.launch {
                bleManager.packetStreamCore.collect { packet: PacketModel ->
                    if (_isRecording.value && currentSessionId != null) {
                        database.captureDao().insertPacket(
                            PacketCapture(
                                sessionId = currentSessionId!!,
                                type = packet.type,
                                serviceUuid = packet.serviceUuid ?: "N/A",
                                charUuid = packet.uuid,
                                data = packet.data,
                                timestamp = packet.timestamp
                            )
                        )
                        
                        // Simple Challenge detection
                        if (packet.type == "WRITE" && (packet.uuid.contains("addc") || packet.uuid.contains("b695"))) {
                            database.captureDao().insertChallenge(
                                ChallengeCapture(
                                    sessionId = currentSessionId!!,
                                    challengeData = packet.data,
                                    responseData = null, // To be updated if possible
                                    isSuccessful = true,
                                    timestamp = packet.timestamp
                                )
                            )
                        }
                    }
                }
            }

            advertCollectionJob = serviceScope.launch {
                @Suppress("DEPRECATION")
                bleManager.scanResultStream.collect { result: android.bluetooth.le.ScanResult ->
                    if (_isRecording.value) {
                        database.captureDao().insertAdvertisement(
                            AdvertisementCapture(
                                deviceName = try { result.scanRecord?.deviceName } catch (e: SecurityException) { null },
                                address = result.device.address,
                                rssi = result.rssi,
                                serviceUuids = result.scanRecord?.serviceUuids?.joinToString(",") { it.toString() } ?: "",
                                manufacturerData = result.scanRecord?.manufacturerSpecificData?.toString(),
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    }
                }
            }
        }
    }

    private fun stopRecording() {
        val sid = currentSessionId
        serviceScope.launch {
            if (sid != null) {
                database.captureDao().getSessionById(sid)?.let { session ->
                    database.captureDao().updateSession(session.copy(endTime = System.currentTimeMillis()))
                }
            }
            
            _isRecording.value = false
            packetCollectionJob?.cancel()
            advertCollectionJob?.cancel()
            currentSessionId = null
            
            withContext(Dispatchers.Main) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            channelId,
            "Capture Service Channel",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    private fun createNotification(content: String): Notification {
        val pendingIntent = Intent(this, com.airysdark.gotchaterminal.wear.MainActivity::class.java).let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("GoTcha Terminal")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(content: String) {
        val notification = createNotification(content)
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(notificationId, notification)
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        const val ACTION_START_RECORDING = "com.airysdark.gotchaterminal.wear.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.airysdark.gotchaterminal.wear.STOP_RECORDING"
        const val EXTRA_SESSION_NAME = "extra_session_name"
    }
}
