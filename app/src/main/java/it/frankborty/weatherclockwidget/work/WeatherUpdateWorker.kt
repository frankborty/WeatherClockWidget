package it.frankborty.weatherclockwidget.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import it.frankborty.weatherclockwidget.data.RefreshOutcome
import it.frankborty.weatherclockwidget.widget.WeatherWidgetRefresher
import it.frankborty.weatherclockwidget.widget.WidgetUpdater
import kotlinx.coroutines.CancellationException

/**
 * Periodic refresh entry point.
 *
 * It only uses the saved location: no GPS request is made in the background.
 * The existing repository/persistence/rendering pipeline is reused, so HTTP
 * logic is not duplicated.
 */
class WeatherUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): ListenableWorker.Result {
        return try {
            if (!WidgetUpdater.hasInstalledWidgets(applicationContext)) {
                return ListenableWorker.Result.success()
            }

            when (val outcome = WeatherWidgetRefresher.refresh(applicationContext)) {
                is RefreshOutcome.Success -> ListenableWorker.Result.success()
                is RefreshOutcome.Failure -> {
                    if (outcome.retryable) {
                        Log.w(TAG, "Transient weather failure, retry scheduled: ${outcome.message}")
                        ListenableWorker.Result.retry()
                    } else {
                        Log.w(TAG, "Permanent weather failure: ${outcome.message}")
                        ListenableWorker.Result.failure()
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected weather worker failure", e)
            ListenableWorker.Result.retry()
        }
    }

    private companion object {
        const val TAG = "WeatherUpdateWorker"
    }
}
