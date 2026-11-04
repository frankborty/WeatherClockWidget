package it.frankborty.weatherclockwidget.widget

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import it.frankborty.weatherclockwidget.R
import it.frankborty.weatherclockwidget.data.ForecastMode
import it.frankborty.weatherclockwidget.data.TimeFormat
import it.frankborty.weatherclockwidget.domain.DailyForecast
import it.frankborty.weatherclockwidget.domain.HourlyForecast
import it.frankborty.weatherclockwidget.domain.WeatherCondition
import it.frankborty.weatherclockwidget.domain.WeatherSnapshot
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Builds the four reference compositions: large, bar, square and mini.
 *
 * The size comes from the app widget options in dp; the host triggers a new
 * render on every resize through onAppWidgetOptionsChanged. No network call is
 * ever made just to change layout.
 */
object WeatherWidgetRenderer {

    fun render(
        context: Context,
        state: WidgetState,
        options: Bundle? = null
    ): RemoteViews = createViews(
        context = context,
        state = state,
        widgetSize = WidgetSizeResolver.fromOptions(options),
        interactive = true
    )

    /**
     * Builds a non-interactive rendering used by the in-app settings preview.
     * The layout is the same one the home-screen widget uses, so colours,
     * opacity, time format and forecast mode look exactly as they will on the
     * launcher.
     */
    fun renderPreview(
        context: Context,
        state: WidgetState,
        widgetSize: WidgetSize = WidgetSize.LARGE
    ): RemoteViews = createViews(
        context = context,
        state = state,
        widgetSize = widgetSize,
        interactive = false
    )

    private fun createViews(
        context: Context,
        state: WidgetState,
        widgetSize: WidgetSize,
        interactive: Boolean
    ): RemoteViews {
        val layoutRes = when (widgetSize) {
            WidgetSize.TINY -> R.layout.widget_weather_tiny
            WidgetSize.MINI -> R.layout.widget_weather_mini
            WidgetSize.BAR -> R.layout.widget_weather_bar
            WidgetSize.SQUARE -> R.layout.widget_weather_square
            WidgetSize.LARGE -> R.layout.widget_weather_large
            WidgetSize.XLARGE -> R.layout.widget_weather_xlarge
        }

        val views = RemoteViews(context.packageName, layoutRes)
        val snapshot = state.snapshot
        val condition = conditionText(context, snapshot)

        applyAppearance(views, state)
        applyClockFormat(views, state.timeFormat)

        views.setTextViewText(R.id.widget_temperature, temperatureText(context, snapshot))
        views.setImageViewResource(
            R.id.widget_condition_icon,
            WeatherIconResolver.iconRes(snapshot?.weatherCode, snapshot?.isDay ?: true)
        )
        views.setContentDescription(R.id.widget_condition_icon, condition)
        views.setContentDescription(
            R.id.widget_clock,
            context.getString(R.string.widget_clock_content_description)
        )

        if (interactive) {
            views.setOnClickPendingIntent(R.id.widget_clock, ClockLauncher.pendingIntent(context))
            views.setOnClickPendingIntent(R.id.widget_root, SettingsLauncher.pendingIntent(context))
        }

        when (widgetSize) {
            WidgetSize.TINY, WidgetSize.MINI -> Unit

            WidgetSize.BAR -> setMinMax(context, views, snapshot)

            WidgetSize.SQUARE -> {
                views.setTextViewText(
                    R.id.widget_location,
                    locationWithDate(context, state, snapshot)
                )
                setSquareForecast(context, views, snapshot, state.forecastMode)
            }

            WidgetSize.LARGE -> {
                views.setTextViewText(R.id.widget_date, formatDate(context, snapshot))
                views.setTextViewText(
                    R.id.widget_location,
                    locationName(context, state, includeError = true)
                )
                setMinMax(context, views, snapshot)
                setForecast(
                    context = context,
                    views = views,
                    snapshot = snapshot,
                    forecastMode = state.forecastMode,
                    timeFormat = state.timeFormat,
                    sectionId = R.id.widget_hourly_section,
                    slots = LARGE_HOUR_SLOTS
                )
            }

            WidgetSize.XLARGE -> {
                views.setTextViewText(R.id.widget_date, formatDate(context, snapshot))
                views.setTextViewText(
                    R.id.widget_location,
                    locationName(context, state, includeError = true)
                )
                setMinMax(context, views, snapshot)
                setForecast(
                    context = context,
                    views = views,
                    snapshot = snapshot,
                    forecastMode = state.forecastMode,
                    timeFormat = state.timeFormat,
                    sectionId = R.id.widget_xl_hourly_section,
                    slots = XL_HOUR_SLOTS
                )
            }
        }

        return views
    }

