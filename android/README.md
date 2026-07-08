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
        MobileNotificationBridge.kt
        MobileRouteAuthPolicy.kt
        MobileRpcSession.kt
        MobileStackTokenProvider.kt
        MobileWebSocketClient.kt
        MobileTcpClient.kt
        MobileWebBridge.kt
        PairedMac.kt
        PairedMacStore.kt
        PairingParser.kt
      test/java/com/cmux/android/
        MobileNotificationBridgeTest.kt
        MobileTcpClientTest.kt
        MobileWebSocketClientTest.kt
        StackTokenRefresherTest.kt
      androidTest/java/com/cmux/android/
        MainActivitySmokeTest.kt
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
- Connect to host routes over the length-prefixed mobile TCP protocol, automatically trying the next advertised route if the first route fails before opening.
- Connect to WebSocket attach routes when a pairing payload advertises one.
- Call `mobile.host.status`, `mobile.workspace.list`, `mobile.terminal.replay`, `mobile.terminal.input`, `mobile.terminal.paste`, and `mobile.terminal.create`.
- Subscribe to `workspace.updated`, `terminal.render_grid`, `notification.badge`, and `notification.dismissed` host events for live refresh.
- Render workspace rows, groups, and styled render-grid terminal output in the WebView.
- Forward terminal scrolling, taps/clicks, text paste, and image paste to the Mac.
- Provide terminal quick keys for Enter, Tab, Esc, and Ctrl-C in the WebView terminal.
- Disable workspace and terminal controls while disconnected, while keeping paired Mac reconnect actions available.
- Sync Mac notification badge state and reconcile/dismiss delivered notification ids.
- Show a native Android summary notification for unread agent notifications when notification permission is available.
- Request the Android 13+ notification permission from the WebView shell when alerts can be enabled.
- Save a manually pasted Stack access token in Android Keystore-backed encrypted storage and attach it to mobile RPC requests.
- Launch hosted Stack Auth sign-in and accept `cmux-ios://auth-callback` token handoff deep links.
- Persist Stack refresh/access token handoffs encrypted with Android Keystore AES-GCM.
- Automatically refresh Stack access tokens from the stored refresh token before authorized RPCs, and retry once after host authorization rejection.
- Send Stack access tokens only over trusted routes: Tailscale CGNAT/MagicDNS, debug loopback, or `wss://` WebSocket routes.
- Handle `cmux-ios://` / `cmux-ios-dev://` Android deep links.
- Reject non-debug loopback host routes from attach and legacy pairing payloads so Android does not dial itself.
- Scan Mac pairing QR codes with the device camera.
- Store paired Mac routes encrypted with Android Keystore AES-GCM, migrating older plaintext records on read.
- Fail pending mobile RPCs with explicit transport errors when the connection closes.
- Run JVM unit tests for auth callback parsing, pairing URL parsing, WebSocket route parsing, route JSON round-trips, route auth policy, mobile TCP/WebSocket framing, and mobile RPC auth envelopes.
- Provide Android instrumentation coverage for the launched WebView shell, Stack token save/clear bridge, attach deep links, workspace list rendering, and terminal render-grid updates.
- Provide English and Japanese WebView strings.

Not verified yet:

- Running Android instrumentation tests on a connected emulator/device in this environment.
- Full end-to-end validation against a live Mac cmux mobile host.

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

Build the Android instrumentation test APK with:

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home gradle :app:assembleDebugAndroidTest
```

Run instrumentation tests on a connected emulator/device with:

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home gradle :app:connectedDebugAndroidTest
```

If the repository later adds a Gradle wrapper, prefer:

```bash
./gradlew :app:assembleDebug
```

The hosted sign-in origin and Stack project used for token refresh can be overridden
from `gradle.properties` or `-P` flags:

```properties
cmuxAuthOrigin=https://cmux.com
cmuxStackBaseUrl=https://api.stack-auth.com
cmuxStackProjectId=9790718f-14cd-4f7e-824d-eaf527a82b82
cmuxStackPublishableClientKey=pck_kzj80gx4mh2jrzn1cx6y5e8jk0kwa01vkevh2p9zd4twr
```

## MVP checklist

- Parse `cmux-ios://` / `cmux-ios-dev://` pairing URLs. Done for attach links.
- Connect to a Tailscale `host:port` route. Done for TCP routes.
- Call `mobile.host.status`. Done.
- Persist paired Mac routes in encrypted Android storage. Done with Android Keystore AES-GCM.
- Attach Stack access tokens to authorized RPC requests. Done for signed-in or manually saved tokens.
- Accept hosted Stack Auth token handoff callbacks. Done for `cmux-ios://auth-callback`.
- Refresh Stack access tokens automatically from stored refresh tokens. Done for proactive refresh and one retry after authorization rejection.
- Gate Stack token transport to trusted routes. Done for Tailscale CGNAT/MagicDNS, debug loopback, and `wss://` WebSocket routes.
- Render terminal output in the WebView. Done for styled render-grid frames.
- Add QR scanning with CameraX or a small native scanner module. Done with ZXing embedded scanner.
- Add WebSocket transport once the Mac side advertises `.websocket` routes. Done for compact/full attach payloads.
