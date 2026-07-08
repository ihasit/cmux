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
        MobileAuthStore.kt
        MobileRouteAuthPolicy.kt
        MobileRpcSession.kt
        MobileWebSocketClient.kt
        MobileTcpClient.kt
        MobileWebBridge.kt
        PairedMac.kt
        PairedMacStore.kt
        PairingParser.kt
      assets/mobile/
        index.html
        app.js
        styles.css
```

## Current status

Implemented:

- Parse `cmux-ios://attach?v=2&r=host:port` pairing links.
- Parse older `attach` / `pair` payload links enough to recover host/port routes.
- Persist paired Macs with Android Keystore-backed encrypted storage.
- Connect to the selected host route over the length-prefixed mobile TCP protocol.
- Connect to WebSocket attach routes when a pairing payload advertises one.
- Call `mobile.host.status`, `mobile.workspace.list`, `mobile.terminal.replay`, `mobile.terminal.input`, `mobile.terminal.paste`, and `mobile.terminal.create`.
- Subscribe to `workspace.updated`, `terminal.render_grid`, `notification.badge`, and `notification.dismissed` host events for live refresh.
- Render workspace rows, groups, and styled render-grid terminal output in the WebView.
- Forward terminal scrolling, taps/clicks, text paste, and image paste to the Mac.
- Sync Mac notification badge state and reconcile/dismiss delivered notification ids.
- Save a manually pasted Stack access token in Android Keystore-backed encrypted storage and attach it to mobile RPC requests.
- Send Stack access tokens only over trusted routes: Tailscale CGNAT/MagicDNS, debug loopback, or `wss://` WebSocket routes.
- Handle `cmux-ios://` / `cmux-ios-dev://` Android deep links.
- Scan Mac pairing QR codes with the device camera.
- Store paired Mac routes encrypted with Android Keystore AES-GCM, migrating older plaintext records on read.
- Run JVM unit tests for pairing URL parsing, WebSocket route parsing, route JSON round-trips, route auth policy, and mobile RPC auth envelopes.
- Provide English and Japanese WebView strings.

Not implemented yet:

- Integrated Stack Auth sign-in. The current client supports manual Stack access token entry only.
- Android instrumentation tests.

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

On machines where Homebrew Gradle runs on a very new JDK, prefer JDK 17 for the
Android Gradle Plugin:

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home gradle :app:assembleDebug
```

Run the JVM unit tests with:

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home gradle :app:testDebugUnitTest
```

If the repository later adds a Gradle wrapper, prefer:

```bash
./gradlew :app:assembleDebug
```

## MVP checklist

- Parse `cmux-ios://` / `cmux-ios-dev://` pairing URLs. Done for attach links.
- Connect to a Tailscale `host:port` route. Done for TCP routes.
- Call `mobile.host.status`. Done.
- Persist paired Mac routes in encrypted Android storage. Done with Android Keystore AES-GCM.
- Attach Stack access tokens to authorized RPC requests. Done for manually saved tokens.
- Gate Stack token transport to trusted routes. Done for Tailscale CGNAT/MagicDNS, debug loopback, and `wss://` WebSocket routes.
- Render terminal output in the WebView. Done for styled render-grid frames.
- Add QR scanning with CameraX or a small native scanner module. Done with ZXing embedded scanner.
- Add WebSocket transport once the Mac side advertises `.websocket` routes. Done for compact/full attach payloads.
