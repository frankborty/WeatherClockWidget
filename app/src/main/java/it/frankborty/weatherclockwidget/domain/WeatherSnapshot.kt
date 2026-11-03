package it.frankborty.weatherclockwidget.domain

/**
 * Weather data fetched from Open-Meteo and cached locally.
 *
 * [locationLabel] is the label of the location the data belongs to, so cached
 * data keeps a coherent label even if the configured location changes and the
 * next refresh fails.
 *
 * The optional daily/hourly fields keep old cached snapshots readable: they are
 * simply absent until the next successful refresh.
 */
data class WeatherSnapshot(
    val temperatureCelsius: Double,
    val weatherCode: Int,
    val isDay: Boolean,
    val locationLabel: String,
    val observedAt: String,
    val timezone: String?,
    val fetchedAtEpochMillis: Long,
    val dailyMinCelsius: Double? = null,
    val dailyMaxCelsius: Double? = null,
    val hourly: List<HourlyForecast> = emptyList(),
    val daily: List<DailyForecast> = emptyList()
)