    private fun setMinMax(
        context: Context,
        views: RemoteViews,
        snapshot: WeatherSnapshot?
    ) {
        val min = snapshot?.dailyMinCelsius
        val max = snapshot?.dailyMaxCelsius

        if (min == null || max == null) {
            views.setViewVisibility(R.id.widget_min_max, View.GONE)
            return
        }

        val minText = context.getString(R.string.widget_temperature_format, min)
        val maxText = context.getString(R.string.widget_temperature_format, max)
        views.setTextViewText(
            R.id.widget_min_max,
            context.getString(R.string.widget_min_max_format, minText, maxText)
        )
        views.setViewVisibility(R.id.widget_min_max, View.VISIBLE)
    }

    private fun setForecast(
        context: Context,
        views: RemoteViews,
        snapshot: WeatherSnapshot?,
        forecastMode: ForecastMode,
        timeFormat: TimeFormat,
        sectionId: Int,
        slots: List<LargeHourSlot>
    ) {
        val items = forecastItems(context, snapshot, forecastMode, timeFormat)
            .take(slots.size)

        views.setViewVisibility(
            sectionId,
            if (items.isEmpty()) View.GONE else View.VISIBLE
        )

        slots.forEachIndexed { index, slot ->
            val item = items.getOrNull(index)
            if (item == null) {
                views.setViewVisibility(slot.container, View.GONE)
                return@forEachIndexed
            }

            views.setViewVisibility(slot.container, View.VISIBLE)
            views.setTextViewText(slot.time, item.label)
            views.setImageViewResource(slot.icon, item.iconRes)
            views.setContentDescription(slot.icon, item.description)
            views.setTextViewText(slot.temperature, item.temperature)

            if (forecastMode == ForecastMode.DAILY) {
                views.setTextViewTextSize(
                    slot.temperature,
                    TypedValue.COMPLEX_UNIT_SP,
                    if (slots.size > LARGE_HOUR_SLOTS.size) 10.5f else 12f
                )
            }
        }
    }

    private fun setSquareForecast(
        context: Context,
        views: RemoteViews,
        snapshot: WeatherSnapshot?,
        forecastMode: ForecastMode
    ) {
        val items = forecastItems(
            context = context,
            snapshot = snapshot,
            forecastMode = forecastMode,
            timeFormat = TimeFormat.HOUR_24
        ).take(SQUARE_HOUR_ICONS.size)

        views.setViewVisibility(
            R.id.widget_square_hourly,
            if (items.isEmpty()) View.GONE else View.VISIBLE
        )

        SQUARE_HOUR_ICONS.forEachIndexed { index, iconId ->
            val item = items.getOrNull(index)
            if (item == null) {
                views.setViewVisibility(iconId, View.GONE)
                return@forEachIndexed
            }

            views.setViewVisibility(iconId, View.VISIBLE)
            views.setImageViewResource(iconId, item.iconRes)
            views.setContentDescription(iconId, item.description)
        }
    }

    private fun forecastItems(
        context: Context,
        snapshot: WeatherSnapshot?,
        forecastMode: ForecastMode,
        timeFormat: TimeFormat
    ): List<ForecastItem> {
        if (snapshot == null) return emptyList()

        return when (forecastMode) {
            ForecastMode.HOURLY -> upcomingHours(snapshot).map { hour ->
                ForecastItem(
                    label = formatHourTime(hour.time, timeFormat, appLocale(context)),
                    temperature = context.getString(
                        R.string.widget_temperature_format,
                        hour.temperatureCelsius
                    ),
                    iconRes = WeatherIconResolver.iconRes(hour.weatherCode, hour.isDay),
                    description = context.getString(
                        WeatherCondition.descriptionRes(hour.weatherCode)
                    )
                )
            }

            ForecastMode.DAILY -> upcomingDays(snapshot).mapIndexed { index, day ->
                val locale = appLocale(context)
                val date = parseDate(day.date)
                val label = if (index == 0) {
                    context.getString(R.string.widget_today)
                } else {
                    date
                        ?.format(DateTimeFormatter.ofPattern("EEE", locale))
                        ?.replaceFirstChar { it.titlecase(locale) }
                        ?: day.date
                }
                val minText = context.getString(
                    R.string.widget_temperature_format,
                    day.temperatureMinCelsius
                )
                val maxText = context.getString(
                    R.string.widget_temperature_format,
                    day.temperatureMaxCelsius
                )

                ForecastItem(
                    label = label,
                    temperature = context.getString(
                        R.string.widget_daily_temperature_format,
                        minText,
                        maxText
                    ),
                    iconRes = WeatherIconResolver.iconRes(day.weatherCode, isDay = true),
                    description = context.getString(
                        R.string.widget_daily_description_format,
                        context.getString(WeatherCondition.descriptionRes(day.weatherCode)),
                        minText,
                        maxText
                    )
                )
            }
        }
    }

