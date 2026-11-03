package it.frankborty.weatherclockwidget.widget

import android.appwidget.AppWidgetManager
import android.os.Bundle

enum class WidgetSize {
    TINY,
    MINI,
    BAR,
    SQUARE,
    LARGE,
    XLARGE
}

/**
 * Chooses one of the four reference compositions from the available size in dp.
 *
 * The wide/tall split follows the visual target: 4x2 is large, 4x1 is a bar,
 * 2x2 is a square and 2x1 is a compact pill.
 */
object WidgetSizeResolver {

    fun fromOptions(options: Bundle?): WidgetSize {
        val minWidth = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0
        val minHeight = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) ?: 0
        val maxWidth = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH) ?: 0
        val maxHeight = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) ?: 0

        val widthDp = minWidth.takeIf { it > 0 } ?: maxWidth
        val heightDp = maxHeight.takeIf { it > 0 } ?: minHeight

        // On the reference launcher the current column span is reported as the
        // minimum width and the current row span as the maximum height. Keep
        // the opposite fields as fallbacks for hosts that report sizes
        // differently.
        return from(widthDp, heightDp)
    }

    fun from(widthDp: Int, heightDp: Int): WidgetSize = when {
        widthDp <= 0 || heightDp <= 0 -> WidgetSize.LARGE
        widthDp >= XLARGE_MIN_WIDTH_DP && heightDp >= XLARGE_MIN_HEIGHT_DP -> WidgetSize.XLARGE
        widthDp >= WIDE_MIN_WIDTH_DP && heightDp >= TALL_MIN_HEIGHT_DP -> WidgetSize.LARGE
        widthDp >= WIDE_MIN_WIDTH_DP -> WidgetSize.BAR
        heightDp >= TALL_MIN_HEIGHT_DP -> WidgetSize.SQUARE
        widthDp < TINY_MAX_SIZE_DP && heightDp < TINY_MAX_SIZE_DP -> WidgetSize.TINY
        else -> WidgetSize.MINI
    }

    private const val XLARGE_MIN_WIDTH_DP = 300
    private const val XLARGE_MIN_HEIGHT_DP = 230
    private const val WIDE_MIN_WIDTH_DP = 250
    private const val TALL_MIN_HEIGHT_DP = 130
    private const val TINY_MAX_SIZE_DP = 150
}
