# Changelog - Meow

Release notes and feature updates for Meow (v1.2.1 to v1.2.5).

## 🚀 Version 1.2.5 (Latest Release)

### ✨ New Features in this Update
- **MetroLyrics Animations**: Immersive word-by-word synchronized karaoke lyric highlighting and fluid layout transitions.
- **Keep-Screen-On Control**: Automatically prevents screen timeout while viewing lyrics or during active music playback.
- **Advanced Discord Rich Presence**: Enhanced Discord gateway integration supporting multiple activity states (`Listening`, `Playing`, `Watching`, `Competing`) and rich presence custom buttons.
- **Album Art Cropping & Letterbox Fix**: Ensures edge-to-edge album cover display without black letterbox bars on custom aspect ratios.
- **Spotify Token Optimization**: Streamlined account loading and resilient background token refresh flows.

### 🛠️ Bug Fixes & Improvements
- **Playback & Buffering**: Optimized track preloading and reduced slow starts on cellular connections.
- **Offline Library Caching**: Improved robust local state caching for playlists and favorites.

---

## 1.2.4

### New
- **Spotify Account & Token Optimization**: Streamlined account loading and bulletproofed token refresh flows.
- **Widget Enhancements**: Added Meow, Cover, Disc, and Strip home screen widgets with custom corner radius and background blur.

### Fixed
- **Audio Buffering & Startup**: Optimized streaming preloader and reduced slow starts on mobile data.
- **Offline Library Sync**: Improved local library caching and playlist restoration without network connections.

---

## 1.2.3

### New
- **Data Saver Mode**: Automatically restricts high-resolution image preloading and high bitrate streaming on mobile data connections.
- **Lyrics Source Selection**: Individual switches for each lyrics source in Settings → Lyrics → Sources.

### Fixed
- **Lyrics Saving to Files**: Safe LRC tag writing into local FLAC and MP3 files with backup-and-restore protection.
- **Crash Fixes**: Resolved background initialization crashes on Android 12+.

---

## 1.2.2

### New
- **Speed Dial & Quick Picks**: Added customizable Speed dial tiles on Home and Quick picks radio launching.
- **Local Files Integration**: Added local music search and tab navigation for local lossless files.

### Fixed
- **Queue Synchronization**: Fixed track synchronization issues when shuffling or reordering queue items.
- **Memory Optimization**: Reduced bitmap cache footprint when loading high-resolution cover art.

---

## 1.2.1

### New
- **Initial Meow**: Converted all underlying components, packages, and UI elements to Meow.
- **Discord Rich Presence**: Integrated gateway presence showing current track, artist progress, and status on user profiles.

### Fixed
- **Navigation & Routing**: Stabilized deep link routing and bottom navigation bar transitions.
- **Audio Effects**: Initialized audio session normalizer and normalizer processor for consistent playback volume.
