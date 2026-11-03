package it.frankborty.weatherclockwidget

import android.Manifest
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.LruCache
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.color.MaterialColors
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import it.frankborty.weatherclockwidget.data.GeocodingResult
import it.frankborty.weatherclockwidget.data.ForecastMode
import it.frankborty.weatherclockwidget.data.LocationMode
import it.frankborty.weatherclockwidget.data.LocationProvider
import it.frankborty.weatherclockwidget.data.OpenMeteoClient
import it.frankborty.weatherclockwidget.data.RefreshOutcome
import it.frankborty.weatherclockwidget.data.SavedLocation
import it.frankborty.weatherclockwidget.data.TimeFormat
import it.frankborty.weatherclockwidget.data.WeatherPreferences
import it.frankborty.weatherclockwidget.data.WidgetAppearance
import it.frankborty.weatherclockwidget.widget.WeatherWidgetRefresher
import it.frankborty.weatherclockwidget.widget.WeatherWidgetRenderer
import it.frankborty.weatherclockwidget.widget.WidgetSize
import it.frankborty.weatherclockwidget.widget.WidgetUpdater
import it.frankborty.weatherclockwidget.work.WeatherUpdateScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

class MainActivity : AppCompatActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val openMeteoClient = OpenMeteoClient()

    private lateinit var preferences: WeatherPreferences
    private lateinit var locationProvider: LocationProvider

    private lateinit var currentLocation: TextView
    private lateinit var currentCoordinates: TextView
    private lateinit var lastUpdate: TextView
    private lateinit var gpsSwitch: MaterialSwitch
    private lateinit var cityQuery: TextInputEditText
    private lateinit var searchResults: LinearLayout
    private lateinit var refreshWeatherButton: MaterialButton
    private lateinit var progress: ProgressBar
    private lateinit var statusMessage: TextView
    private lateinit var colorSwatches: LinearLayout
    private lateinit var opacitySlider: Slider
    private lateinit var opacityValue: TextView
    private lateinit var timeFormatGroup: MaterialButtonToggleGroup
    private lateinit var forecastModeGroup: MaterialButtonToggleGroup
    private lateinit var hueSlider: Slider
    private lateinit var saturationSlider: Slider
    private lateinit var brightnessSlider: Slider
    private lateinit var colorPreview: View
    private lateinit var colorHex: TextView
    private lateinit var widgetPreview: FrameLayout

    private var bindingMode = false
    private var bindingColor = false
    private var isWorking = false
    private var citySearchJob: Job? = null
    private var suppressCityAutocomplete = false
    private val citySearchCache = LruCache<String, List<GeocodingResult>>(CITY_SEARCH_CACHE_SIZE)

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (granted) {
            updateGpsCoordinatesOnly()
        } else {
            preferences.mode = LocationMode.FIXED
            renderLocationSummary()
            showStatus(getString(R.string.gps_permission_denied), isError = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        preferences = WeatherPreferences(this)
        locationProvider = LocationProvider(this)

        bindViews()
        bindActions()
        renderState()
        startInitialWork()
        WeatherUpdateScheduler.syncWithWidgets(this)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun bindViews() {
        currentLocation = findViewById(R.id.current_location)
        currentCoordinates = findViewById(R.id.current_coordinates)
        lastUpdate = findViewById(R.id.last_update)
        gpsSwitch = findViewById(R.id.gps_switch)
        cityQuery = findViewById(R.id.city_query)
        searchResults = findViewById(R.id.search_results)
        refreshWeatherButton = findViewById(R.id.refresh_weather_button)
        progress = findViewById(R.id.progress)
        statusMessage = findViewById(R.id.status_message)
        colorSwatches = findViewById(R.id.color_swatches)
        opacitySlider = findViewById(R.id.opacity_slider)
        opacityValue = findViewById(R.id.opacity_value)
        timeFormatGroup = findViewById(R.id.time_format_group)
        forecastModeGroup = findViewById(R.id.forecast_mode_group)
        hueSlider = findViewById(R.id.hue_slider)
        saturationSlider = findViewById(R.id.saturation_slider)
        brightnessSlider = findViewById(R.id.brightness_slider)
        colorPreview = findViewById(R.id.color_preview)
        colorHex = findViewById(R.id.color_hex)
        widgetPreview = findViewById(R.id.widget_preview_container)
    }

    private fun bindActions() {
        gpsSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (bindingMode) return@setOnCheckedChangeListener

            if (isChecked) {
                onGpsModeSelected()
            } else {
                onFixedModeSelected()
            }
        }

        timeFormatGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (bindingMode || !isChecked) return@addOnButtonCheckedListener

            onTimeFormatSelected(
                if (checkedId == R.id.time_12) TimeFormat.HOUR_12 else TimeFormat.HOUR_24
            )
        }

        forecastModeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (bindingMode || !isChecked) return@addOnButtonCheckedListener

            onForecastModeSelected(
                if (checkedId == R.id.forecast_daily) ForecastMode.DAILY else ForecastMode.HOURLY
            )
        }

        val colorSliders = listOf(hueSlider, saturationSlider, brightnessSlider)
        colorSliders.forEach { slider ->
            slider.addOnChangeListener { _, _, fromUser ->
                if (fromUser && !bindingColor) {
                    updateColorPreview(currentPickedColor())
                }
            }
            slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) = Unit

                override fun onStopTrackingTouch(slider: Slider) {
                    if (!bindingColor) {
                        onColorSelected(currentPickedColor())
                    }
                }
            })
        }

        opacitySlider.addOnChangeListener { _, value, fromUser ->
            if (bindingMode) return@addOnChangeListener

            val opacity = value.toInt()
            opacityValue.text = getString(R.string.opacity_value_format, opacity)

            if (fromUser) {
                preferences.widgetBackgroundOpacity = opacity
                WidgetUpdater.updateAll(this)
                renderPreview()
            }
        }

        refreshWeatherButton.setOnClickListener { refreshWeather() }

        cityQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchCity()
                true
            } else {
                false
            }
        }

        cityQuery.doAfterTextChanged { editable ->
            if (suppressCityAutocomplete) return@doAfterTextChanged

            scheduleCityAutocomplete(editable?.toString().orEmpty())
        }
    }

    private fun startInitialWork() {
        when {
            preferences.mode == LocationMode.GPS &&
                locationProvider.hasLocationPermission() -> updateGpsLocationAndWeather()

            preferences.mode == LocationMode.GPS -> {
                showStatus(getString(R.string.gps_permission_denied), isError = true)
            }

            preferences.weather == null -> refreshWeather()
        }
    }

    private fun renderState() {
        bindingMode = true
        timeFormatGroup.check(
            if (preferences.timeFormat == TimeFormat.HOUR_12) R.id.time_12 else R.id.time_24
        )
        forecastModeGroup.check(
            if (preferences.forecastMode == ForecastMode.DAILY) {
                R.id.forecast_daily
            } else {
                R.id.forecast_hourly
            }
        )
        val opacity = preferences.widgetBackgroundOpacity
        opacitySlider.value = opacity.toFloat()
        opacityValue.text = getString(R.string.opacity_value_format, opacity)
        bindingMode = false

        renderColorSwatches()
        renderColorPicker(preferences.widgetBackgroundColor)
        renderLocationSummary()

        val lastSuccess = preferences.lastSuccessEpochMillis
        lastUpdate.text = if (lastSuccess != null) {
            getString(R.string.last_update_format, formatTimestamp(lastSuccess))
        } else {
            getString(R.string.last_update_none)
        }

        if (statusMessage.text.isNullOrBlank()) {
            preferences.lastError
                ?.takeIf { it.isNotBlank() }
                ?.let { showStatus(it, isError = true) }
        }

        renderPreview()
    }

    private fun renderLocationSummary() {
        val location = preferences.location
        currentLocation.text = location.name
        currentCoordinates.text = getString(
            R.string.coordinates_format,
            location.latitude,
            location.longitude
        )

        bindingMode = true
        gpsSwitch.isChecked = preferences.mode == LocationMode.GPS
        bindingMode = false
    }

    private fun renderWeatherDependentUi() {
        lastUpdate.text = preferences.lastSuccessEpochMillis?.let { lastSuccess ->
            getString(R.string.last_update_format, formatTimestamp(lastSuccess))
        } ?: getString(R.string.last_update_none)

        renderPreview()
    }

    private fun renderPreview() {
        if (!::widgetPreview.isInitialized) return

        val views = WeatherWidgetRenderer.renderPreview(
            context = this,
            state = WidgetUpdater.currentState(this),
            widgetSize = WidgetSize.LARGE
        )
        // RemoteViews only accepts framework widget classes. The activity's
        // inflater is AppCompat-instrumented, so inflate through the plain
        // application inflater instead.
        val previewContext = ContextThemeWrapper(
            applicationContext,
            android.R.style.Theme_DeviceDefault
        )

        val existing = widgetPreview.getChildAt(0)
        if (existing != null) {
            views.reapply(previewContext, existing)
            return
        }

        val inflated = views.apply(previewContext, widgetPreview)

        widgetPreview.addView(
            inflated,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun onTimeFormatSelected(format: TimeFormat) {
        preferences.timeFormat = format
        WidgetUpdater.updateAll(this)
        renderPreview()
        lastUpdate.text = preferences.lastSuccessEpochMillis?.let { lastSuccess ->
            getString(R.string.last_update_format, formatTimestamp(lastSuccess))
        } ?: getString(R.string.last_update_none)
    }

    private fun onForecastModeSelected(mode: ForecastMode) {
        preferences.forecastMode = mode
        WidgetUpdater.updateAll(this)
        renderPreview()
    }

    private fun renderColorSwatches() {
        colorSwatches.removeAllViews()
        val selectedColor = preferences.widgetBackgroundColor

        WidgetAppearance.BACKGROUND_COLORS.forEachIndexed { index, color ->
            val selected = color == selectedColor
            val swatch = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    setStroke(
                        if (selected) 3.dp() else 1.dp(),
                        if (selected) Color.WHITE else 0x33FFFFFF
                    )
                }
                contentDescription = getString(R.string.color_swatch_description, index + 1)
                isClickable = true
                setOnClickListener { onColorSelected(color) }
            }

            val size = 36.dp()
            val params = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = 8.dp()
            }
            colorSwatches.addView(swatch, params)
        }
    }

    private fun onColorSelected(color: Int) {
        preferences.widgetBackgroundColor = color
        renderColorSwatches()
        renderColorPicker(color)
        WidgetUpdater.updateAll(this)
        renderPreview()
    }

    private fun renderColorPicker(color: Int) {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)

        bindingColor = true
        hueSlider.value = hsv[0].coerceIn(0f, 360f)
        saturationSlider.value = (hsv[1] * 100f).coerceIn(0f, 100f)
        brightnessSlider.value = (hsv[2] * 100f).coerceIn(0f, 100f)
        bindingColor = false

        updateColorPreview(color)
    }

    private fun currentPickedColor(): Int = Color.HSVToColor(
        255,
        floatArrayOf(
            hueSlider.value.coerceIn(0f, 360f),
            saturationSlider.value.coerceIn(0f, 100f) / 100f,
            brightnessSlider.value.coerceIn(0f, 100f) / 100f
        )
    )

    private fun updateColorPreview(color: Int) {
        colorPreview.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(2.dp(), 0x66FFFFFF)
        }
        colorHex.text = String.format(Locale.US, "#%06X", 0xFFFFFF and color)
    }

    private fun onFixedModeSelected() {
        if (preferences.mode == LocationMode.GPS) {
            preferences.saveFixedLocation(preferences.location)
            preferences.mode = LocationMode.FIXED
        }
        renderLocationSummary()
    }

    private fun onGpsModeSelected() {
        preferences.mode = LocationMode.GPS
        renderLocationSummary()

        if (locationProvider.hasLocationPermission()) {
            updateGpsCoordinatesOnly()
        } else {
            requestLocationPermission()
        }
    }

    private fun updateGpsCoordinatesOnly() {
        if (!locationProvider.hasLocationPermission()) {
            requestLocationPermission()
            return
        }

        scope.launch {
            try {
                val location = locationProvider.currentLocation()
                if (location == null) {
                    showStatus(getString(R.string.gps_unavailable), isError = true)
                    return@launch
                }

                val name = try {
                    locationProvider.reverseGeocode(location.latitude, location.longitude)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }

                preferences.saveGpsLocation(
                    SavedLocation(
                        name = name ?: getString(R.string.location_current),
                        latitude = location.latitude,
                        longitude = location.longitude
                    )
                )
                renderLocationSummary()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                showStatus(getString(R.string.gps_unavailable), isError = true)
            }
        }
    }

    private fun requestLocationPermission() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun scheduleCityAutocomplete(rawQuery: String) {
        val query = rawQuery.trim()
        citySearchJob?.cancel()

        if (query.length < MIN_CITY_QUERY_LENGTH) {
            searchResults.removeAllViews()
            searchResults.visibility = View.GONE
            return
        }

        citySearchJob = scope.launch {
            delay(AUTOCOMPLETE_DEBOUNCE_MS.milliseconds)

            val cacheKey = query.lowercase(Locale.ROOT)
            val cached = citySearchCache.get(cacheKey)
            if (cached != null) {
                if (currentCityQuery() == query) {
                    showSearchResults(cached)
                }
                return@launch
            }

            try {
                val results = openMeteoClient.searchLocations(query)
                citySearchCache.put(cacheKey, results)
                if (currentCityQuery() == query) {
                    showSearchResults(results)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Keep the previous results. A manual search reports the error.
            }
        }
    }

    private fun currentCityQuery(): String =
        cityQuery.text?.toString()?.trim().orEmpty()

    private fun searchCity() {
        if (isWorking) return

        val query = currentCityQuery()
        if (query.length < MIN_CITY_QUERY_LENGTH) {
            showStatus(getString(R.string.search_query_too_short), isError = true)
            return
        }

        hideKeyboard()
        citySearchJob?.cancel()
        searchResults.removeAllViews()
        searchResults.visibility = View.GONE
        setWorking(true)
        showStatus(getString(R.string.search_in_progress), isError = false)

        scope.launch {
            try {
                val cacheKey = query.lowercase(Locale.ROOT)
                val results = citySearchCache.get(cacheKey)
                    ?: openMeteoClient.searchLocations(query).also { fetched ->
                        citySearchCache.put(cacheKey, fetched)
                    }
                showSearchResults(results)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                showStatus(getString(R.string.search_error), isError = true)
            } finally {
                setWorking(false)
            }
        }
    }

    private fun showSearchResults(results: List<GeocodingResult>) {
        searchResults.removeAllViews()

        if (results.isEmpty()) {
            searchResults.visibility = View.GONE
            showStatus(getString(R.string.search_no_results), isError = false)
            return
        }

        searchResults.visibility = View.VISIBLE
        showStatus(getString(R.string.search_select_location), isError = false)

        results.forEach { result ->
            val subtitle = listOfNotNull(result.admin1, result.country).joinToString(", ")
            val label = if (subtitle.isBlank()) {
                result.name
            } else {
                getString(R.string.search_result_format, result.name, subtitle)
            }

            val item = TextView(this).apply {
                text = label
                textSize = 16f
                setPadding(16.dp(), 12.dp(), 16.dp(), 12.dp())
                isClickable = true
                isFocusable = true
                setTextColor(
                    MaterialColors.getColor(
                        this,
                        com.google.android.material.R.attr.colorOnSurface
                    )
                )
                setSelectableItemBackground()
                setOnClickListener { selectFixedLocation(result) }
            }

            searchResults.addView(
                item,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun selectFixedLocation(result: GeocodingResult) {
        preferences.mode = LocationMode.FIXED
        preferences.saveFixedLocation(
            SavedLocation(
                name = result.name,
                latitude = result.latitude,
                longitude = result.longitude,
                timezone = result.timezone
            )
        )

        searchResults.removeAllViews()
        searchResults.visibility = View.GONE
        citySearchJob?.cancel()
        suppressCityAutocomplete = true
        cityQuery.setText("")
        suppressCityAutocomplete = false
        hideKeyboard()
        renderLocationSummary()
        refreshWeather()
    }

    private fun refreshWeather() {
        if (isWorking) return

        setWorking(true)
        scope.launch {
            try {
                refreshWeatherInternal()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                showStatus(getString(R.string.weather_error_generic), isError = true)
            } finally {
                setWorking(false)
                renderWeatherDependentUi()
            }
        }
    }

    private suspend fun refreshWeatherInternal() {
        try {
            when (val outcome = WeatherWidgetRefresher.refresh(applicationContext)) {
                is RefreshOutcome.Success -> {
                    showStatus("", isError = false)
                }
                is RefreshOutcome.Failure -> {
                    showStatus(outcome.message, isError = true)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            showStatus(getString(R.string.weather_error_generic), isError = true)
        }
    }

    private fun updateGpsLocationAndWeather() {
        if (isWorking) return

        if (!locationProvider.hasLocationPermission()) {
            showStatus(getString(R.string.gps_permission_denied), isError = true)
            requestLocationPermission()
            return
        }

        setWorking(true)

        scope.launch {
            var locationUnavailable = false

            try {
                val location = locationProvider.currentLocation()
                if (location == null) {
                    locationUnavailable = true
                } else {
                    val name = try {
                        locationProvider.reverseGeocode(location.latitude, location.longitude)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                    preferences.saveGpsLocation(
                        SavedLocation(
                            name = name ?: getString(R.string.location_current),
                            latitude = location.latitude,
                            longitude = location.longitude
                        )
                    )
                    renderLocationSummary()
                }

                refreshWeatherInternal()

                if (locationUnavailable) {
                    showStatus(getString(R.string.gps_unavailable), isError = true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                showStatus(getString(R.string.gps_unavailable), isError = true)
            } finally {
                setWorking(false)
                renderWeatherDependentUi()
            }
        }
    }

    private fun setWorking(working: Boolean) {
        isWorking = working
        progress.visibility = if (working) View.VISIBLE else View.GONE
        refreshWeatherButton.isEnabled = !working
        gpsSwitch.isEnabled = !working
        cityQuery.isEnabled = !working
    }

    private fun showStatus(message: String, isError: Boolean) {
        statusMessage.text = message
        statusMessage.visibility = if (message.isBlank()) View.GONE else View.VISIBLE

        val colorAttribute = if (isError) {
            com.google.android.material.R.attr.colorError
        } else {
            com.google.android.material.R.attr.colorOnSurfaceVariant
        }

        statusMessage.setTextColor(MaterialColors.getColor(statusMessage, colorAttribute))
    }

    private fun formatTimestamp(epochMillis: Long): String {
        val pattern = if (preferences.timeFormat == TimeFormat.HOUR_24) {
            "d MMM yyyy, HH:mm"
        } else {
            "d MMM yyyy, h:mm a"
        }

        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(epochMillis))
    }

    private fun hideKeyboard() {
        val inputMethodManager = getSystemService(InputMethodManager::class.java) ?: return
        inputMethodManager.hideSoftInputFromWindow(cityQuery.windowToken, 0)
        cityQuery.clearFocus()
    }

    private fun TextView.setSelectableItemBackground() {
        val value = TypedValue()
        if (context.theme.resolveAttribute(
                android.R.attr.selectableItemBackground,
                value,
                true
            )
        ) {
            setBackgroundResource(value.resourceId)
        }
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    private companion object {
        const val MIN_CITY_QUERY_LENGTH = 2
        const val AUTOCOMPLETE_DEBOUNCE_MS = 350L
        const val CITY_SEARCH_CACHE_SIZE = 20
    }
}
