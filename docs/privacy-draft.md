# Privacy Policy (Draft)

> **Status:** Draft for Personal Build Review. Not wired into app UI.

---

## 1. Introduction & Contact
This Privacy Policy describes how this personal build of YMusic handles information on your device.
- **Operator:** [PENDING USER INPUT: Who operates the app and display name]
- **Contact:** Telegram [https://t.me/SethuramaIyer_CBI](https://t.me/SethuramaIyer_CBI)
- **Effective Date:** [PENDING USER INPUT: Effective date]

---

## 2. On-Device Data Storage
The application stores all user data locally on your device:
1. **Media Library & Playlists (`media.db`):** User playlists, saved songs, document scan indices, and local file metadata.
2. **Playback History (`play_history.db`):** Local play events, repeat fatigue records, and rest intervals. Users can clear this at any time in Settings › Privacy › "Clear heard history".
3. **Learned Artist Language & Autoplay Mix:** Local channel language observation counts and session mix context used by the autoplay mix policy. Kept entirely on device in memory, cleared when using "Clear heard history", and never transmitted.
4. **Search History:** Query terms stored in private settings. Users can clear this at any time in Settings › Privacy › "Clear search history".
5. **Application Logs (`logs.db`):** Rolling internal diagnostic log buffer (up to 5,000 lines). Logs remain on-device and are never transmitted automatically.
6. **Cache Directories:** Audio and video chunks (`DiskMediaBlobCache`), image thumbnails (`image-source-cache`), and album artwork (`image-cover-cache`) stored in private application cache storage.

---

## 3. Network Communications & Data Flow
All streaming, search, and download traffic is established directly between your device and external service providers:
1. **YouTube / Google Services:**
   - Search queries, video streaming chunks, metadata lookups, and channel information are requested directly from Google servers (`googlevideo.com`, `youtubei/v1`).
   - If signed in via WebView, authentication cookies (`SID`, `HSID`, `SSID`, `LOGIN_INFO`) are stored in the local secure cookie jar and sent only to Google domains to maintain your session.
2. **SponsorBlock API:**
   - If enabled in Settings, the app queries `sponsor.ajay.app` with the video identifier to fetch skip timestamps.
3. **In-App Updates (GitHub):**
   - The update check contacts github.com only when the user taps the button, and GitHub can see the IP address.
4. **Telemetry & Crash Reports:**
   - Opt-in error reporting via Sentry (if configured at build time). No analytics or advertising SDKs are included.
   - When submitting feedback manually, logs are packaged into an anonymous report only with explicit user consent.

---

## 4. Declared Device Permissions
- **Android:**
  - `INTERNET` & `ACCESS_NETWORK_STATE`: Stream media and fetch metadata.
  - `FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_MEDIA_PLAYBACK`: Keep playback active when screen is locked or app is backgrounded.
  - `FOREGROUND_SERVICE_DATA_SYNC`: Manage offline download tasks.
  - `POST_NOTIFICATIONS`: Display media player controls and download progress.
  - `WAKE_LOCK`: Prevent CPU sleep during active audio playback.
  - `REQUEST_INSTALL_PACKAGES`: Optional package installation.
- **iOS:**
  - `UIBackgroundModes` (`audio`): Maintain background audio playback.

---

## 5. User Control & Data Deletion
Users retain full control of their data. Uninstalling the application deletes all databases, settings, and cache files. Built-in settings options allow granular clearing of search history, heard history, and caches at any time.

---

## Open Questions for Operator Approval
1. Should Sentry error reporting be permanently stripped or remain conditional on build flags?
2. Are anonymous feedback reports supported via Telegram direct contact instead of Sentry?
3. What specific jurisdiction policies (e.g. GDPR, CCPA) must be explicitly cited?
