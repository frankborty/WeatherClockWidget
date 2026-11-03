package it.frankborty.weatherclockwidget.data

enum class LocationMode(val storageValue: String) {
    FIXED("fixed"),
    GPS("gps");

    companion object {
        fun fromStorage(value: String?): LocationMode =
            entries.firstOrNull { it.storageValue == value } ?: FIXED
    }
}
