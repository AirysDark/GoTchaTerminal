package com.airysdark.gotchaterminal.wear.complication

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import com.airysdark.gotchaterminal.storage.AppDatabase
import com.airysdark.gotchaterminal.wear.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PacketCountComplicationService : ComplicationDataSourceService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database by lazy { AppDatabase.getDatabase(this) }

    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener
    ) {
        scope.launch {
            val sessions = database.captureDao().getAllSessions().first()
            val activeSession = sessions.firstOrNull { it.endTime == null }
            
            var count = 0
            if (activeSession != null) {
                count = database.captureDao().getPacketsForSessionList(activeSession.id).size
            }

            val tapIntent = Intent(this@PacketCountComplicationService, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                this@PacketCountComplicationService, 
                0, 
                tapIntent, 
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val complicationData = when (request.complicationType) {
                ComplicationType.SHORT_TEXT -> {
                    ShortTextComplicationData.Builder(
                        text = PlainComplicationText.Builder(count.toString()).build(),
                        contentDescription = PlainComplicationText.Builder("Packet Count").build()
                    ).setTapAction(pendingIntent)
                        .build()
                }
                ComplicationType.LONG_TEXT -> {
                    LongTextComplicationData.Builder(
                        text = PlainComplicationText.Builder("$count Packets").build(),
                        contentDescription = PlainComplicationText.Builder("Packet Count").build()
                    ).setTapAction(pendingIntent)
                        .build()
                }
                else -> null
            }
            if (complicationData != null) {
                listener.onComplicationData(complicationData)
            }
        }
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        return when (type) {
            ComplicationType.SHORT_TEXT -> {
                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder("123").build(),
                    contentDescription = PlainComplicationText.Builder("Packet Count").build()
                ).build()
            }
            else -> null
        }
    }
}
