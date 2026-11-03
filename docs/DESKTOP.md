# Desktop Builds (Windows & Ubuntu)

Personal desktop builds for Windows and Ubuntu (Linux x64) built by GitHub Actions and published alongside the personal Android APK on GitHub Releases.

---

## 1. Windows Installation & Usage

### Downloads Available
1. **Installer (`YMusic-<tag>-windows-x64-setup.exe`)**:
   - Single-file installer built with Inno Setup (LZMA2 compression).
   - Installs per-machine into `C:\Program Files\YMusic\`.
   - Creates a Start Menu shortcut and registers an entry in Windows Settings > Apps ("Installed apps").
2. **Portable ZIP (`YMusic-<tag>-windows-x64.zip`)**:
   - Zero-install portable bundle.
   - Extract anywhere (e.g. `C:\Tools\YMusic\` or a portable drive) and run `YMusic.exe`.

### Windows Defender SmartScreen
Because personal builds are not signed with an expensive commercial EV Code Signing certificate, Windows SmartScreen will display:
> *"Windows protected your PC"* / *"Unknown Publisher"*

**To run:**
1. Click **More info**.
2. Click **Run anyway**.

### Data Storage
- Settings, local caches, and playback history are stored in your user profile:
  - `%APPDATA%\YMusic\` (or `%LOCALAPPDATA%\YMusic\`)
  - SQLite database for playback history: `play-history.db`
  - Preferences: `settings.properties`

### Uninstalling
- **Installer**: Open Windows Settings > **Installed Apps** > search for **YMusic** > select **Uninstall**. Or run `unins000.exe` in `C:\Program Files\YMusic\`.
- **Portable ZIP**: Delete the extracted folder.

---

## 2. Ubuntu / Linux (x64) Installation & Usage

### Supported Ubuntu Versions
- **Ubuntu 22.04 LTS (Jammy Jellyfish)** (GLIBC 2.35+)
- **Ubuntu 24.04 LTS (Noble Numbat)** (GLIBC 2.39+)
- Built on `ubuntu-22.04` to maintain backward GLIBC compatibility across both LTS releases.

### Downloads Available
1. **Debian Package (`YMusic-<tag>-linux-x64.deb`)**:
   - Package name: `ymusic-personal` (installed into `/opt/ymusic-personal/`).
   - Installs system launcher icon and menu item under *Sound & Video* (`Categories=AudioVideo;Audio;Player;`).
   - Does not conflict with upstream `ymusic` installations.
2. **Portable Archive (`YMusic-<tag>-linux-x64.tar.gz`)**:
   - Extract and run directly without root permissions:
     ```bash
     tar -xzf YMusic-<tag>-linux-x64.tar.gz
     ./ymusic-personal/bin/ymusic-personal
     ```

### Installing the .deb
```bash
sudo apt update
sudo apt install ./YMusic-<tag>-linux-x64.deb
```
*(Or via dpkg: `sudo dpkg -i YMusic-<tag>-linux-x64.deb && sudo apt-get install -f -y`)*

### Launcher Entry
- Search for **YMusic** in the GNOME Dash / Application menu.
- Window and taskbar matching uses `StartupWMClass=io-ymusic-desktop-MainKt`.

### Data Storage
- Stored under XDG directories:
  - `~/.local/share/ymusic-personal/` (or `~/.config/YMusic/`)
  - SQLite databases: `play-history.db`, `app.db`, `logs.db`

### Uninstalling
```bash
sudo apt remove ymusic-personal
```

---

## 3. Updates

- **Current Behavior**:
  - In-app automatic downloading and silent installation are Android-only.
  - On Desktop, clicking **Check for updates** in Settings or the navigation drawer opens:
    `https://github.com/Govind-v-kartha/ymusic/releases`
  - To update, download the newer `.exe` / `.deb` / `.zip` / `.tar.gz` and run or install it over the previous version. The installer GUID and package name are persistent, so upgrades replace the existing installation in place while preserving user data.

---

## 4. Known Limits & Intentional Differences

1. **Unsigned Installers**:
   - Installers are unsigned community builds. SmartScreen warnings appear on Windows; Ubuntu requires local package installation approval.
2. **Local Storage**:
   - Settings, cached media, and play history remain strictly local to each machine. There is no cloud account sync between Android and Desktop.
3. **Audio Output Backend**:
   - Linux uses PulseAudio / ALSA (`libpulse-dev` runtime linking with fallback).
   - Windows uses WASAPI via standard Java/Compose AWT audio pipelines and bundled ffmpeg libraries.

---

## 5. Running a Test Build in CI

To test desktop compilation and artifact packaging without publishing a public GitHub Release:
1. Create and push a tag starting with `desktop-test-`:
   ```bash
   git tag desktop-test-1
   git push origin desktop-test-1
   ```
2. Or trigger the `release-personal-desktop` workflow manually in the GitHub Actions tab via **Run workflow** (`workflow_dispatch`).
3. The workflow builds Windows and Linux targets and uploads the 4 binaries and `SHA256SUMS-desktop.txt` as GitHub Actions workflow artifacts (retained for 14 days), with zero interaction with GitHub Releases.
