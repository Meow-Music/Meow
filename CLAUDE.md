# Meow

Unofficial YouTube Music client for Android (Kotlin + Jetpack Compose). Talks directly to
YouTube Music and ~10 lyric services from the phone; no backend. Features word-synced lyrics,
offline downloads, EQ/spatial audio, on-device "Replay" stats, and full Android Auto support.

## Stack
- Kotlin, Compose (Material 3), Media3 ExoPlayer + MediaSession, Room (KSP), DataStore,
  OkHttp, kotlinx.serialization, Coil 3, WorkManager, NewPipe Extractor, Haze (blur).
- JDK 17, compileSdk 37, targetSdk 36, minSdk 26. Package/applicationId: `com.meow.music`.
- Single Gradle module `:app`.

## Commands (Windows: use `.\gradlew.bat`)
- Build release APK: `./gradlew :app:assembleRelease` → `app/build/outputs/apk/release/`
- Debug build: `./gradlew :app:assembleDebug`
- Unit tests: `./gradlew :app:testDebugUnitTest` (some hit live lyric/YT Music endpoints — needs network, can flake)

## Map (`app/src/main/java/com/meow/music/`)
- `MainActivity.kt` — entry point
- `AppContainer.kt` — manual dependency wiring (no Hilt/Dagger)
- `data/innertube/` — YouTube Music API, response parsing, search ranking
- `data/stream/` — stream URL resolving via NewPipe Extractor
- `data/lyrics/` — lyric providers + TTML/LRC/QRC/YRC/KRC parsers
- `data/db/`, `data/local/` — Room database / local storage
- `data/prefs/` — DataStore settings
- `data/model/`, `data/meta/`, `data/canvas/` — models, metadata, animated covers
- `playback/` — playback service, audio effects, Android Auto browse tree, car lyrics
- `download/` — downloads and smart downloads
- `ui/` — Compose: `screens/`, `player/`, `components/`, `theme/`
- `app/src/test/` — JUnit 4 unit tests
- `docs/screenshots/` — README images only

## Conventions / decisions
- Release builds are **unminified** on purpose: NewPipeExtractor + Rhino use reflection. Don't enable R8.
- Lyrics priority: word-synced → line-synced → plain; user can switch source per song and offset timing.
- Privacy: all history/Replay/settings stay on device; no Meow server. Don't add telemetry.
- Update banner (`data/Updates.kt`): Home asks GitHub's `releases/latest` (≤ every 6 h, always on; Settings → About checks on tap)
  and compares its tag to `versionName`.
- Fresh-install defaults in `AppSettings` / `HomeSections.initial` are customized defaults (smart downloads 1 GB).
- Streams (`data/stream/StreamResolver.kt`): NewPipe resolves anonymously first; when signed in, songs it can't play
  (age-restricted, Music Premium-only, no usable audio) are re-asked via `InnerTube.signedInPlayer` as the TV client
  (`TVHTML5`, cookies + SAPISIDHASH, no PO token), then WEB_REMIX.
- Not affiliated with Google/YouTube — keep the disclaimer in README.
- `*.apk`, `local.properties`, keystores are gitignored.
