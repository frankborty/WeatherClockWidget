package it.frankborty.weatherclockwidget.data

import android.content.Context
import androidx.core.content.edit
import it.frankborty.weatherclockwidget.R
import it.frankborty.weatherclockwidget.domain.DailyForecast
import it.frankborty.weatherclockwidget.domain.HourlyForecast
import it.frankborty.weatherclockwidget.domain.WeatherSnapshot
import org.json.JSONArray
import org.json.JSONObject

/**
 * SharedPreferences-backed persistence for the two location modes and the last
 * successfully fetched weather snapshot.
 *
 * Fixed and GPS locations are stored separately so switching mode does not
 * destroy the manually selected city. A failed refresh never clears the
 * previous snapshot: it only records the error so the widget can show stale
 * data plus a status indication.
 */
class WeatherPreferences(context: Context) {

    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var mode: LocationMode
        get() = LocationMode.fromStorage(preferences.getString(KEY_MODE, null))
        set(value) {
            preferences.edit {
                putString(KEY_MODE, value.storageValue)
                remove(KEY_LAST_ERROR)
            }
        }

    var widgetBackgroundColor: Int
        get() = preferences.getInt(KEY_BACKGROUND_COLOR, WidgetAppearance.DEFAULT_BACKGROUND_COLOR)
        set(value) {
            preferences.edit { putInt(KEY_BACKGROUND_COLOR, value) }
        }

    var widgetBackgroundOpacity: Int
        get() = preferences
            .getInt(KEY_BACKGROUND_OPACITY, WidgetAppearance.DEFAULT_OPACITY)
            .coerceIn(0, 100)
        set(value) {
            preferences.edit { putInt(KEY_BACKGROUND_OPACITY, value.coerceIn(0, 100)) }
        }

    var timeFormat: TimeFormat
        get() = TimeFormat.fromStorage(preferences.getString(KEY_TIME_FORMAT, null))
        set(value) {
            preferences.edit { putString(KEY_TIME_FORMAT, value.storageValue) }
        }

    var forecastMode: ForecastMode
        get() = ForecastMode.fromStorage(preferences.getString(KEY_FORECAST_MODE, null))
        set(value) {
            preferences.edit { putString(KEY_FORECAST_MODE, value.storageValue) }
        }

    val fixedLocation: SavedLocation?
        get() = readLocation(
            nameKey = KEY_FIXED_NAME,
            latitudeKey = KEY_FIXED_LATITUDE,
            longitudeKey = KEY_FIXED_LONGITUDE,
            timezoneKey = KEY_FIXED_TIMEZONE
        )

    val gpsLocation: SavedLocation?
        get() = readLocation(
            nameKey = KEY_GPS_NAME,
            latitudeKey = KEY_GPS_LATITUDE,
            longitudeKey = KEY_GPS_LONGITUDE,
            timezoneKey = KEY_GPS_TIMEZONE
        )

    /**
     * Location used for the next weather request.
     *
     * GPS mode falls back to the fixed location (or the built-in default) until
     * a GPS position has been saved.
     */
    val location: SavedLocation
        get() = when (mode) {
            LocationMode.FIXED -> fixedLocation ?: defaultLocation()
            LocationMode.GPS -> gpsLocation ?: fixedLocation ?: defaultLocation()
        }

    val weather: WeatherSnapshot?
        get() {
            val raw = preferences.getString(KEY_WEATHER, null) ?: return null
            return runCatching { raw.toSnapshot() }.getOrNull()
        }

    val lastSuccessEpochMillis: Long?
        get() = preferences.getLong(KEY_LAST_SUCCESS, 0L).takeIf { it > 0L }

    val lastError: String?
        get() = preferences.getString(KEY_LAST_ERROR, null)

    fun saveFixedLocation(location: SavedLocation) {
        writeLocation(
            nameKey = KEY_FIXED_NAME,
            latitudeKey = KEY_FIXED_LATITUDE,
            longitudeKey = KEY_FIXED_LONGITUDE,
            timezoneKey = KEY_FIXED_TIMEZONE,
            location = location
        )
    }

    fun saveGpsLocation(location: SavedLocation) {
        writeLocation(
            nameKey = KEY_GPS_NAME,
            latitudeKey = KEY_GPS_LATITUDE,
            longitudeKey = KEY_GPS_LONGITUDE,
            timezoneKey = KEY_GPS_TIMEZONE,
            location = location
        )
    }

