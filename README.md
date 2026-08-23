# Athena

Athena is an Android app for keeping calendars, anniversaries, and countdowns together.

## Included

- Month calendar with important-day markers and date selection
- Separate anniversary and countdown lists
- Add important dates with type, note, repeat behavior, and a date picker
- Edit and delete existing dates from any list or calendar card
- Annual occurrences automatically roll forward, including leap-day anniversaries
- Local SQLite persistence for user-added dates using Room, including automatic migration from the earlier SharedPreferences format
- Five persistent in-app themes: Mist Violet, Sage, Sunrise Amber, Ocean Blue, and Rose

## Open and run

1. Open `D:\Developer\Athena` in Android Studio.
2. Use JDK 17 and let Android Studio sync the Gradle Wrapper.
3. Select an Android device or emulator running Android 8.0 (API 26) or newer.
4. Run the `app` configuration.

The project targets Android API 36 and uses Jetpack Compose.

## Releases

Pushing a `vMAJOR.MINOR.PATCH` tag runs the signed Android release pipeline and publishes the APK plus its SHA-256 checksum to GitHub Releases. See [docs/releasing.md](docs/releasing.md) for one-time signing setup and release instructions.
