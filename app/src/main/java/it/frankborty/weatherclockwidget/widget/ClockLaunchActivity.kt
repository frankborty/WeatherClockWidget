package it.frankborty.weatherclockwidget.widget

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.AlarmClock
import android.widget.Toast
import it.frankborty.weatherclockwidget.R

/**
 * Small trampoline used by the widget clock.
 *
 * The public SHOW_ALARMS action is not handled by every vendor clock app, so we
 * fall back to the clock packages commonly installed on Samsung, Google, AOSP
 * and other devices.
 */
@SuppressLint("CustomSplashScreen")
class ClockLaunchActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (launchClock()) {
            finish()
            return
        }

        Toast.makeText(this, R.string.clock_app_not_found, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun launchClock(): Boolean {
        val candidates = buildList {
            add(Intent(AlarmClock.ACTION_SHOW_ALARMS))
            CLOCK_PACKAGES.forEach { packageName ->
                packageManager.getLaunchIntentForPackage(packageName)?.let(::add)
            }
        }

        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(packageManager) == null) continue

            try {
                startActivity(intent)
                return true
            } catch (e: ActivityNotFoundException) {
                // Try the next candidate.
            } catch (e: SecurityException) {
                // Try the next candidate.
            }
        }

        return false
    }

    private companion object {
        val CLOCK_PACKAGES = listOf(
            "com.sec.android.app.clockpackage",
            "com.google.android.deskclock",
            "com.android.deskclock",
            "com.miui.clock",
            "com.coloros.alarmclock",
            "com.oneplus.clock",
            "com.transsion.clock",
            "com.android.alarmclock"
        )
    }
}
