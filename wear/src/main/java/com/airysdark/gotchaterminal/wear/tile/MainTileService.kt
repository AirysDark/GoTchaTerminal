package com.airysdark.gotchaterminal.wear.tile

import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.airysdark.gotchaterminal.ble.BLEManager
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class MainTileService : TileService() {
    private val bleManager by lazy { BLEManager.getInstance(this) }

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val deviceParams = requestParams.deviceConfiguration
        val connectionState = bleManager.connectionState.value

        val statusText = when (connectionState) {
            BLEManager.ConnectionState.CONNECTED -> "Connected"
            BLEManager.ConnectionState.CONNECTING -> "Connecting"
            else -> "Disconnected"
        }

        val statusColor = when (connectionState) {
            BLEManager.ConnectionState.CONNECTED -> 0xFF00FF00.toInt()
            BLEManager.ConnectionState.CONNECTING -> 0xFFFFFF00.toInt()
            else -> 0xFFFF0000.toInt()
        }

        val rootLayout = PrimaryLayout.Builder(deviceParams)
            .setResponsiveContentInsetEnabled(true)
            .setContent(
                LayoutElementBuilders.Column.Builder()
                    .addContent(
                        Text.Builder(this, "GoTcha Status")
                            .setTypography(Typography.TYPOGRAPHY_TITLE3)
                            .setColor(argb(0xFFAAAAAA.toInt()))
                            .build()
                    )
                    .addContent(
                        Text.Builder(this, statusText)
                            .setTypography(Typography.TYPOGRAPHY_DISPLAY1)
                            .setColor(argb(statusColor))
                            .build()
                    )
                    .build()
            )
            .setPrimaryLabelTextContent(
                Text.Builder(this, "Tap to open")
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .build()
            )
            .build()

        return Futures.immediateFuture(
            TileBuilders.Tile.Builder()
                .setResourcesVersion("1")
                .setTileTimeline(
                    TimelineBuilders.Timeline.Builder()
                        .addTimelineEntry(
                            TimelineBuilders.TimelineEntry.Builder()
                                .setLayout(
                                    LayoutElementBuilders.Layout.Builder()
                                        .setRoot(rootLayout)
                                        .build()
                                )
                                .build()
                        )
                        .build()
                )
                .build()
        )
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        return Futures.immediateFuture(
            ResourceBuilders.Resources.Builder()
                .setVersion("1")
                .build()
        )
    }
}
