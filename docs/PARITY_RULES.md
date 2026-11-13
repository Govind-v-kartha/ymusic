# Cross-Platform Parity Rules

## Core Rule
Whenever a feature or behavior changes on mobile or desktop, it must work on both.
All target platforms for any release are built from the exact same Git commit.

## Principles
1. **Shared Code First**: App logic, models, playback logic, formatters, and settings live in shared KMP modules (`:projectCommon`, `:settings`, `:player`, `:commonUi`). Shell modules (`:androidApp`, `:desktopApp`, `:iosAppKotlin`) only wire system integrations and platform roots.
2. **Strict Platform Gating**: When layout or interaction idioms diverge (e.g. 72dp desktop navigation rail vs mobile bottom navigation, persistent Now Playing side panel vs bottom sheet), behavior must be gated behind `isDesktopPlatform()` (`!platformUsesTouch`). Android and iOS touch layouts must remain identical to their baseline.
3. **Settings Parity**: User settings exist across all platforms. Platform-specific defaults are determined via platform descriptors or shared hooks, with UI toggles displayed consistently across platforms.
4. **CI Enforcement**: Pre-merge CI (`ci-check.yml`) enforces Android compilation, desktop compilation, shared unit tests, and layout parity checks.

## Playback Behavior Rules
1. **Smart Previous Button Behavior**:
   - *Restart threshold*: If the current track has played for longer than 3 seconds (`SMART_PREV_THRESHOLD_MS = 3_000L` plus any sponsor/intro skip segment), pressing "previous" restarts the current track from the beginning (or intro skip offset).
   - *History navigation*: A second press while within the first 3 seconds navigates back to the preceding song in the play history stack (`QueueController.playHistory`).
   - *Shared implementation*: This logic resides in `PlayerImpl.skipPrev()` and `QueueController.previous()`, ensuring identical behavior across Android, Desktop, and iOS.
2. **Per-Device Repeat History**:
   - The anti-repetition policy (`RepeatPolicyImpl`) records play events, rotation penalties, and rested tracks in local SQLite storage.
   - Repeat history is strictly per-device. Playback events and active rests are not synchronized across devices, preventing cross-device interference and respecting individual listening sessions.

## Intentional Platform Differences
1. **Auto-Resume on App Launch**:
   - *Desktop*: Users frequently open and close music players during multitasking. The `resumeOnLaunch` setting (default `false`) controls whether opening the application restores the previous queue and playback session from history.
   - *Android & iOS*: Mobile operating systems do not restore audio playback simply by opening the application UI. Audio playback resumption is triggered by system media callbacks (such as Android Auto connection, headphone button press, or lockscreen widget tap via `PlaybackService`), not cold UI app launch. The `resumeOnLaunch` and `launchAtLogin` settings toggles are intentionally hidden on mobile platforms in `SettingsScreen.kt`.
2. **Navigation and Player Layout**:
   - *Desktop*: Uses a 72dp persistent left navigation rail and a 320dp right Now Playing panel alongside the content pane.
   - *Mobile*: Uses standard mobile bottom navigation bar and full-screen / bottom-sheet Now Playing player.
3. **Local File Ingestion**:
   - *Desktop*: Supports direct drag-and-drop of audio files and CLI arguments parsed via `DesktopFileValidator`.
   - *Mobile*: Ingests local files via the Storage Access Framework (SAF) document tree picker.
