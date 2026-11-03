package it.frankborty.weatherclockwidget.widget

import it.frankborty.weatherclockwidget.data.ForecastMode
import it.frankborty.weatherclockwidget.data.TimeFormat
import it.frankborty.weatherclockwidget.domain.WeatherSnapshot

/**
 * Everything the widget needs to render one update.
 */
data class WidgetState(
    val snapshot: WeatherSnapshot?,
    val configuredLocationName: String?,
    val lastSuccessEpochMillis: Long?,
    val lastError: String?,
    val backgroundColor: Int,
    val backgroundOpacity: Int,
    val timeFormat: TimeFormat,
    val forecastMode: ForecastMode
)