    private fun applyAppearance(views: RemoteViews, state: WidgetState) {
        val color = Color.rgb(
            Color.red(state.backgroundColor),
            Color.green(state.backgroundColor),
            Color.blue(state.backgroundColor)
        )
        val alpha = (state.backgroundOpacity.coerceIn(0, 100) * 255 / 100)

        views.setInt(R.id.widget_background, "setColorFilter", color)
        views.setInt(R.id.widget_background, "setImageAlpha", alpha)
        views.setInt(R.id.widget_background_border, "setImageAlpha", alpha)
    }

    private fun applyClockFormat(views: RemoteViews, timeFormat: TimeFormat) {
        val pattern = if (timeFormat == TimeFormat.HOUR_24) "HH:mm" else "h:mm a"
        views.setCharSequence(R.id.widget_clock, "setFormat12Hour", pattern)
        views.setCharSequence(R.id.widget_clock, "setFormat24Hour", pattern)
    }

    private fun locationName(
        context: Context,
        state: WidgetState,
        includeError: Boolean
    ): String {
        val name = state.snapshot?.locationLabel
            ?: state.configuredLocationName
            ?: context.getString(R.string.widget_location_empty)

        if (!includeError || state.lastError == null) return name

        return context.getString(
            R.string.widget_location_with_status,
            name,
            context.getString(R.string.widget_status_stale)
        )
    }

    private fun locationWithDate(
        context: Context,
        state: WidgetState,
        snapshot: WeatherSnapshot?
    ): String {
        val location = locationName(context, state, includeError = false)
        val date = formatDate(context, snapshot)
        val base = context.getString(R.string.widget_location_date_format, location, date)

        if (state.lastError == null) return base

        return context.getString(
            R.string.widget_location_with_status,
            base,
            context.getString(R.string.widget_status_stale)
        )
    }

    private fun formatDate(context: Context, snapshot: WeatherSnapshot?): String {
        val locale = appLocale(context)
        val zone = snapshot?.timezone?.let { timezone ->
            runCatching { ZoneId.of(timezone) }.getOrNull()
        } ?: ZoneId.systemDefault()

        val text = LocalDate.now(zone).format(
            DateTimeFormatter.ofPattern("EEEE d MMMM", locale)
        )

        return text.split(' ')
            .joinToString(" ") { word ->
                word.replaceFirstChar { it.titlecase(locale) }
            }
    }

    private fun upcomingHours(snapshot: WeatherSnapshot): List<HourlyForecast> {
        val zone = snapshot.timezone?.let { timezone ->
            runCatching { ZoneId.of(timezone) }.getOrNull()
        }
        val now = if (zone != null) LocalDateTime.now(zone) else LocalDateTime.now()

        return snapshot.hourly
            .filter { hour -> parseHourTime(hour.time)?.isAfter(now) ?: true }
    }

    private fun upcomingDays(snapshot: WeatherSnapshot): List<DailyForecast> {
        val zone = snapshot.timezone?.let { timezone ->
            runCatching { ZoneId.of(timezone) }.getOrNull()
        }
        val today = LocalDate.now(zone ?: ZoneId.systemDefault())

        return snapshot.daily.filter { day ->
            val date = parseDate(day.date)
            date == null || !date.isBefore(today)
        }
    }

    private fun formatHourTime(value: String, timeFormat: TimeFormat, locale: Locale): String {
        val time = parseHourTime(value) ?: return value.takeLast(5)
        val pattern = if (timeFormat == TimeFormat.HOUR_24) "HH:mm" else "h a"
        return time.format(DateTimeFormatter.ofPattern(pattern, locale))
    }

