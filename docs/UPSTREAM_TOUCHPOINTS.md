# Upstream Touchpoints Ledger

This document tracks all files touched or introduced in `feature/no-repeat` relative to `upstream/master`, providing rationale, isolation boundaries, and merge conflict analysis to ensure smooth upstream synchronization.

## 1. Architectural Strategy
Changes are designed with minimal invasive footprint:
- **Additive by default**: New capabilities (no-repeat policy, changelog, theme overrides, icon tools) reside in dedicated files or isolated packages (`io.ymusic.personal`, `io.ymusic.player.repeat`, `tools/`).
- **Single Hook Points**:
  - The entire color theme is hooked via a single line at `App.kt:310` (`YMusicTheme(io.ymusic.personal.personalDesignPalette(palette))`). `Theme.kt` remains completely untouched.
  - The no-repeat queue filter hooks into `Queue.kt` and `PlayerImpl.kt` via clean delegate interfaces.
- **Upstream String Preservation**: No upstream string resources are modified or deleted; new personal strings are appended across all `values-*/strings.xml` locales.

---

## 2. Touchpoint Directory & Ledger

### `:commonUi`
| File | Type | Rationale |
| :--- | :--- | :--- |
| `commonUi/.../io/ymusic/personal/PersonalTheme.kt` | NEW | Royal Blue palette definitions (Light, Dark, Amoled) with WCAG 2.1 AA compliance. |
| `commonUi/.../io/ymusic/ui/App.kt` | MODIFIED | Single-line root theme hook; wires changelog dialog and personal palette. |
| `commonUi/.../io/ymusic/ui/Drawer.kt` | MODIFIED | Points premium chip badge to `DesignColors.BrandGreen` with theme alpha tinting. |
| `commonUi/.../io/ymusic/ui/AppLinks.kt` | MODIFIED | Hardcodes personal website (`t.me/ymusic_personal`) and Telegram support link. |
| `commonUi/.../io/ymusic/ui/Feedback.kt` | MODIFIED | Replaces email intent with direct Telegram contact opening. |
| `commonUi/.../io/ymusic/ui/FeedbackScreen.kt` | MODIFIED | Hides obsolete email feedback row. |
| `commonUi/.../io/ymusic/ui/SettingsScreen.kt` | MODIFIED | Embeds No-Repeat settings section, changelog trigger, hides unapproved terms row. |
| `commonUi/.../io/ymusic/ui/NoRepeatControl.kt` | NEW | UI chip and settings controls for the no-repeat window. |
| `commonUi/.../io/ymusic/ui/Changelog.kt` | NEW | In-app changelog dialog, parser, and version tracking logic. |
| `commonUi/.../composeResources/files/changelog.json` | NEW | Bundled changelog database with release notes. |
| `commonUi/.../values-*/strings.xml` | MODIFIED | Localized string definitions for no-repeat, changelog, and personal links. |

### `:player`
| File | Type | Rationale |
| :--- | :--- | :--- |
| `player/.../io/ymusic/player/repeat/RepeatPolicy.kt` | NEW | Core anti-spam repeat policy engine interface and decay algorithms. |
| `player/.../io/ymusic/player/repeat/RepeatPolicyImpl.kt` | NEW | In-memory implementation of no-repeat window and fatigue decay. |
| `player/.../io/ymusic/player/repeat/RepeatConfig.kt` | NEW | Window durations and user preferences. |
| `player/.../io/ymusic/player/repeat/RepeatEvent.kt` | NEW | Playback event models for repeat history tracking. |
| `player/.../io/ymusic/player/PlayerImpl.kt` | MODIFIED | Integrates repeat policy into track completion and skip handling. |
| `player/.../io/ymusic/player/Queue.kt` | MODIFIED | Applies repeat filtering during queue population. |
| `player/.../io/ymusic/player/RepeatPolicyTest.kt` | NEW | Unit test suite for repeat policy behavior. |

### `:androidApp`
| File | Type | Rationale |
| :--- | :--- | :--- |
| `androidApp/src/main/AndroidManifest.xml` | MODIFIED | Removed `ymusic.io` deep link intent filter; icon and theme mappings. |
| `androidApp/src/main/kotlin/.../PlayerWidgets.kt` | MODIFIED | Replaced brown/peach fallback colors with dark blue / royal blue theme colors. |
| `androidApp/src/main/kotlin/.../YMusicApplication.kt` | MODIFIED | Initialized personal state and notification channels. |
| `androidApp/src/main/res/values/colors.xml` | MODIFIED | Updated `app_surface` (`#FFF8FAFC`) and `app_surface_dark` (`#FF0F172A`). |
| `androidApp/src/main/res/values-night/colors.xml` | MODIFIED | Updated `app_surface` (`#FF0F172A`). |
| `androidApp/src/main/res/values-v31/themes.xml` | MODIFIED | Splash screen background mapped to `@color/app_surface`. |
| `androidApp/src/main/res/mipmap-*` | MODIFIED/NEW | New royal blue adaptive icon assets. |
| `androidApp/src/main/res/drawable/` | NEW | `ic_launcher_monochrome.xml`, `splash_icon.xml`, `ic_notification.xml`. |

