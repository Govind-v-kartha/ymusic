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

## 3. Merge Conflict Analysis with `upstream/master`
Trial merge simulation against `upstream/master` results:
- **Clean automatic merge**: All isolated modules (`PersonalTheme.kt`, `NoRepeatControl.kt`, `Changelog.kt`, icon generation tools) merge with zero conflicts.
- **Expected Touchpoint Merges**:
  - `commonUi/src/commonMain/kotlin/io/ymusic/ui/App.kt`: Clean 1-line hook merge.
  - `commonUi/.../values-*/strings.xml`: Git's three-way merge cleanly appends the new keys at the end of `<resources>`.
  - `AndroidManifest.xml`: Clean deletion of personal deep links.

