package com.example.alakey.wear

import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.tiles.ActionBuilders
import androidx.wear.tiles.ColorBuilders
import androidx.wear.tiles.DimensionBuilders
import androidx.wear.tiles.LayoutElementBuilders
import androidx.wear.tiles.ModifiersBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TimelineBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.example.alakey.tile.TileContract
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Now-playing tile: show + episode title + play/pause state + position.
 * Pure projection of the last frame the phone pushed — tap sends "toggle"
 * back through the phone's existing media session.
 */
class NowPlayingTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> {
        TileStateStore.restore(this)
        return Futures.immediateFuture(
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RES_VERSION)
                .setTimeline(
                    TimelineBuilders.Timeline.Builder()
                        .addTimelineEntry(
                            TimelineBuilders.TimelineEntry.Builder()
                                .setLayout(
                                    LayoutElementBuilders.Layout.Builder()
                                        .setRoot(layout(TileStateStore.state.value))
                                        .build(),
                                )
                                .build(),
                        )
                        .build(),
                )
                .build(),
        )
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> = Futures.immediateFuture(
        ResourceBuilders.Resources.Builder().setVersion(RES_VERSION).build(),
    )

    private fun layout(np: TileContract.NowPlaying): LayoutElementBuilders.LayoutElement {
        val click = ModifiersBuilders.Clickable.Builder()
            .setId("toggle")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setClassName(ToggleActivity::class.java.name)
                            .setPackageName(packageName)
                            .build(),
                    )
                    .build(),
            )
            .build()

        val column = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)

        if (np.timestampMs == 0L) {
            column.addContent(text("Alakey", 16f, COL_BRIGHT, 1))
            column.addContent(spacer(6f))
            column.addContent(text("Play something on your phone", 12f, COL_DIM, 3))
        } else {
            column.addContent(text(np.show, 10f, COL_DIM, 1))
            column.addContent(spacer(4f))
            column.addContent(text(np.title, 15f, COL_BRIGHT, 5))
            column.addContent(spacer(8f))
            column.addContent(text(glyph(np), 20f, COL_ACCENT, 1))
            column.addContent(spacer(4f))
            column.addContent(text("${mmss(np.positionMs)} / ${mmss(np.durationMs)}", 10f, COL_DIM, 1))
        }

        return LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(click)
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setAll(DimensionBuilders.dp(14f))
                            .build(),
                    )
                    .build(),
            )
            .addContent(column.build())
            .build()
    }

    private fun glyph(np: TileContract.NowPlaying): String = when {
        np.isBuffering -> "◌"
        np.isPlaying -> "❚❚"
        else -> "▶"
    }

    private fun text(value: String, sizeSp: Float, color: Int, maxLines: Int) =
        LayoutElementBuilders.Text.Builder()
            .setText(value)
            .setMaxLines(maxLines)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(DimensionBuilders.sp(sizeSp))
                    .setColor(ColorBuilders.ColorProp.Builder().setArgb(color).build())
                    .build(),
            )
            .build()

    private fun spacer(heightDp: Float) =
        LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(heightDp)).build()

    private fun mmss(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(totalSec / 60, totalSec % 60)
    }

    private companion object {
        const val RES_VERSION = "v1"
        const val COL_BRIGHT = 0xFFEDEDED.toInt()
        const val COL_DIM = 0xFF9E9E9E.toInt()
        const val COL_ACCENT = 0xFFFFB74D.toInt()
    }
}
