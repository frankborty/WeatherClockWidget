package it.frankborty.weatherclockwidget.data

enum class TimeFormat(val storageValue: String) {
    HOUR_12("12"),
    HOUR_24("24");

    companion object {
        fun fromStorage(value: String?): TimeFormat =
            entries.firstOrNull { it.storageValue == value } ?: HOUR_24
    }
}
