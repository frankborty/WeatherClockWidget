package it.frankborty.weatherclockwidget.data

/**
 * A location chosen manually or resolved from GPS.
 *
 * [timezone] is optional: it is known for geocoding results, while GPS
 * coordinates get the timezone from the forecast response instead.
 */
data class SavedLocation(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val timezone: String? = null
)
