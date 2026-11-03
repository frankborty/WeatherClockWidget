package it.frankborty.weatherclockwidget.domain

import androidx.annotation.StringRes
import it.frankborty.weatherclockwidget.R

/**
 * Maps WMO weather interpretation codes returned by Open-Meteo to Italian
 * descriptions. All codes used by Open-Meteo are covered explicitly, including
 * intensity variants.
 */
object WeatherCondition {

    @StringRes
    fun descriptionRes(weatherCode: Int): Int = when (weatherCode) {
        0 -> R.string.weather_clear
        1 -> R.string.weather_mainly_clear
        2 -> R.string.weather_partly_cloudy
        3 -> R.string.weather_overcast

        45 -> R.string.weather_fog
        48 -> R.string.weather_rime_fog

        51 -> R.string.weather_drizzle_light
        53 -> R.string.weather_drizzle_moderate
        55 -> R.string.weather_drizzle_dense
        56 -> R.string.weather_freezing_drizzle_light
        57 -> R.string.weather_freezing_drizzle_dense

        61 -> R.string.weather_rain_slight
        63 -> R.string.weather_rain_moderate
        65 -> R.string.weather_rain_heavy
        66 -> R.string.weather_freezing_rain_light
        67 -> R.string.weather_freezing_rain_heavy

        71 -> R.string.weather_snow_slight
        73 -> R.string.weather_snow_moderate
        75 -> R.string.weather_snow_heavy
        77 -> R.string.weather_snow_grains

        80 -> R.string.weather_rain_showers_slight
        81 -> R.string.weather_rain_showers_moderate
        82 -> R.string.weather_rain_showers_violent
        85 -> R.string.weather_snow_showers_slight
        86 -> R.string.weather_snow_showers_heavy

        95 -> R.string.weather_thunderstorm
        96 -> R.string.weather_thunderstorm_hail_slight
        99 -> R.string.weather_thunderstorm_hail_heavy

        else -> R.string.weather_unknown
    }
}
