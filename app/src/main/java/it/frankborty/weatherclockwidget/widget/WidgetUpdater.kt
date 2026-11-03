package it.frankborty.weatherclockwidget.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import it.frankborty.weatherclockwidget.data.WeatherPreferences

/**
 * Renders the cached state into every widget instance.
 */
object WidgetUpdater {

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val widgetIds = manager.getAppWidgetIds(
            ComponentName(context, WeatherWidgetProvider::class.java)
        )

        widgetIds.forEach { appWidgetId ->
            update(context, manager, appWidgetId)
        }
    }

    fun hasInstalledWidgets(context: Context): Boolean =
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, WeatherWidgetProvider::class.java))
            .isNotEmpty()

    fun update(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        options: Bundle? = null
    ) {
        appWidgetManager.updateAppWidget(
            appWidgetId,
            WeatherWidgetRenderer.render(
                context = context,
                state = currentState(context),
                options = options ?: appWidgetManager.getAppWidgetOptions(appWidgetId)
            )
        )
    }

    fun currentState(context: Context): WidgetState {
        val preferences = WeatherPreferences(context)
        return WidgetState(
            snapshot = preferences.weather,
            configuredLocationName = preferences.location.name,
            lastSuccessEpochMillis = preferences.lastSuccessEpochMillis,
            lastError = preferences.lastError,
            backgroundColor = preferences.widgetBackgroundColor,
            backgroundOpacity = preferences.widgetBackgroundOpacity,
            timeFormat = preferences.timeFormat,
            forecastMode = preferences.forecastMode
        )
    }
}
