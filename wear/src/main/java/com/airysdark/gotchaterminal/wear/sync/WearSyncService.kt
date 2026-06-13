package com.airysdark.gotchaterminal.wear.sync

import android.util.Log
import com.airysdark.gotchaterminal.core.sync.SyncConstants
import com.google.android.gms.wearable.*

class WearSyncService : WearableListenerService() {
    private val TAG = "WearSyncService"

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "Message received: ${messageEvent.path}")
        when (messageEvent.path) {
            SyncConstants.PATH_START_SCAN -> {
                // TODO: Trigger BLE Scan in Watch app
            }
            SyncConstants.PATH_STOP_SCAN -> {
                // TODO: Stop BLE Scan in Watch app
            }
        }
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        Log.d(TAG, "Capability changed: ${capabilityInfo.name}")
    }
}
