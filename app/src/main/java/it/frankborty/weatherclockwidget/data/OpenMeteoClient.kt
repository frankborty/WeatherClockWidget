package it.frankborty.weatherclockwidget.data

import it.frankborty.weatherclockwidget.domain.DailyForecast
import it.frankborty.weatherclockwidget.domain.HourlyForecast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.util.Locale

data class GeocodingResult(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val admin1: String?,
    val country: String?,
    val timezone: String?
)

data class Forecast(
    val temperatureCelsius: Double,
    val weatherCode: Int,
    val isDay: Boolean,
    val observedAt: String,
    val timezone: String?,
    val dailyMinCelsius: Double?,
    val dailyMaxCelsius: Double?,
    val hourly: List<HourlyForecast>,
    val daily: List<DailyForecast>
)

class InvalidWeatherDataException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

class OpenMeteoHttpException(val statusCode: Int) : IOException("Open-Meteo HTTP $statusCode")

/**
 * Minimal Open-Meteo client based on HttpURLConnection and org.json.
 *
 * No API key is required. Both calls run on Dispatchers.IO so the main thread is
 * never blocked.
 */
class OpenMeteoClient(
    private val connectTimeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
    private val readTimeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS
) {

    suspend fun searchLocations(
        query: String,
        language: String = "en"
    ): List<GeocodingResult> =
        withContext(Dispatchers.IO) {
            val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
            val encodedLanguage = URLEncoder.encode(language, StandardCharsets.UTF_8.name())
            val url = URL(
                "$GEOCODING_BASE_URL?name=$encodedQuery&count=10" +
                    "&language=$encodedLanguage&format=json"
            )
            parseGeocoding(get(url))
        }

    suspend fun fetchForecast(latitude: Double, longitude: Double): Forecast =
        withContext(Dispatchers.IO) {
            val latitudeParam = String.format(Locale.US, "%.6f", latitude)
            val longitudeParam = String.format(Locale.US, "%.6f", longitude)
            val url = URL(
                "$FORECAST_BASE_URL" +
                    "?latitude=$latitudeParam" +
                    "&longitude=$longitudeParam" +
                    "&current=temperature_2m,weather_code,is_day" +
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                    "&hourly=temperature_2m,weather_code,is_day,precipitation_probability" +
                    "&forecast_days=7" +
                    "&temperature_unit=celsius" +
                    "&timezone=auto"
            )
            parseForecast(get(url))
        }

    private fun get(url: URL): String {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMillis
            readTimeout = readTimeoutMillis
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "WeatherClockWidget/1.0 (Android)")
            useCaches = false
        }

        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                throw OpenMeteoHttpException(status)
            }

            val body = connection.inputStream
                .bufferedReader(StandardCharsets.UTF_8)
                .use { it.readText() }

            if (body.isBlank()) {
                throw IOException("Open-Meteo empty response")
            }

            return body
        } finally {
            connection.disconnect()
        }
    }

    private fun parseGeocoding(body: String): List<GeocodingResult> {
        val results = JSONObject(body).optJSONArray("results") ?: return emptyList()

        return buildList {
            for (index in 0 until results.length()) {
                val item = results.optJSONObject(index) ?: continue
                val name = item.optString("name").takeIf { it.isNotBlank() } ?: continue
                val latitude = item.optDouble("latitude", Double.NaN)
                val longitude = item.optDouble("longitude", Double.NaN)
                if (latitude.isNaN() || longitude.isNaN()) continue

                add(
                    GeocodingResult(
                        name = name,
                        latitude = latitude,
                        longitude = longitude,
                        admin1 = item.optString("admin1").takeIf { it.isNotBlank() },
                        country = item.optString("country").takeIf { it.isNotBlank() },
                        timezone = item.optString("timezone").takeIf { it.isNotBlank() }
                    )
                )
            }
        }
    }

    private fun parseForecast(body: String): Forecast {
        val root = try {
            JSONObject(body)
        } catch (e: JSONException) {
            throw InvalidWeatherDataException("Invalid Open-Meteo response", e)
        }

        val current = root.optJSONObject("current")
            ?: throw InvalidWeatherDataException("Missing current weather in Open-Meteo response")

        val temperature = current.optDouble("temperature_2m", Double.NaN)
        val weatherCode = current.optInt("weather_code", -1)
        if (temperature.isNaN() || weatherCode < 0) {
            throw InvalidWeatherDataException("Invalid current weather in Open-Meteo response")
        }

        val daily = root.optJSONObject("daily")

        return Forecast(
            temperatureCelsius = temperature,
            weatherCode = weatherCode,
            isDay = current.optInt("is_day", 1) != 0,
            observedAt = current.optString("time"),
            timezone = root.optString("timezone").takeIf { it.isNotBlank() },
            dailyMinCelsius = daily
                ?.optJSONArray("temperature_2m_min")
                ?.optNullableDouble(0),
            dailyMaxCelsius = daily
                ?.optJSONArray("temperature_2m_max")
                ?.optNullableDouble(0),
            hourly = root.optJSONObject("hourly").toHourlyForecasts(
                currentTime = current.optString("time")
            ),
            daily = daily.toDailyForecasts()
        )
    }

    private fun JSONObject?.toDailyForecasts(): List<DailyForecast> {
        if (this == null) return emptyList()

        val dates = optJSONArray("time") ?: return emptyList()
        val minimums = optJSONArray("temperature_2m_min") ?: return emptyList()
        val maximums = optJSONArray("temperature_2m_max") ?: return emptyList()
        val weatherCodes = optJSONArray("weather_code") ?: return emptyList()
        val count = minOf(dates.length(), minimums.length(), maximums.length(), weatherCodes.length())

        return buildList {
            for (index in 0 until count) {
                val date = dates.optString(index).takeIf { it.isNotBlank() } ?: continue
                val minimum = minimums.optNullableDouble(index) ?: continue
                val maximum = maximums.optNullableDouble(index) ?: continue
                val weatherCode = weatherCodes.optInt(index, -1)
                if (weatherCode < 0) continue

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

    private fun JSONObject?.toHourlyForecasts(currentTime: String): List<HourlyForecast> {
        if (this == null) return emptyList()

        val times = optJSONArray("time") ?: return emptyList()
        val temperatures = optJSONArray("temperature_2m") ?: return emptyList()
        val weatherCodes = optJSONArray("weather_code") ?: return emptyList()
        val isDayValues = optJSONArray("is_day")
        val precipitationValues = optJSONArray("precipitation_probability")
        val currentLocalTime = parseLocalDateTime(currentTime)

        return buildList {
            for (index in 0 until times.length()) {
                val time = times.optString(index).takeIf { it.isNotBlank() } ?: continue
                val localTime = parseLocalDateTime(time)
                if (currentLocalTime != null &&
                    localTime != null &&
                    !localTime.isAfter(currentLocalTime)
                ) {
                    continue
                }

                val temperature = temperatures.optNullableDouble(index) ?: continue
                val weatherCode = weatherCodes.optInt(index, -1)
                if (weatherCode < 0) continue

                val isDay = isDayValues
                    ?.takeUnless { it.isNull(index) }
                    ?.optInt(index, 1)
                    ?.let { it != 0 }
                    ?: true
                val precipitation = precipitationValues
                    ?.takeUnless { it.isNull(index) }
                    ?.optInt(index)

                add(
                    HourlyForecast(
                        time = time,
                        temperatureCelsius = temperature,
                        weatherCode = weatherCode,
                        isDay = isDay,
                        precipitationProbability = precipitation
                    )
                )

                if (size >= MAX_HOURLY_FORECASTS) break
            }
        }
    }

    private fun JSONArray?.optNullableDouble(index: Int): Double? {
        if (this == null || index < 0 || index >= length() || isNull(index)) return null
        val value = optDouble(index, Double.NaN)
        return value.takeIf { !it.isNaN() }
    }

    private fun parseLocalDateTime(value: String): LocalDateTime? =
        runCatching { LocalDateTime.parse(value) }.getOrNull()

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 8_000
        const val MAX_HOURLY_FORECASTS = 12
        const val GEOCODING_BASE_URL = "https://geocoding-api.open-meteo.com/v1/search"
        const val FORECAST_BASE_URL = "https://api.open-meteo.com/v1/forecast"
    }
}
