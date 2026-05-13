package com.lockphone.watch

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.wrap
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.*
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ModifiersBuilders.Background
import androidx.wear.protolayout.ModifiersBuilders.Modifiers
import androidx.wear.protolayout.ModifiersBuilders.Corner
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.*
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.tasks.await

class LockTileService : TileService(), MessageClient.OnMessageReceivedListener {

    companion object {
        private const val PREFS = "tile_prefs"
        private const val KEY_AUTO_LOCK = "auto_lock_enabled"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        Wearable.getMessageClient(this).addListener(this)
    }

    override fun onDestroy() {
        Wearable.getMessageClient(this).removeListener(this)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            "/lock-confirmed" -> {
                vibrate()
            }
            "/auto-lock-state" -> {
                val enabled = messageEvent.data.isNotEmpty() && messageEvent.data[0] == 1.toByte()
                getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_AUTO_LOCK, enabled).apply()
                getUpdater(this).requestUpdate(LockTileService::class.java)
            }
        }
    }

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        serviceScope.future {
            val lastClickableId = requestParams.currentState.lastClickableId
            when (lastClickableId) {
                "lock_button" -> sendLockCommand()
                "toggle_auto_lock" -> sendToggleAutoLock()
            }

            val isConnected = checkConnection()
            val autoLockEnabled = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_AUTO_LOCK, true)

            TileBuilders.Tile.Builder()
                .setResourcesVersion("1")
                .setFreshnessIntervalMillis(10_000)
                .setTileTimeline(
                    TimelineBuilders.Timeline.Builder()
                        .addTimelineEntry(
                            TimelineBuilders.TimelineEntry.Builder()
                                .setLayout(
                                    Layout.Builder()
                                        .setRoot(buildLayout(isConnected, autoLockEnabled))
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

    private fun buildLayout(isConnected: Boolean, autoLockEnabled: Boolean): LayoutElement {
        val statusText = if (isConnected) "Connected" else "Disconnected"
        val statusColor = if (isConnected) 0xFF4CAF50.toInt() else 0xFFFF5252.toInt()

        // Galaxy Watch 4 Classic: 450x450px, ~227dp diameter, ~192dp usable
        return Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
            .addContent(
                // Center: Lock button (absolute center of screen)
                buildLockButton()
            )
            .addContent(
                // Overlay column for top/bottom elements
                Column.Builder()
                    .setWidth(expand())
                    .setHeight(expand())
                    .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                    .addContent(
                        // Top: Auto-lock toggle
                        Spacer.Builder().setHeight(dp(30f)).build()
                    )
                    .addContent(
                        buildToggleRow(autoLockEnabled)
                    )
                    .addContent(
                        // Flexible spacer pushes status to bottom
                        Spacer.Builder()
                            .setHeight(expand())
                            .build()
                    )
                    .addContent(
                        // Bottom: Connection status
                        Text.Builder(this, statusText)
                            .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                            .setColor(argb(statusColor))
                            .build()
                    )
                    .addContent(
                        Spacer.Builder().setHeight(dp(24f)).build()
                    )
                    .build()
            )
            .build()
    }

    private fun buildLockButton(): LayoutElement {
        // Glossy grey button, large enough to fit "LOCK" comfortably
        return Box.Builder()
            .setWidth(dp(72f))
            .setHeight(dp(72f))
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
            .setModifiers(
                Modifiers.Builder()
                    .setClickable(
                        ModifiersBuilders.Clickable.Builder()
                            .setId("lock_button")
                            .setOnClick(ActionBuilders.LoadAction.Builder().build())
                            .build()
                    )
                    .setBackground(
                        Background.Builder()
                            .setColor(argb(0xFF5C5C5C.toInt()))
                            .setCorner(
                                Corner.Builder()
                                    .setRadius(dp(36f))
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .addContent(
                Text.Builder(this, "LOCK")
                    .setTypography(Typography.TYPOGRAPHY_TITLE2)
                    .setColor(argb(0xFFFFFFFF.toInt()))
                    .build()
            )
            .build()
    }

    private fun buildToggleRow(autoLockEnabled: Boolean): LayoutElement {
        val toggleBgColor = if (autoLockEnabled) 0xFF4CAF50.toInt() else 0xFF424242.toInt()
        val toggleText = if (autoLockEnabled) "ON" else "OFF"
        val knobAlign = if (autoLockEnabled) HORIZONTAL_ALIGN_END else HORIZONTAL_ALIGN_START

        return Row.Builder()
            .setWidth(wrap())
            .setHeight(wrap())
            .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
            .addContent(
                Text.Builder(this, "Auto-Lock")
                    .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                    .setColor(argb(0xFFCCCCCC.toInt()))
                    .build()
            )
            .addContent(
                Spacer.Builder().setWidth(dp(8f)).build()
            )
            .addContent(
                // Toggle switch visual
                Box.Builder()
                    .setWidth(dp(36f))
                    .setHeight(dp(18f))
                    .setHorizontalAlignment(knobAlign)
                    .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
                    .setModifiers(
                        Modifiers.Builder()
                            .setClickable(
                                ModifiersBuilders.Clickable.Builder()
                                    .setId("toggle_auto_lock")
                                    .setOnClick(ActionBuilders.LoadAction.Builder().build())
                                    .build()
                            )
                            .setBackground(
                                Background.Builder()
                                    .setColor(argb(toggleBgColor))
                                    .setCorner(
                                        Corner.Builder()
                                            .setRadius(dp(9f))
                                            .build()
                                    )
                                    .build()
                            )
                            .build()
                    )
                    .addContent(
                        // Knob
                        Box.Builder()
                            .setWidth(dp(14f))
                            .setHeight(dp(14f))
                            .setModifiers(
                                Modifiers.Builder()
                                    .setBackground(
                                        Background.Builder()
                                            .setColor(argb(0xFFFFFFFF.toInt()))
                                            .setCorner(
                                                Corner.Builder()
                                                    .setRadius(dp(7f))
                                                    .build()
                                            )
                                            .build()
                                    )
                                    .build()
                            )
                            .build()
                    )
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
        } catch (_: Exception) {}
    }

    private suspend fun sendToggleAutoLock() {
        try {
            val nodes = Wearable.getNodeClient(this).connectedNodes.await()
            val messageClient = Wearable.getMessageClient(this)
            for (node in nodes) {
                messageClient.sendMessage(node.id, "/toggle-auto-lock", byteArrayOf()).await()
            }
        } catch (_: Exception) {}
    }

    private suspend fun checkConnection(): Boolean {
        return try {
            val nodes = Wearable.getNodeClient(this).connectedNodes.await()
            nodes.isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    private fun vibrate() {
        val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        val vibrator = vibratorManager.defaultVibrator
        vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
