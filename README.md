# Project:SHIRO

**Project:SHIRO** is a native Android music player and interactive 2.5D visualizer built with **Jetpack Compose**, **Jetpack Media3 (ExoPlayer)**, and a custom **Deformable Jelly Physics Engine**. 

At the heart of the app is **Shiro**—an adorable, tail-free round fluff mascot that dances, bobs, rolls, and reacts to your music in real time.

---

## Key Features

### Interactive Music Visualizer & Stage
* **4 Visualizer Combinations**:
  * **Spectrum / Floor**: 24 glass columns with slow peak trails along the bottom details panel.
  * **Spectrum / Circle**: 24 radial glass bars wrapping around the album artwork with peak trails.
  * **Liquid / Floor**: 3 undulating liquid glass ribbons flowing across the baseline.
  * **Liquid / Circle**: A continuous 96-segment circular liquid wave wrapping around the album art with inner decorative layers and contact dents.
* **Dynamic Palette Theming**: Extracts dominant artwork colors (`ArtworkPalette.kt`) to dynamically theme the seekbar, buttons, details panel, and glass UI cards.
* **Audio-Driven Motion**: Real-time 24-band FFT spectrum analyzer (`SpectrumAnalyzer.kt`), rhythm tracker (`RhythmTracker.kt`), and peak energy detectors drive visual bar heights, wave amplitudes, and Shiro jump forces without using the microphone.

### Deformable Jelly Physics Engine
* **20×16 Deformable Jelly Mesh** (`JellyMesh.kt`): Bellies bulge, bodies bend, and facial expressions deform based on impact speed, local wave slope, and contact points.
* **120 Hz Physics Substepping** (`WaveCatBody.kt`, `CatPhysicsClock.kt`): Simulates gravity, slope friction, rotational spin, jelly spring recovery, and cat-to-cat collisions.
* **Core & Shell Contours**: Firm inner circular core and soft outer jelly layer; contact dents deform around the core without collapsing.
* **Radial Center Field Physics**: In Circle layouts (`CenterCatField.kt`), bass energy generates radial suction toward the center while transient beats fling Shiros outward.
* **Interactive Touch Gestures**:
  * **Bounce**: Tap any Shiro to bounce it.
  * **Toss**: Drag and toss Shiros with capped momentum.
  * **Split**: Double-tap an original Shiro to split it into two smaller kitten children (~71% size).
  * **Merge**: Drag two child Shiros together to merge them back into a full-sized original.
  * **Population Limit**: Supports 5 to 10 active Shiros.

### Background Playback & Media Service
* **Foreground Media3 Service** (`PlaybackService.kt`): Independent `MediaSessionService` owning ExoPlayer and MediaSession. Audio playback continues seamlessly in the background when navigating between screens or swiping the app away from recent tasks.
* **System Media Player Integration**: Integrates directly with Android's native System Media Player card (showing song title, artist, timeline seekbar, play/pause, prev, next, and custom Exit X button).
* **Sleep Timer** (`SleepTimer.kt`): 15, 30, or 60-minute countdowns or **End of Track** timer. Features a gentle 30-second volume fade, dimmed stage lighting, and sleepy Shiro eye expressions.
* **Playlist & Storage**: SAF-based music folder scanning (`FolderScan.kt`, `PlaylistScanner.kt`), persistent cache (`PlaylistCache.kt`), and favorite tracks management with filter chips (**All tracks** / **Favorites**).

### Adaptive Glassmorphic UI & Widgets
* **Glassmorphic Panel Design** (`GlassStyle.kt`): Translucent glass panels with light edge highlights, subtle gradients, and physical touch responses (4% compression, 180ms rim brightening).
* **App Widgets** (`ShiroWidgetProvider.kt`, `ShiroWidgetUpdates.kt`): Home screen widget displaying current track details, artwork frame, and playback controls.

---

## Architecture & Code Structure

```
com.example.shiro/
├── MainActivity.kt            # Main Compose UI, screen navigation, and permission flows
├── PlaybackService.kt         # Media3 MediaSessionService owning ExoPlayer and notifications
├── PlaybackSpectrum.kt        # Real-time spectrum frame distribution
├── ShiroStage.kt              # Core visualizer stage & Shiro physics rendering
├── ConnectedShiroStage.kt     # Lifecycle-aware Stage wrapper
├── WaveCatBody.kt             # 2D physics body (velocity, spin, gravity, floor step)
├── CatCollision.kt            # Concave outline collision detection & impulse resolution
├── JellyMesh.kt               # 20x16 vertex deformation mesh
├── JellyCat.kt                # Canvas rendering for deformable Shiros
├── SpectrumAnalyzer.kt        # 24-band FFT audio analyzer
├── RhythmTracker.kt           # Real-time beat strength and tempo estimation
├── ArtworkPalette.kt          # Color extraction from album artwork
├── GlassStyle.kt              # Translucent glass UI modifiers and themes
├── PlaylistScanner.kt         # SAF folder music scanner & track metadata parser
├── SleepTimer.kt              # Sleep timer state and volume fade logic
└── ui/theme/                  # Compose typography, colors, and Material3 theme
```

---

## Android Requirements

* **Minimum SDK**: Android 8.0 (API Level 26)
* **Target SDK**: Android 16 (API Level 37)
* **Compile SDK**: API Level 37
* **Language / Language Level**: Kotlin 2.2+, Java 11
* **UI Framework**: Jetpack Compose (Material3)
* **Media Engine**: Jetpack Media3 (ExoPlayer & MediaSession v1.5.1)

---

## Building & Running

### Prerequisites
* Android Studio Ladybug or newer
* JDK 11 or higher
* Android SDK 37

### Build Commands

```bash
# Build Debug APK
./gradlew :app:assembleDebug

# Run Unit Tests
./gradlew :app:testDebugUnitTest

# Run Lint Checks
./gradlew :app:lint
```

---

## License

This project is licensed under the terms of the open-source license included in the [`LICENSE`](LICENSE) file.
