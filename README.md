<div align="center">

# Meow

**A YouTube Music client for Android, with word-by-word lyrics, Replay, offline downloads and full Android Auto support.**

### Project layout

| Area | Where (`app/src/main/java/com/meow/music/`) |
|---|---|
| YouTube Music API, parsing and search ranking | `data/innertube/` |
| Stream resolving (NewPipe Extractor) | `data/stream/` |
| Lyric providers and TTML / LRC / QRC / YRC / KRC parsers | `data/lyrics/` |
| Playback service, audio effects, Android Auto and car lyrics | `playback/` |
| Downloads and smart downloads | `download/` |
| Compose UI and design kit | `ui/` |

Stack: Kotlin, Jetpack Compose (Material 3), Media3 ExoPlayer and MediaSession, Room, DataStore, OkHttp, kotlinx.serialization, Coil, WorkManager, NewPipe Extractor.