    fun saveWeather(snapshot: WeatherSnapshot) {
        preferences.edit {
            putString(KEY_WEATHER, snapshot.toJson().toString())
            putLong(KEY_LAST_SUCCESS, snapshot.fetchedAtEpochMillis)
            remove(KEY_LAST_ERROR)
        }
    }

    fun recordFailure(message: String) {
        preferences.edit {
            putString(KEY_LAST_ERROR, message)
        }
    }

    private fun readLocation(
        nameKey: String,
        latitudeKey: String,
        longitudeKey: String,
        timezoneKey: String
    ): SavedLocation? {
        val name = preferences.getString(nameKey, null)?.takeIf { it.isNotBlank() } ?: return null
        val latitude = preferences.getString(latitudeKey, null)?.toDoubleOrNull() ?: return null
        val longitude = preferences.getString(longitudeKey, null)?.toDoubleOrNull() ?: return null

        return SavedLocation(
            name = name,
            latitude = latitude,
            longitude = longitude,
            timezone = preferences.getString(timezoneKey, null)
        )
    }

    private fun writeLocation(
        nameKey: String,
        latitudeKey: String,
        longitudeKey: String,
        timezoneKey: String,
        location: SavedLocation
    ) {
        preferences.edit {
            putString(nameKey, location.name)
            putString(latitudeKey, location.latitude.toString())
            putString(longitudeKey, location.longitude.toString())
            if (location.timezone.isNullOrBlank()) {
                remove(timezoneKey)
            } else {
                putString(timezoneKey, location.timezone)
            }
            remove(KEY_LAST_ERROR)
        }
    }

    private fun defaultLocation(): SavedLocation = SavedLocation(
        name = appContext.getString(R.string.default_location_name),
        latitude = DEFAULT_LATITUDE,
        longitude = DEFAULT_LONGITUDE,
        timezone = DEFAULT_TIMEZONE
    )

    private fun WeatherSnapshot.toJson(): JSONObject = JSONObject().apply {
        put(JSON_TEMPERATURE, temperatureCelsius)
        put(JSON_WEATHER_CODE, weatherCode)
        put(JSON_IS_DAY, isDay)
        put(JSON_LOCATION, locationLabel)
        put(JSON_OBSERVED_AT, observedAt)
        put(JSON_TIMEZONE, timezone ?: JSONObject.NULL)
        put(JSON_FETCHED_AT, fetchedAtEpochMillis)
        put(JSON_DAILY_MIN, dailyMinCelsius ?: JSONObject.NULL)
        put(JSON_DAILY_MAX, dailyMaxCelsius ?: JSONObject.NULL)
        put(
            JSON_HOURLY,
            JSONArray().apply {
                hourly.forEach { hour -> put(hour.toJson()) }
            }
        )
        put(
            JSON_DAILY,
            JSONArray().apply {
                daily.forEach { day -> put(day.toJson()) }
            }
        )
    }

    private fun DailyForecast.toJson(): JSONObject = JSONObject().apply {
        put(JSON_DAY_DATE, date)
        put(JSON_DAY_MIN, temperatureMinCelsius)
        put(JSON_DAY_MAX, temperatureMaxCelsius)
        put(JSON_DAY_WEATHER_CODE, weatherCode)
    }

    private fun HourlyForecast.toJson(): JSONObject = JSONObject().apply {
        put(JSON_HOUR_TIME, time)
        put(JSON_HOUR_TEMPERATURE, temperatureCelsius)
        put(JSON_HOUR_WEATHER_CODE, weatherCode)
        put(JSON_HOUR_IS_DAY, isDay)
        put(JSON_HOUR_PRECIPITATION, precipitationProbability ?: JSONObject.NULL)
    }

    private fun String.toSnapshot(): WeatherSnapshot {
        val json = JSONObject(this)
        return WeatherSnapshot(
            temperatureCelsius = json.getDouble(JSON_TEMPERATURE),
            weatherCode = json.getInt(JSON_WEATHER_CODE),
            isDay = json.getBoolean(JSON_IS_DAY),
            locationLabel = json.getString(JSON_LOCATION),
            observedAt = json.optString(JSON_OBSERVED_AT),
            timezone = if (json.isNull(JSON_TIMEZONE)) {
                null
            } else {
                json.optString(JSON_TIMEZONE).takeIf { it.isNotBlank() }
            },
            fetchedAtEpochMillis = json.getLong(JSON_FETCHED_AT),
            dailyMinCelsius = json.optNullableDouble(JSON_DAILY_MIN),
            dailyMaxCelsius = json.optNullableDouble(JSON_DAILY_MAX),
            hourly = json.optJSONArray(JSON_HOURLY).toHourlyForecasts(),
            daily = json.optJSONArray(JSON_DAILY).toDailyForecasts()
        )
    }

