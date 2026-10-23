# PixelPocket — Memories gallery for Android

A photo & video gallery app built with Kotlin and Jetpack Compose.

## Features

- **One-time registration** — name, email and optional mobile number on first launch only.
- **Photos & videos** — asks for media permission, then loads everything on the device with a loading indicator.
- **Sorting** — date (newest first is the default every time the app opens), date oldest first, name A–Z / Z–A, size largest / smallest. Date sorts group items under day headers.
- **Filters** — All / Photos / Videos.
- **Crop** — free, original, 1:1, 4:3, 3:4, 16:9, 9:16, rotate and flip. Saved as a new copy using your preferences (format JPEG/PNG/WEBP, quality, album), with an option to remember them as defaults (also editable in Settings).
- **Select** — long-press to select, tap to add more, *Select all*, or *Select day*.
- **Move** — single or multiple items to an existing album or a new one.
- **Delete** — single or multiple items (Android shows its own confirmation on 11+).
- **Share** — one or many photos/videos to any app via the Android share sheet.
- **Viewer** — swipe between items, pinch / double-tap to zoom, play videos, view details.

## Download

Every push builds the APK with GitHub Actions and attaches `PixelPocket.apk` to a new
[GitHub Release](../../releases). Download it on your phone and install it
(allow "Install unknown apps" when prompted). Requires Android 8.0 or newer.

## Build locally

```bash
./gradlew assembleRelease
# app/build/outputs/apk/release/app-release.apk
```

The release build is signed with the bundled `keystore/pixelpocket.jks`
(password `pixelpocket`) so the APK installs directly. Replace it with your own
private keystore before publishing to the Play Store.
