package it.frankborty.weatherclockwidget.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Builds the explicit PendingIntent used by the widget clock.
 *
 * The trampoline activity can try multiple clock-app intents so the click works
 * on OEM devices whose Clock application does not handle ACTION_SHOW_ALARMS.
 */
object ClockLauncher {

    private const val REQUEST_CODE = 1001

    fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ClockLaunchActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
