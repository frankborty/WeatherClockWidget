package it.frankborty.weatherclockwidget.widget

import android.content.Context
import it.frankborty.weatherclockwidget.data.RefreshOutcome
import it.frankborty.weatherclockwidget.data.WeatherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Data refresh plus widget rendering: the single entry point used by the
 * settings screen and by the AppWidgetProvider.
 */
object WeatherWidgetRefresher {

    suspend fun refresh(context: Context): RefreshOutcome = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val outcome = WeatherRepository(appContext).refresh()
        WidgetUpdater.updateAll(appContext)
        outcome
    }
}
