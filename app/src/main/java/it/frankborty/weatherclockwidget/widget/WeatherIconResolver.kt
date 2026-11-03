package it.frankborty.weatherclockwidget.widget

import androidx.annotation.DrawableRes
import it.frankborty.weatherclockwidget.R

object WeatherIconResolver {

    @DrawableRes
    fun iconRes(weatherCode: Int?, isDay: Boolean): Int = when (weatherCode) {
        0, 1 -> if (isDay) R.drawable.ic_weather_sun else R.drawable.ic_weather_moon
        2 -> if (isDay) {
            R.drawable.ic_weather_partly_cloudy_day
        } else {
            R.drawable.ic_weather_partly_cloudy_night
        }
        3 -> R.drawable.ic_weather_cloudy
        45, 48 -> R.drawable.ic_weather_fog
        51, 53, 55, 56, 57 -> R.drawable.ic_weather_drizzle
        61, 63, 65, 66, 67, 80, 81, 82 -> R.drawable.ic_weather_rain
        71, 73, 75, 77, 85, 86 -> R.drawable.ic_weather_snow
        95, 96, 99 -> R.drawable.ic_weather_thunder
        else -> R.drawable.ic_weather_cloudy
    }
}
