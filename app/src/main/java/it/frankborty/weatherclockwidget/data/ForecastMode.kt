package it.frankborty.weatherclockwidget.data

enum class ForecastMode(val storageValue: String) {
    HOURLY("hourly"),
    DAILY("daily");

    companion object {
        fun fromStorage(value: String?): ForecastMode =
            entries.firstOrNull { it.storageValue == value } ?: HOURLY
    }
}
