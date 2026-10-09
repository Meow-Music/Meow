# Meow Music - Features & Cross-Project Merge Plan

---

## 📌 Executive Summary

By analyzing your sibling project **Echo Music** (`Meow-Music` located at `C:/Users/Rexx-Gaming-PC/AndroidStudioProjects/Meow-Music`), we identified several production-ready features and modules that can be pulled and merged directly into **Meow**.

Merging these components will bring **Shazam audio recognition**, **Media3 audio crossfade**, **silence skipping**, **beat detection**, **AutoEQ profile presets**, **USB DAC bit-perfect playback**, and **extra lyrics providers** directly into Meow without starting from scratch.

---

## 🗂️ Files to Copy & Target Paths in Meow

To merge features cleanly without package or build conflicts, copy the following files from `Meow-Music` into `Meow`:

### 1. 🔍 Shazam Audio Recognition ("Echo Find")
* **Source in `Meow-Music`**:
  * `shazamkit/src/main/kotlin/com/music/shazamkit/Shazam.kt`
  * `shazamkit/src/main/kotlin/com/music/shazamkit/models/ShazamModels.kt`
* **Target in `Meow`**:
  * `app/src/main/java/com/meow/music/data/shazam/Shazam.kt`
  * `app/src/main/java/com/meow/music/data/shazam/ShazamModels.kt`
* **Value Added**: Allows users to record surrounding audio using the mic and identify songs instantly via Shazam's API, opening the matched track directly in Meow!

---

### 2. 🎚️ Silence Detector Audio Processor (Auto-Skip Silence)
* **Source in `Meow-Music`**:
  * `playback/src/main/kotlin/echo/music/iad1tya/playback/audio/SilenceDetectorAudioProcessor.kt`
* **Target in `Meow`**:
  * `app/src/main/java/com/meow/music/playback/SilenceDetectorAudioProcessor.kt`
* **Value Added**: Media3 AudioProcessor that detects leading/trailing silence below a volume threshold and skips forward automatically.

---

### 3. 🔀 Standby Crossfade Scheduler (Smooth Track Crossfading)
* **Source in `Meow-Music`**:
  * `playback/src/main/kotlin/echo/music/iad1tya/playback/crossfade/StandbyCrossfadeScheduler.kt`
  * `playback/src/main/kotlin/echo/music/iad1tya/playback/crossfade/StandbyCrossfadeSchedulerImpl.kt`
  * `playback/src/main/kotlin/echo/music/iad1tya/playback/crossfade/AdaptivePreArmManager.kt`
  * `playback/src/main/kotlin/echo/music/iad1tya/playback/crossfade/EqualPowerCurve.kt`
* **Target in `Meow`**:
  * `app/src/main/java/com/meow/music/playback/crossfade/StandbyCrossfadeScheduler.kt`
  * `app/src/main/java/com/meow/music/playback/crossfade/StandbyCrossfadeSchedulerImpl.kt`
  * `app/src/main/java/com/meow/music/playback/crossfade/AdaptivePreArmManager.kt`
  * `app/src/main/java/com/meow/music/playback/crossfade/EqualPowerCurve.kt`
* **Value Added**: Pre-arms a standby ExoPlayer instance to smoothly crossfade audio between tracks when approaching the end of a song.

---

### 4. 🥁 Beat Analyzer & Tempo Detection
* **Source in `Meow-Music`**:
  * `playback/src/main/kotlin/echo/music/iad1tya/playback/audio/BeatAnalyzer.kt`
* **Target in `Meow`**:
  * `app/src/main/java/com/meow/music/playback/BeatAnalyzer.kt`
* **Value Added**: Real-time BPM calculation and onset/beat grid tracking for audio visualizers and beat-matched crossfading.

---

### 5. 🎧 AutoEQ Profile Repository & Custom Parametric EQ
* **Source in `Meow-Music`**:
  * `audio-dsp/src/main/kotlin/echo/music/dsp/data/EQProfileRepository.kt`
  * `audio-dsp/src/main/kotlin/echo/music/dsp/audio/CustomEqualizerAudioProcessor.kt`
* **Target in `Meow`**:
  * `app/src/main/java/com/meow/music/playback/dsp/EQProfileRepository.kt`
  * `app/src/main/java/com/meow/music/playback/dsp/CustomEqualizerAudioProcessor.kt`
* **Value Added**: Manage custom parametric EQ profiles and AutoEQ presets for specific headphone models.

---

### 6. 📜 Additional Lyrics Providers (SimpMusicLyrics & YouLyPlus)
* **Source in `Meow-Music`**:
  * `simpmusic/src/main/kotlin/com/music/simpmusic/SimpMusicLyrics.kt`
  * `youlyplus/src/main/kotlin/com/music/youlyplus/YouLyPlus.kt`
* **Target in `Meow`**:
  * `app/src/main/java/com/meow/music/data/lyrics/SimpMusicLyrics.kt`
  * `app/src/main/java/com/meow/music/data/lyrics/YouLyPlus.kt`
* **Value Added**: Expands lyric coverage and accuracy across obscure or newly released songs.

---

### 7. 🔌 Direct USB DAC Bit-Perfect Hardware Output
* **Source in `Meow-Music`**:
  * `usbaudio/` module (C++ engine + Kotlin USB driver)
* **Target in `Meow`**:
  * Can be added as a Gradle subproject module `:usbaudio` in `settings.gradle.kts` to enable bit-perfect 384kHz/32-bit hardware output to external USB DACs.

---

## 🛠️ Step-by-Step Merge & Integration Roadmap

1. **Step 1: Copy Core Audio Processors**:
   * Copy `SilenceDetectorAudioProcessor.kt` to `com.meow.music.playback`.
   * Add `SilenceDetectorAudioProcessor` into ExoPlayer's `DefaultRenderersFactory` audio processor chain in `PlaybackService.kt`.

2. **Step 2: Copy & Register Extra Lyrics Providers**:
   * Copy `SimpMusicLyrics.kt` and `YouLyPlus.kt` to `com.meow.music.data.lyrics`.
   * Register them inside `LyricsRepository.kt` as additional lyric search sources.

3. **Step 3: Integrate Shazam Recognition**:
   * Copy `Shazam.kt` & `ShazamModels.kt` to `com.meow.music.data.shazam`.
   * Adapt HTTP calls to use Meow's `OkHttpClient`.
   * Add a mic recognition button to `SearchScreen.kt`.

4. **Step 4: Integrate Standby Crossfade**:
   * Copy `StandbyCrossfadeScheduler` files to `com.meow.music.playback.crossfade`.
   * Connect scheduler events in `PlaybackService.kt` to manage smooth track transitions.

5. **Step 5: Integrate AutoEQ & EQ Profile Repository**:
   * Copy `EQProfileRepository.kt` & `CustomEqualizerAudioProcessor.kt` to `com.meow.music.playback.dsp`.
   * Connect profiles into `EqualizerScreen.kt` for headphone preset selection.
