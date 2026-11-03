package it.frankborty.weatherclockwidget.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import it.frankborty.weatherclockwidget.widget.WidgetUpdater
import java.util.concurrent.TimeUnit

/**
 * Owns the unique periodic weather refresh.
 *
 * The work is unique so repeated app starts or widget updates cannot create
 * duplicate periodic jobs. WorkManager persists the schedule across reboots.
 */
object WeatherUpdateScheduler {

    private const val UNIQUE_WORK_NAME = "weather-refresh-periodic"
    private const val WORK_TAG = "weather-refresh"
    private const val INTERVAL_MINUTES = 30L
    private const val BACKOFF_MINUTES = 10L

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<WeatherUpdateWorker>(
            INTERVAL_MINUTES,
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                BACKOFF_MINUTES,
                TimeUnit.MINUTES
            )
            .addTag(WORK_TAG)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    /**
     * Keeps the periodic work only while at least one widget is installed.
     */
    fun syncWithWidgets(context: Context) {
        if (WidgetUpdater.hasInstalledWidgets(context)) {
            schedule(context)
        } else {
            cancel(context)
        }
    }
}
