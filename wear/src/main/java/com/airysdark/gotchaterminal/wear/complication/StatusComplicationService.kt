package com.airysdark.gotchaterminal.wear.complication

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import com.airysdark.gotchaterminal.ble.BLEManager
import com.airysdark.gotchaterminal.wear.MainActivity

class StatusComplicationService : ComplicationDataSourceService() {
    private val bleManager by lazy { BLEManager.getInstance(this) }

    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener
    ) {
        val connectionState = bleManager.connectionState.value
        
        val tapIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, tapIntent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val text = when(connectionState) {
            BLEManager.ConnectionState.CONNECTED -> "CONN"
            BLEManager.ConnectionState.CONNECTING -> "..."
            else -> "DISC"
        }

        val complicationData = when (request.complicationType) {
            ComplicationType.SHORT_TEXT -> {
                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(text).build(),
                    contentDescription = PlainComplicationText.Builder("Connection Status").build()
                )
                .setTapAction(pendingIntent)
                .build()
            }
            ComplicationType.LONG_TEXT -> {
                LongTextComplicationData.Builder(
                    text = PlainComplicationText.Builder("Go-tcha: $text").build(),
                    contentDescription = PlainComplicationText.Builder("Connection Status").build()
                )
                .setTapAction(pendingIntent)
                .build()
            }
            else -> null
        }

        if (complicationData != null) {
            listener.onComplicationData(complicationData)
        }
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        return when (type) {
            ComplicationType.SHORT_TEXT -> {
                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder("CONN").build(),
                    contentDescription = PlainComplicationText.Builder("Status").build()
                ).build()
            }
            else -> null
        }
    }
}
