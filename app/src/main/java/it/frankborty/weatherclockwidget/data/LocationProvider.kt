package it.frankborty.weatherclockwidget.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Foreground-only location access.
 *
 * No background updates are requested: a position is obtained when the user
 * opens the settings screen or explicitly taps the update button.
 */
class LocationProvider(context: Context) {

    private val appContext = context.applicationContext

    fun hasLocationPermission(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fineGranted || coarseGranted
    }

    private fun hasFineLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun currentLocation(): Location? = withContext(Dispatchers.IO) {
        if (!hasLocationPermission()) return@withContext null

        val manager = appContext.getSystemService(LocationManager::class.java)
            ?: return@withContext null

        val lastKnown = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
            .filter { provider -> providerEnabled(manager, provider) }
            .mapNotNull { provider -> lastKnownLocation(manager, provider) }
            .maxByOrNull { it.time }

        if (lastKnown != null &&
            System.currentTimeMillis() - lastKnown.time < LAST_LOCATION_MAX_AGE_MILLIS
        ) {
            return@withContext lastKnown
        }

        val preferredProviders = if (hasFineLocationPermission()) {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        } else {
            listOf(LocationManager.NETWORK_PROVIDER)
        }

        for (provider in preferredProviders) {
            if (!providerEnabled(manager, provider)) continue
            val current = requestCurrentLocation(manager, provider)
            if (current != null) return@withContext current
        }

        lastKnown
    }

    suspend fun reverseGeocode(latitude: Double, longitude: Double): String? =
        withTimeoutOrNull(GEOCODE_TIMEOUT_MILLIS) {
            withContext(Dispatchers.IO) {
                if (!Geocoder.isPresent()) return@withContext null

                val geocoder = Geocoder(appContext, Locale.getDefault())
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocation(
                        latitude,
                        longitude,
                        1,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<android.location.Address>) {
                                if (continuation.isActive) {
                                    continuation.resume(addresses.firstOrNull().toDisplayName())
                                }
                            }

                            override fun onError(errorMessage: String?) {
                                if (continuation.isActive) {
                                    continuation.resume(null)
                                }
                            }
                        }
                    )
                }
            }
        }

    private fun providerEnabled(manager: LocationManager, provider: String): Boolean =
        runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)

    private fun android.location.Address?.toDisplayName(): String? =
        this?.let { address ->
            listOfNotNull(
                address.locality,
                address.subAdminArea,
                address.adminArea,
                address.countryName
            ).firstOrNull { it.isNotBlank() }
        }

    @SuppressLint("MissingPermission")
    private fun lastKnownLocation(manager: LocationManager, provider: String): Location? =
        runCatching { manager.getLastKnownLocation(provider) }.getOrNull()

    @SuppressLint("MissingPermission")
    private suspend fun requestCurrentLocation(
        manager: LocationManager,
        provider: String
    ): Location? = withTimeoutOrNull(LOCATION_TIMEOUT_MILLIS) {
        suspendCancellableCoroutine { continuation ->
            val cancellationSignal = CancellationSignal()

            try {
                manager.getCurrentLocation(
                    provider,
                    cancellationSignal,
                    ContextCompat.getMainExecutor(appContext)
                ) { location ->
                    if (continuation.isActive) {
                        continuation.resume(location)
                    }
                }
            } catch (e: SecurityException) {
                if (continuation.isActive) continuation.resume(null)
            } catch (e: IllegalArgumentException) {
                if (continuation.isActive) continuation.resume(null)
            }

            continuation.invokeOnCancellation { cancellationSignal.cancel() }
        }
    }

    private companion object {
        const val LOCATION_TIMEOUT_MILLIS = 5_000L
        const val GEOCODE_TIMEOUT_MILLIS = 8_000L
        const val LAST_LOCATION_MAX_AGE_MILLIS = 10 * 60 * 1_000L
    }
}