    private fun appLocale(context: Context): Locale =
        context.resources.configuration.locales[0]

    private fun parseHourTime(value: String): LocalDateTime? =
        runCatching { LocalDateTime.parse(value) }.getOrNull()

    private fun parseDate(value: String): LocalDate? =
        runCatching { LocalDate.parse(value) }.getOrNull()

    private fun temperatureText(context: Context, snapshot: WeatherSnapshot?): String =
        snapshot
            ?.let { context.getString(R.string.widget_temperature_format, it.temperatureCelsius) }
            ?: context.getString(R.string.widget_temperature_empty)

    private fun conditionText(context: Context, snapshot: WeatherSnapshot?): String =
        snapshot
            ?.let { context.getString(WeatherCondition.descriptionRes(it.weatherCode)) }
            ?: context.getString(R.string.widget_condition_empty)

    private data class ForecastItem(
        val label: String,
        val temperature: String,
        val iconRes: Int,
        val description: String
    )

    private data class LargeHourSlot(
        val container: Int,
        val time: Int,
        val icon: Int,
        val temperature: Int
    )

    private val LARGE_HOUR_SLOTS = listOf(
        LargeHourSlot(
            container = R.id.widget_hour_1,
            time = R.id.widget_hour_time_1,
            icon = R.id.widget_hour_icon_1,
            temperature = R.id.widget_hour_temp_1
        ),
        LargeHourSlot(
            container = R.id.widget_hour_2,
            time = R.id.widget_hour_time_2,
            icon = R.id.widget_hour_icon_2,
            temperature = R.id.widget_hour_temp_2
        ),
        LargeHourSlot(
            container = R.id.widget_hour_3,
            time = R.id.widget_hour_time_3,
            icon = R.id.widget_hour_icon_3,
            temperature = R.id.widget_hour_temp_3
        ),
        LargeHourSlot(
            container = R.id.widget_hour_4,
            time = R.id.widget_hour_time_4,
            icon = R.id.widget_hour_icon_4,
            temperature = R.id.widget_hour_temp_4
        ),
        LargeHourSlot(
            container = R.id.widget_hour_5,
            time = R.id.widget_hour_time_5,
            icon = R.id.widget_hour_icon_5,
            temperature = R.id.widget_hour_temp_5
        )
    )

    private val SQUARE_HOUR_ICONS = listOf(
        R.id.widget_square_hour_icon_1,
        R.id.widget_square_hour_icon_2,
        R.id.widget_square_hour_icon_3,
        R.id.widget_square_hour_icon_4,
        R.id.widget_square_hour_icon_5
    )

    private val XL_HOUR_SLOTS = listOf(
        LargeHourSlot(
            container = R.id.widget_hour_1,
            time = R.id.widget_hour_time_1,
            icon = R.id.widget_hour_icon_1,
            temperature = R.id.widget_hour_temp_1
        ),
        LargeHourSlot(
            container = R.id.widget_hour_2,
            time = R.id.widget_hour_time_2,
            icon = R.id.widget_hour_icon_2,
            temperature = R.id.widget_hour_temp_2
        ),
        LargeHourSlot(
            container = R.id.widget_hour_3,
            time = R.id.widget_hour_time_3,
            icon = R.id.widget_hour_icon_3,
            temperature = R.id.widget_hour_temp_3
        ),
        LargeHourSlot(
            container = R.id.widget_hour_4,
            time = R.id.widget_hour_time_4,
            icon = R.id.widget_hour_icon_4,
            temperature = R.id.widget_hour_temp_4
        ),
        LargeHourSlot(
            container = R.id.widget_hour_5,
            time = R.id.widget_hour_time_5,
            icon = R.id.widget_hour_icon_5,
            temperature = R.id.widget_hour_temp_5
        ),
        LargeHourSlot(
            container = R.id.widget_hour_6,
            time = R.id.widget_hour_time_6,
            icon = R.id.widget_hour_icon_6,
            temperature = R.id.widget_hour_temp_6
        ),
        LargeHourSlot(
            container = R.id.widget_hour_7,
            time = R.id.widget_hour_time_7,
            icon = R.id.widget_hour_icon_7,
            temperature = R.id.widget_hour_temp_7
        ),
        LargeHourSlot(
            container = R.id.widget_hour_8,
            time = R.id.widget_hour_time_8,
            icon = R.id.widget_hour_icon_8,
            temperature = R.id.widget_hour_temp_8
        )
    )

}
