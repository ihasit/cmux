# cmux Android client

This directory is an isolated Android client workspace for Mobile Connect.

The first milestone is a hybrid client:

- Android native shell for permissions, secure storage, QR scanning, and network transport.
- WebView UI for pairing, workspace lists, and the terminal surface.
- A native bridge that speaks the existing cmux mobile TCP framing protocol.

It is intentionally not wired into the macOS Xcode project or the iOS packages.

## Current shape

```text
android/
  settings.gradle.kts
  build.gradle.kts
  gradle.properties
  app/
    build.gradle.kts
    src/main/
      AndroidManifest.xml
      java/com/cmux/android/
        MainActivity.kt
        MobileTcpClient.kt
        MobileWebBridge.kt
      assets/mobile/
        index.html
        app.js
        styles.css
```

## Protocol target

The native transport should match the existing Swift protocol in:

- `Packages/Shared/CMUXMobileCore/Sources/CMUXMobileCore/CmxTransport.swift`
- `Packages/Shared/CMUXMobileCore/Sources/CMUXMobileCore/MobileSyncProtocol.swift`
- `Sources/Mobile/MobileHostRPC.swift`

The TCP stream uses a 4-byte big-endian frame length followed by a JSON payload.

## Build

Install Android Studio or an Android SDK, then make sure Gradle can find the SDK.
Android Studio's setup wizard normally installs it at:

```bash
~/Library/Android/sdk
```

If Gradle cannot find it, create a local `local.properties` file:

```properties
sdk.dir=/Users/<you>/Library/Android/sdk
```

Then from this directory run:

```bash
gradle :app:assembleDebug
```

If the repository later adds a Gradle wrapper, prefer:

```bash
./gradlew :app:assembleDebug
```

## MVP checklist

- Parse `cmux-ios://` / `cmux-ios-dev://` pairing URLs.
- Connect to a Tailscale `host:port` route.
- Call `mobile.host.status`.
- Persist paired Mac routes in encrypted Android storage.
- Render terminal output in the WebView.
- Add QR scanning with CameraX or a small native scanner module.
- Add WebSocket transport once the Mac side advertises `.websocket` routes.
