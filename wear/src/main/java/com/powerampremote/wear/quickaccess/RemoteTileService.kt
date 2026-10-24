package com.powerampremote.wear.quickaccess

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Chip
import androidx.wear.protolayout.material.ChipColors
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import androidx.concurrent.futures.CallbackToFutureAdapter
import com.google.common.util.concurrent.ListenableFuture
import com.powerampremote.wear.MainActivity
import com.powerampremote.wear.R

/** O sistema desenha este layout ProtoLayout; o Tile não executa Compose nem comandos de áudio. */
class RemoteTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        // packageName é o applicationId real, diferente do namespace Kotlin .wear.
        val openPlayer = ModifiersBuilders.Clickable.Builder()
            .setId("open_player")
            .setOnClick(ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName(MainActivity::class.java.name).build())
                .build())
            .build()
        val device = requestParams.deviceConfiguration
        // Margens proporcionais mantêm o conteúdo na região central da tela circular.
        val width = device.screenWidthDp * 0.72f
        val button = Chip.Builder(this, openPlayer, device)
            .setWidth(dp(width))
            .setPrimaryLabelContent(getString(R.string.quick_access_open))
            .setContentDescription(getString(R.string.quick_access_description))
            .setChipColors(ChipColors(0xFFD0BCFF.toInt(), 0xFF21182E.toInt()))
            .build()
        val column = LayoutElementBuilders.Column.Builder()
            .setWidth(dp(width))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(text(getString(R.string.quick_access_tile_title), 18f, 0xFF80CBC4.toInt(), 2))
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(8f)).build())
            .addContent(text(getString(R.string.quick_access_tile_subtitle), 12f, 0xFFFFFFFF.toInt(), 1))
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(12f)).build())
            .addContent(button)
            .build()
        val root = LayoutElementBuilders.Box.Builder().setWidth(expand()).setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(ModifiersBuilders.Modifiers.Builder()
                .setBackground(ModifiersBuilders.Background.Builder()
                    .setColor(ColorBuilders.argb(0xFF000000.toInt())).build()).build())
            .addContent(column).build()
        val timeline = TimelineBuilders.Timeline.Builder()
            .addTimelineEntry(TimelineBuilders.TimelineEntry.Builder()
                .setLayout(LayoutElementBuilders.Layout.Builder().setRoot(root).build()).build())
            .build()
        return ready(TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(timeline)
            // Zero: nenhum pedido periódico. O sistema ainda pode solicitar o layout quando necessário.
            .setFreshnessIntervalMillis(0)
            .build())
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest):
        ListenableFuture<ResourceBuilders.Resources> = ready(
            ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build())

    private fun text(value: String, size: Float, color: Int, lines: Int) =
        LayoutElementBuilders.Text.Builder().setText(value).setMaxLines(lines)
            .setMultilineAlignment(LayoutElementBuilders.TEXT_ALIGN_CENTER)
            .setFontStyle(LayoutElementBuilders.FontStyle.Builder()
                .setSize(sp(size)).setColor(ColorBuilders.argb(color)).build()).build()

    private fun <T> ready(value: T): ListenableFuture<T> = CallbackToFutureAdapter.getFuture { completer ->
        completer.set(value)
        "Static quick access"
    }

    private companion object {
        const val RESOURCES_VERSION = "quick-access-1"
    }
}
