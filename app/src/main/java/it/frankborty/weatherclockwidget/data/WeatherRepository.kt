package it.frankborty.weatherclockwidget.data

import android.content.Context
import android.util.Log
import it.frankborty.weatherclockwidget.R
import it.frankborty.weatherclockwidget.domain.WeatherSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

sealed interface RefreshOutcome {
    data class Success(val snapshot: WeatherSnapshot) : RefreshOutcome
    data class Failure(
        val message: String,
        val retryable: Boolean
    ) : RefreshOutcome
}

/**
 * Coordinates the weather client and the local cache.
 *
 * It never touches the widget: callers decide when to render. On failure the
 * previous snapshot stays in SharedPreferences and the error is recorded.
 */
class WeatherRepository(context: Context) {

    private val appContext = context.applicationContext
    private val preferences = WeatherPreferences(appContext)
    private val client = OpenMeteoClient()

    suspend fun refresh(): RefreshOutcome = withContext(Dispatchers.IO) {
        val location = preferences.location

        try {
            val forecast = client.fetchForecast(location.latitude, location.longitude)
            val snapshot = WeatherSnapshot(
                temperatureCelsius = forecast.temperatureCelsius,
                weatherCode = forecast.weatherCode,
                isDay = forecast.isDay,
                locationLabel = location.name,
                observedAt = forecast.observedAt,
                timezone = forecast.timezone,
                fetchedAtEpochMillis = System.currentTimeMillis(),
                dailyMinCelsius = forecast.dailyMinCelsius,
                dailyMaxCelsius = forecast.dailyMaxCelsius,
                hourly = forecast.hourly,
                daily = forecast.daily
            )

            preferences.saveWeather(snapshot)
            RefreshOutcome.Success(snapshot)
        } catch (e: CancellationException) {
            throw e
        } catch (e: InvalidWeatherDataException) {
            fail(appContext.getString(R.string.weather_error_invalid_data), e, retryable = false)
        } catch (e: OpenMeteoHttpException) {
            val retryable = e.statusCode in RETRYABLE_HTTP_STATUSES ||
                e.statusCode in 500..599
            val message = if (retryable) {
                appContext.getString(R.string.weather_error_server)
            } else {
                appContext.getString(R.string.weather_error_location)
            }
            fail(message, e, retryable)
        } catch (e: IOException) {
            fail(appContext.getString(R.string.weather_error_network), e, retryable = true)
        } catch (e: Exception) {
            fail(appContext.getString(R.string.weather_error_generic), e, retryable = false)
        }
    }

    private fun fail(
        message: String,
        throwable: Throwable,
        retryable: Boolean
    ): RefreshOutcome.Failure {
        Log.w(TAG, message, throwable)
        preferences.recordFailure(message)
        return RefreshOutcome.Failure(message = message, retryable = retryable)
    }

    private companion object {
        const val TAG = "WeatherRepository"
        val RETRYABLE_HTTP_STATUSES = setOf(408, 425, 429)
    }
}
