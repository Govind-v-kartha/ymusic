# iOS Personal Release Guide

This document explains options for building, signing, and deploying your personal YMusic build to an iOS device.

## Prerequisites
- macOS host with Xcode installed. (Windows and Linux machines cannot compile iOS binaries or produce IPAs because Apple's toolchain and SDKs are macOS-only).
- The shared KMP logic in `:commonUi` and `:player` compiles directly into an iOS framework (`Player.framework` and `commonUi.framework`) on macOS.

---

## Option 1: Sideloading via AltStore / SideStore (Free Apple ID)

Ideal for personal testing without an Apple Developer subscription (\$99/year).

### Constraints
- Certificates expire every **7 days**.
- Apps must be refreshed over local Wi-Fi or via AltServer/WireGuard VPN within 7 days.
- Maximum 3 active sideloaded apps per free Apple ID account.

### Workflow
1. Open the repository on a Mac.
2. Build the shared framework:
   ```bash
   ./gradlew :commonUi:linkReleaseFrameworkIosArm64
   ```
3. Open `iosApp/iosApp.xcworkspace` in Xcode.
4. In Xcode project settings under **Signing & Capabilities**:
   - Change Bundle Identifier to your unique identifier (e.g. `com.yourname.ymusic`).
   - Select your personal Apple ID team.
5. In Xcode: **Product > Archive**.
6. Export the `.ipa` using "Development" or "Ad Hoc" distribution.
7. Install via **AltStore** or **SideStore**:
   - In AltStore: tap `+` on the "My Apps" tab and select the generated `.ipa`.

---

## Option 2: TestFlight / Ad-Hoc (Paid Apple Developer Account)

Ideal for seamless continuous installation without weekly re-signing.

### Constraints
- Requires paid Apple Developer Program enrollment (\$99/year).

### Workflow
1. In Xcode **Signing & Capabilities**, select your paid Developer Team.
2. Select target device "Any iOS Device (arm64)".
3. In Xcode: **Product > Archive**.
4. In Organizer window, click **Distribute App** > **TestFlight & App Store** (or "Ad Hoc" for direct provisioning profile installation via Apple Configurator / Xcode).
5. Build is uploaded to App Store Connect and becomes installable via TestFlight (valid for 90 days per build, updates automatically).
