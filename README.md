[Italiano](README.it.md) | English

# WeatherClockWidget

Android home-screen widget that combines a clock, current weather conditions and forecasts. The app includes a settings screen with a live preview of the widget appearance.

The project is written in Kotlin, with no third-party UI libraries beyond AndroidX and Material components.

## Project status

This repository is a personal experiment built with a vibe-coding approach: rapid, iterative development assisted by language models, with manual verification of the result. It is not a commercial product, is not intended for production use and has no support process.

The software is provided as is, without warranties of correctness, stability or fitness for a particular purpose. No support, response time or commitment to fix issues is provided.

Issues, reports and pull requests may not be read, handled or accepted. If you find a bug or need a change, fork the repository, apply the fix in your own fork and maintain it yourself.

## Features

- Resizable widget with dedicated layouts based on the available space:
  - 1x1: clock on top and weather below
  - 2x1: clock, icon and temperature
  - 4x1: clock on the left, weather right-aligned
  - 2x2: clock, temperature and forecast icons
  - 4x2: clock, date, location, min/max and forecast strip
  - maximum size: extended layout with eight forecast slots
- Bold clock with 12-hour or 24-hour format.
- Clock tap: opens the device Clock app, with fallbacks for common apps.
- Tap anywhere else on the widget: opens the app settings.
- Forecasts for the next hours or the next days, selectable in the settings.
- Daily forecasts with minimum and maximum temperatures.
- Italian and English interface, with a System / Italiano / English selector. Defaults to the system language.
- Always-visible city search with automatic suggestions while typing; selecting a result saves the location automatically.
- GPS location switch: sets the location and coordinates from the device position. Turning it off keeps the current city as the manual location. Enabling GPS does not force a weather refresh.
- Custom background color with HSV picker, preset swatches and hex code.
- Adjustable background opacity.
- Live preview in the settings, updated as color, opacity, time format and forecast mode change.
- Local cache of the last successful update, also shown when the network fails.
- Periodic updates through WorkManager and manual refresh from the app.
- Status messages shown only on errors; successful updates do not add status lines.

## Requirements

- Android API 37 or higher.
- JDK 17.
- A recent Android Studio with Android SDK Platform 37 installed.
- The Gradle Wrapper included in the repository.

## Building

Clone the repository and use the Gradle wrapper:

```bash
git clone https://github.com/frankborty/WeatherClockWidget.git
cd WeatherClockWidget
./gradlew assembleDebug
```

On Windows:

```powershell
.\gradlew.bat assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The `local.properties` file containing the SDK path is generated automatically by Android Studio and is not versioned.

## Project structure

```text
app/src/main/java/it/frankborty/weatherclockwidget/
  MainActivity.kt              Settings screen, preview and status
  data/                        Preferences, Open-Meteo client, location, repository
  domain/                      Current, hourly and daily weather models
  widget/                      Provider, RemoteViews renderer, size resolution
  work/                        Periodic updates with WorkManager
```

Widget resources are in `app/src/main/res/layout`, and graphic styles are in `app/src/main/res/drawable`.

## Data and privacy

Weather data comes from [Open-Meteo](https://open-meteo.com/) and does not require API keys. The coordinates of the selected location are sent to Open-Meteo services to retrieve weather data and search for locations.

The app does not include analytics, telemetry or advertising. Location, preferences and the last weather snapshot are stored locally on the device through `SharedPreferences`.

Declared permissions:

- `INTERNET`: fetching weather data and searching for locations.
- `ACCESS_COARSE_LOCATION`: approximate location in GPS mode.
- `ACCESS_FINE_LOCATION`: precise location in GPS mode.

## Known limitations

- The project does not include an automated test suite.
- The selected layout depends on the dp size reported by the launcher and may vary between devices.
- Clock app availability depends on the apps installed on the device.
- The project is optimized for personal use, not for public distribution.

## License

The project is released into the public domain under the Unlicense. Anyone may use, modify, distribute or sell it without attribution. The software is provided without warranties and without any liability for the authors. The full text is available in the `LICENSE` file.
