package it.frankborty.weatherclockwidget.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import android.util.Log
import it.frankborty.weatherclockwidget.work.WeatherUpdateScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WeatherWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WeatherUpdateScheduler.schedule(context)
    }

    override fun onDisabled(context: Context) {
        WeatherUpdateScheduler.cancel(context)
        super.onDisabled(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        if (appWidgetIds.isEmpty()) {
            WeatherUpdateScheduler.cancel(context)
            return
        }

        WeatherUpdateScheduler.schedule(context)

        appWidgetIds.forEach { appWidgetId ->
            WidgetUpdater.update(context, appWidgetManager, appWidgetId)
        }

        refreshInBackground(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        WidgetUpdater.update(context, appWidgetManager, appWidgetId, newOptions)
    }

    /**
     * One-shot refresh while the widget is being added/updated.
     *
     * WorkManager owns the periodic updates; this goAsync() refresh keeps the
     * widget immediately fresh when the system asks for an update.
     */
    private fun refreshInBackground(context: Context) {
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                WeatherWidgetRefresher.refresh(context.applicationContext)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Widget weather refresh failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "WeatherWidgetProvider"
    }
}