    private fun JSONObject.optNullableDouble(key: String): Double? {
        if (isNull(key)) return null
        val value = optDouble(key, Double.NaN)
        return value.takeIf { !it.isNaN() }
    }

    private fun JSONArray?.toHourlyForecasts(): List<HourlyForecast> {
        if (this == null) return emptyList()

        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                val time = item.optString(JSON_HOUR_TIME).takeIf { it.isNotBlank() } ?: continue
                val temperature = item.optDouble(JSON_HOUR_TEMPERATURE, Double.NaN)
                val weatherCode = item.optInt(JSON_HOUR_WEATHER_CODE, -1)
                if (temperature.isNaN() || weatherCode < 0) continue

                add(
                    HourlyForecast(
                        time = time,
                        temperatureCelsius = temperature,
                        weatherCode = weatherCode,
                        isDay = item.optBoolean(JSON_HOUR_IS_DAY, true),
                        precipitationProbability = if (item.isNull(JSON_HOUR_PRECIPITATION)) {
                            null
                        } else {
                            item.optInt(JSON_HOUR_PRECIPITATION)
                        }
                    )
                )
            }
        }
    }

    private fun JSONArray?.toDailyForecasts(): List<DailyForecast> {
        if (this == null) return emptyList()

        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                val date = item.optString(JSON_DAY_DATE).takeIf { it.isNotBlank() } ?: continue
                val minimum = item.optDouble(JSON_DAY_MIN, Double.NaN)
                val maximum = item.optDouble(JSON_DAY_MAX, Double.NaN)
                val weatherCode = item.optInt(JSON_DAY_WEATHER_CODE, -1)
                if (minimum.isNaN() || maximum.isNaN() || weatherCode < 0) continue

                add(
                    DailyForecast(
                        date = date,
                        temperatureMinCelsius = minimum,
                        temperatureMaxCelsius = maximum,
                        weatherCode = weatherCode
                    )
                )
            }
        }
    }

    private companion object {
        const val PREFS_NAME = "weather_clock_preferences"

        const val KEY_MODE = "location_mode"

        const val KEY_BACKGROUND_COLOR = "widget_background_color"
        const val KEY_BACKGROUND_OPACITY = "widget_background_opacity"
        const val KEY_TIME_FORMAT = "widget_time_format"
        const val KEY_FORECAST_MODE = "widget_forecast_mode"

        const val KEY_FIXED_NAME = "fixed_location_name"
        const val KEY_FIXED_LATITUDE = "fixed_location_latitude"
        const val KEY_FIXED_LONGITUDE = "fixed_location_longitude"
        const val KEY_FIXED_TIMEZONE = "fixed_location_timezone"

        const val KEY_GPS_NAME = "gps_location_name"
        const val KEY_GPS_LATITUDE = "gps_location_latitude"
        const val KEY_GPS_LONGITUDE = "gps_location_longitude"
        const val KEY_GPS_TIMEZONE = "gps_location_timezone"

        const val KEY_WEATHER = "weather_snapshot"
        const val KEY_LAST_SUCCESS = "last_success_epoch_millis"
        const val KEY_LAST_ERROR = "last_error"

        const val JSON_TEMPERATURE = "temperature"
        const val JSON_WEATHER_CODE = "weather_code"
        const val JSON_IS_DAY = "is_day"
        const val JSON_LOCATION = "location"
        const val JSON_OBSERVED_AT = "observed_at"
        const val JSON_TIMEZONE = "timezone"
        const val JSON_FETCHED_AT = "fetched_at"
        const val JSON_DAILY_MIN = "daily_min"
        const val JSON_DAILY_MAX = "daily_max"
        const val JSON_HOURLY = "hourly"
        const val JSON_DAILY = "daily"

        const val JSON_HOUR_TIME = "time"
        const val JSON_HOUR_TEMPERATURE = "temperature"
        const val JSON_HOUR_WEATHER_CODE = "weather_code"
        const val JSON_HOUR_IS_DAY = "is_day"
        const val JSON_HOUR_PRECIPITATION = "precipitation_probability"

        const val JSON_DAY_DATE = "date"
        const val JSON_DAY_MIN = "min"
        const val JSON_DAY_MAX = "max"
        const val JSON_DAY_WEATHER_CODE = "weather_code"

        const val DEFAULT_LATITUDE = 41.9028
        const val DEFAULT_LONGITUDE = 12.4964
        const val DEFAULT_TIMEZONE = "Europe/Rome"
    }
}
