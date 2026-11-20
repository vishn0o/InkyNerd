# InkyNerd

An Android app that turns niche Wikipedia trivia and nerdy facts into a home-screen widget, a reader, and a quiz.

## Features

- **Home-screen widget** with three sizes (standard 4×2, compact 3×2, large 4×3) and five visual styles: E-ink, Swiss, Glass, Dot, and Almanac. Tap to cycle topics or advance to the next fact; swipe-to-refresh support is built in.
- **Reader** with "rabbit hole" navigation through linked articles, back/forward history that survives process death, and an offline archive.
- **Bookmarks and folders** — save facts from the widget or the reader and organize them into folders.
- **Quizzes** — ranked and daily rounds backed by the fact archive.
- **Offline-first** — facts are cached in Room and refreshed by a periodic WorkManager job (only when the battery is not low).

## Tech stack

- Kotlin + Jetpack Compose (Material 3, Navigation Compose)
- Room (KSP, with exported schemas and migration tests), DataStore, WorkManager
- Retrofit + OkHttp + `kotlinx.serialization` against the public Wikipedia APIs (no API keys required)
- Gradle Kotlin DSL with a version catalog (`gradle/libs.versions.toml`)

## Requirements

- Android Studio with Android SDK (compileSdk 36, minSdk 26, targetSdk 35)
- JDK 17
- Set `sdk.dir` in `local.properties` (this file is git-ignored)

## Build and test

```bash
./gradlew assembleDebug          # build the debug APK
./gradlew testDebugUnitTest      # run unit tests
./gradlew lintDebug              # run lint
```

Instrumented tests require a connected emulator/device:

```bash
./gradlew connectedDebugAndroidTest
```

## Documentation

- [`docs/AUDIT_REPORT.md`](docs/AUDIT_REPORT.md) — architecture, performance, security, and test coverage notes.

The bundled fonts are distributed under the SIL Open Font License; their license texts are in `app/src/main/assets/licenses/`.
