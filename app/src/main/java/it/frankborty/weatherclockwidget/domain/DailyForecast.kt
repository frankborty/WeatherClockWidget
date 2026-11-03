package it.frankborty.weatherclockwidget.domain

/**
 * One daily forecast point. [date] is the local date returned by Open-Meteo
 * (for example "2026-11-03").
 */
data class DailyForecast(
    val date: String,
    val temperatureMinCelsius: Double,
    val temperatureMaxCelsius: Double,
    val weatherCode: Int
)