### `:desktopApp` & `:iosApp`
| File | Type | Rationale |
| :--- | :--- | :--- |
| `desktopApp/.../WindowGround.kt` | MODIFIED | Windows/Linux/macOS window ground synced to `PersonalDarkPalette.Surface`. |
| `desktopApp/ymusic.*` | MODIFIED | Desktop application icons generated from royal blue source. |
| `iosApp/.../AppIcon.appiconset` | MODIFIED | iOS application icons generated from royal blue source. |

### `tools/`
| File | Type | Rationale |
| :--- | :--- | :--- |
| `tools/generate_icons.py` | NEW | Automated idempotent multi-platform asset generation script. |
| `tools/check_resources.py` | NEW | Standalone Android XML resource and reference sanity checker. |
| `tools/check_contrast.py` | NEW | WCAG 2.1 AA luminance and contrast ratio verification tool. |

---

## 3. Upstream Overlap & Risk Analysis
Analysis of modified files against upstream change frequency in its last 200 commits (`git log upstream/master -200 --name-only`):
- Total files modified in personal build: 137
- Total files with upstream touches: 13
- Total files with zero upstream touches: 124

### Overlapping Files Ranked by Upstream Change Frequency
| File | Upstream Commits (Last 200) | Personal Modification | Merge Risk Level |
| :--- | :---: | :--- | :--- |
| `androidApp/src/main/kotlin/io/ymusic/android/YMusicApplication.kt` | 18 | App initialization, notification channel setup | High |
| `.github/workflows/release.yml` | 12 | Release workflow triggers and secrets | High |
| `androidApp/build.gradle.kts` | 4 | Dependency configuration, packaging | Medium |
| `player/src/commonMain/kotlin/io/ymusic/player/PlayerImpl.kt` | 4 | No-repeat queue hook & completion callback | Medium |
| `error-codes.txt` | 2 | Ledger error code allocation | Low |
| `commonUi/src/commonMain/kotlin/io/ymusic/ui/NowPlaying.kt` | 2 | Rest this song menu item, overflow action | Medium |
| `commonUi/src/commonMain/kotlin/io/ymusic/ui/AppLinks.kt` | 1 | Hardcoded personal links and support | Low |
| `commonUi/src/commonMain/kotlin/io/ymusic/ui/SettingsScreen.kt` | 1 | No-repeat section and changelog action | Low |
| `commonUi/src/commonMain/kotlin/io/ymusic/ui/UpdateDialog.kt` | 1 | Custom release link and text styling | Low |
| `commonUi/src/commonMain/kotlin/io/ymusic/ui/App.kt` | 1 | Single-line theme hook & dialog wiring | Low |
| `player/src/commonMain/kotlin/io/ymusic/player/Player.kt` | 1 | RepeatPolicy interface wiring | Low |
| `player/src/commonMain/kotlin/io/ymusic/player/Queue.kt` | 1 | Queue population filter delegate | Low |
| `desktopApp/ymusic.png` | 1 | Binary desktop icon asset | Low |

### Top 5 Riskiest Files for Future Upstream Merges
1. **`androidApp/src/main/kotlin/io/ymusic/android/YMusicApplication.kt`** (18 touches): High-activity upstream file handling core application lifecycle and services. Changes should remain strictly appended to lifecycle callbacks.
2. **`.github/workflows/release.yml`** (12 touches): Active CI/CD workflow where upstream updates action versions and runners.
3. **`androidApp/build.gradle.kts`** (4 touches): Build script subject to upstream dependency bumps and AGP configuration updates.
4. **`player/src/commonMain/kotlin/io/ymusic/player/PlayerImpl.kt`** (4 touches): Core playback engine state machine; personal repeat policy hooks must remain narrow and delegative.
5. **`commonUi/src/commonMain/kotlin/io/ymusic/ui/NowPlaying.kt`** (2 touches): Substantial UI composable where menu actions and gestures frequently evolve upstream.

