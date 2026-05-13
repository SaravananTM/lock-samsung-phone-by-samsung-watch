package com.lockphone.watch

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Button
import androidx.wear.protolayout.material.ButtonColors
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.Wearable
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.*
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.tasks.await

class LockTileService : TileService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        serviceScope.future {
            val lastClickableId = requestParams.currentState.lastClickableId
            if (lastClickableId == "lock_button") {
                sendLockCommand()
            }

            TileBuilders.Tile.Builder()
                .setResourcesVersion("1")
                .setTileTimeline(
                    TimelineBuilders.Timeline.Builder()
                        .addTimelineEntry(
                            TimelineBuilders.TimelineEntry.Builder()
                                .setLayout(
                                    LayoutElementBuilders.Layout.Builder()
                                        .setRoot(buildLayout(requestParams))
                                        .build()
                                )
                                .build()
                        )
                        .build()
                )
                .build()
        }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        serviceScope.future {
            ResourceBuilders.Resources.Builder()
                .setVersion("1")
                .build()
        }

    private fun buildLayout(requestParams: RequestBuilders.TileRequest): LayoutElementBuilders.LayoutElement {
        val deviceParams = requestParams.deviceConfiguration

        return PrimaryLayout.Builder(deviceParams)
            .setContent(
                Button.Builder(
                    this,
                    ModifiersBuilders.Clickable.Builder()
                        .setId("lock_button")
                        .setOnClick(
                            ActionBuilders.LoadAction.Builder().build()
                        )
                        .build()
                )
                    .setTextContent("LOCK")
                    .setButtonColors(
                        ButtonColors(
                            argb(0xFFFF0000.toInt()),
                            argb(0xFFFFFFFF.toInt())
                        )
                    )
                    .setSize(dp(80f))
                    .build()
            )
            .setPrimaryLabelTextContent(
                Text.Builder(this, "Lock Phone")
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .setColor(argb(0xFFFFFFFF.toInt()))
                    .build()
            )
            .build()
    }

    private suspend fun sendLockCommand() {
        try {
            val nodes = Wearable.getNodeClient(this).connectedNodes.await()
            val messageClient = Wearable.getMessageClient(this)
            for (node in nodes) {
                messageClient.sendMessage(node.id, "/lock-phone", byteArrayOf()).await()
            }
        } catch (_: Exception) {
            // Tile has no UI feedback mechanism for errors
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
