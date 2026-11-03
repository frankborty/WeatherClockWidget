package it.frankborty.weatherclockwidget.domain

/**
 * One hourly forecast point. [time] is the local time returned by Open-Meteo
 * (for example "2026-11-03T16:00").
 */
data class HourlyForecast(
    val time: String,
    val temperatureCelsius: Double,
    val weatherCode: Int,
    val isDay: Boolean,
    val precipitationProbability: Int?
)
