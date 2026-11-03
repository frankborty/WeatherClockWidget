package it.frankborty.weatherclockwidget.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import it.frankborty.weatherclockwidget.MainActivity

/**
 * Opens the app settings when the user taps any part of the widget that is not
 * the clock. The clock keeps its own intent to launch the Clock app.
 */
object SettingsLauncher {

    private const val REQUEST_CODE = 1002

    fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        return PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
