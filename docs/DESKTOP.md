# Desktop Builds (Windows & Ubuntu)

Personal desktop builds for Windows and Ubuntu (Linux x64) built by GitHub Actions and published alongside the personal Android APK on GitHub Releases.

---

## 1. Features & System Integration

### System Media Transport Controls (SMTC) & MPRIS
- **Windows (SMTC)**: Native `ISystemMediaTransportControls` integration via WinRT COM / JNA.
  - Windows Quick Settings (Win+A) and lock screen media card display current track title, artist, and album art.
  - Transport buttons: Play, Pause, Next Track, Previous Track, and Stop.
  - Timeline seeking support via `SetPlaybackPosition`.
- **Linux (MPRIS2)**: D-Bus service registered under `org.mpris.MediaPlayer2.ymusic`.
  - Exposes `org.mpris.MediaPlayer2` and `org.mpris.MediaPlayer2.Player` interfaces.
  - Interacts with GNOME Shell, KDE Plasma, media control applets, and playerctl.
  - Emits `PropertiesChanged` signals for `PlaybackStatus`, `Metadata`, and `CanSeek`.

### Global Media Keys & Hotkeys
- Global hardware media keys (`VK_MEDIA_PLAY_PAUSE`, `VK_MEDIA_NEXT_TRACK`, `VK_MEDIA_PREV_TRACK`, `VK_MEDIA_STOP` on Windows; `XF86AudioPlay`, `XF86AudioNext`, `XF86AudioPrev`, `XF86AudioStop` on Linux X11) are captured even when YMusic is minimized or running in the background.
- Global window visibility toggle hotkey raises or minimizes YMusic to the system tray.

### Desktop UI Layout
- **Left Navigation Rail (72dp)**: Replaces the mobile slide-in modal drawer and bottom navigation bar with a persistent desktop rail featuring Discover, Library, YT Music, Play Again, Downloads, and Settings.
- **Persistent Now Playing Side Panel (340dp)**: Automatically expands on the right side of the screen when music is playing, leaving the main browsing feed visible. Includes large album art, interactive scrub seek bar, transport controls, and current queue with direct track selection.
- **Compact List Rows (48dp)**: Track and library item rows adapt from touch-friendly 72dp to dense 48dp on desktop viewports, with 40dp artwork thumbnails and compact vertical padding.
- **Auto-Fit Dialogs**: Dialogs such as "What's New" and "Settings" are clamped to max width (480dp) on 1080p+ widescreen monitors.

### File Associations, Drag & Drop, and CLI
- **Drag & Drop**: Dragging audio or video files (`.mp3`, `.m4a`, `.flac`, `.opus`, `.ogg`, `.wav`, `.mp4`, `.webm`) directly onto the YMusic window immediately enqueues and plays them.
- **CLI Arguments**: Launching `ymusic <filepath>` from the command line opens and plays the target local audio file.
- **OS File Associations**:
  - Windows Inno Setup installer associates `.mp3`, `.m4a`, `.flac`, `.opus`, `.ogg`, `.wav`, `.mp4`, `.webm` with YMusic.
  - Linux Debian package registers `MimeType` associations in `/usr/share/applications/ymusic-personal.desktop`.

### Download Manager & Taskbar Progress
- **Windows Taskbar Progress**: The Windows taskbar icon displays a live green progress bar (`ITaskbarList3` via `java.awt.Taskbar.setWindowProgressValue`) during active downloads.
- **Download Complete Notifications**: Fires system tray balloon notifications on Windows and native `notify-send` desktop notifications on Linux upon download completion.

### Startup & Playback Options
- **Resume Playback on Launch**: Located in Settings > Playback. Default is **OFF** on desktop (so music does not blare unexpectedly upon opening) and **ON** on mobile devices.
- **Launch at Login**: Located in Settings > Playback. Automatically adds/removes YMusic from Windows Registry (`HKCU\...\Run`) or Linux autostart (`~/.config/autostart/ymusic.desktop`).

---

## 2. Windows Installation & Usage

### Downloads Available
1. **Installer (`YMusic-<tag>-windows-x64-setup.exe`)**:
   - Single-file installer built with Inno Setup (LZMA2 compression).
   - Installs per-machine into `C:\Program Files\YMusic\`.
   - Creates Start Menu shortcuts, associates media file types, and registers in Windows Settings > Apps ("Installed apps").
2. **Portable ZIP (`YMusic-<tag>-windows-x64.zip`)**:
   - Zero-install portable bundle.
   - Extract anywhere (e.g. `C:\Tools\YMusic\` or a portable drive) and run `YMusic.exe`.

### Windows Defender SmartScreen
Because personal community builds are not signed with a commercial EV Code Signing certificate, Windows SmartScreen will display:
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

## 3. Ubuntu / Linux (x64) Installation & Usage

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

## 4. Technical Architecture Notes

1. **Remote Config (`YMUSIC_REMOTE_CONFIG_URL`)**:
   - In personal forks, `YMUSIC_REMOTE_CONFIG_URL` is empty. The desktop app gracefully detects the empty endpoint and falls back to bundled `engine.js` and `player.js` assets and local SQLite configuration stores with zero user-facing degradation.
2. **Windows ARM64 Runner Gap**:
   - GitHub Actions currently does not offer free standard Windows ARM64 runners in public repositories. Windows 11 ARM64 natively executes the Windows x64 binary via transparent x64 emulation.
3. **Audio Output Routing**:
   - Playback routes to the default system audio endpoint (WASAPI on Windows, PulseAudio/PipeWire on Linux). Routing playback to specific headphones or DACs is handled seamlessly through the OS Sound Settings / Volume Mixer.
4. **Startup Flash Mitigation**:
   - AWT content pane background is pre-initialized to `0x1B, 0x1B, 0x1B` and window visibility is gated until Compose paints the initial ground color, eliminating blank white frames on startup.
5. **Debian Package Coexistence**:
   - The Linux package is named `ymusic-personal` and installs into `/opt/ymusic-personal/` with binary `ymusic-personal` so that personal builds and upstream official packages can coexist peacefully on the same workstation.
