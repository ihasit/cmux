package com.cmux.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.web.assertion.WebViewAssertions.webMatches
import androidx.test.espresso.web.sugar.Web.onWebView
import androidx.test.espresso.web.webdriver.DriverAtoms.clearElement
import androidx.test.espresso.web.webdriver.DriverAtoms.findElement
import androidx.test.espresso.web.webdriver.DriverAtoms.getText
import androidx.test.espresso.web.webdriver.DriverAtoms.webClick
import androidx.test.espresso.web.webdriver.DriverAtoms.webKeys
import androidx.test.espresso.web.webdriver.Locator
import androidx.test.ext.junit.runners.AndroidJUnit4
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.ByteString
import org.hamcrest.CoreMatchers.containsString
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @Before
    fun clearState() {
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("cmux_mobile", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun launchShowsPairingAndStackAuthControls() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onWebView()
                .withElement(findElement(Locator.ID, "pairingView"))
                .check(webMatches(getText(), containsString("Pair a Mac")))

            onWebView()
                .withElement(findElement(Locator.ID, "authTitle"))
                .check(webMatches(getText(), containsString("Stack Auth token")))

            val notificationButtonState = scenario.evaluateScript(
                """
                JSON.stringify({
                  text: document.getElementById('enableNotifications').textContent,
                  hidden: document.getElementById('enableNotifications').classList.contains('hidden')
                })
                """.trimIndent()
            )
            check(notificationButtonState.contains("\"text\":\"Enable alerts\"")) { notificationButtonState }
        }
    }

    @Test
    fun stackAccessTokenCanBeSavedAndClearedThroughWebViewBridge() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onWebView()
                .withElement(findElement(Locator.ID, "stackAccessToken"))
                .perform(clearElement())
                .perform(webKeys("instrumentation-access-token"))

            onWebView()
                .withElement(findElement(Locator.ID, "saveStackAccessToken"))
                .perform(webClick())

            onWebView()
                .withElement(findElement(Locator.ID, "authStatusText"))
                .check(webMatches(getText(), containsString("Stack access token is configured")))

            onWebView()
                .withElement(findElement(Locator.ID, "toast"))
                .check(webMatches(getText(), containsString("Stack access token saved")))

            onWebView()
                .withElement(findElement(Locator.ID, "clearStackAccessToken"))
                .perform(webClick())

            onWebView()
                .withElement(findElement(Locator.ID, "authStatusText"))
                .check(webMatches(getText(), containsString("Add a Stack access token")))

            onWebView()
                .withElement(findElement(Locator.ID, "toast"))
                .check(webMatches(getText(), containsString("Stack access token cleared")))
        }
    }

    @Test
    fun authCallbackIntentStoresStackSessionInWebView() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.evaluateScript("document.readyState")
            scenario.onActivity { activity ->
                activity.setPendingAuthStateForTest("instrumentation-state")
                val accessCookie = URLEncoder.encode(
                    "[\"refresh-from-callback\",\"access-from-callback\"]",
                    StandardCharsets.UTF_8.name()
                )
                activity.handleIncomingIntent(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("cmux-ios://auth-callback?stack_access=$accessCookie&cmux_auth_state=instrumentation-state")
                    )
                )
            }

            onWebView()
                .withElement(findElement(Locator.ID, "authStatusText"))
                .check(webMatches(getText(), containsString("Signed in with a Stack session")))

            onWebView()
                .withElement(findElement(Locator.ID, "toast"))
                .check(webMatches(getText(), containsString("Signed in to Stack Auth")))
        }
    }

    @Test
    fun attachDeepLinkStoresPairedMacInWebView() {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("cmux-ios://attach?v=2&ub=instrumentation-user&pc=1&av=1.2.3&ab=42&r=100.64.0.12:58465")
        )

        ActivityScenario.launch<MainActivity>(intent).use {
            onWebView()
                .withElement(findElement(Locator.ID, "pairedList"))
                .check(webMatches(getText(), containsString("100.64.0.12:58465")))
        }
    }

    @Test
    fun sharedHostPortTextStoresPairedMacInWebView() {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .setClass(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
            .putExtra(Intent.EXTRA_TEXT, "100.64.0.77:58465")

        ActivityScenario.launch<MainActivity>(intent).use {
            onWebView()
                .withElement(findElement(Locator.ID, "pairedList"))
                .check(webMatches(getText(), containsString("100.64.0.77:58465")))
        }
    }

    @Test
    fun sharedTextSubtypeStoresPairedMacInWebView() {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/x-uri")
            .setClass(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
            .putExtra(Intent.EXTRA_TEXT, "100.64.0.79:58465")

        ActivityScenario.launch<MainActivity>(intent).use {
            onWebView()
                .withElement(findElement(Locator.ID, "pairedList"))
                .check(webMatches(getText(), containsString("100.64.0.79:58465")))
        }
    }

    @Test
    fun websocketPairingConnectsToFakeHostAndRendersWorkspaceList() {
        val server = MockWebServer()
        val observedMethods = java.util.Collections.synchronizedList(mutableListOf<String>())
        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    val request = JSONObject(readSingleFrame(bytes.toByteArray()))
                    val method = request.getString("method")
                    observedMethods.add(method)
                    webSocket.send(ByteString.of(*rpcResponseFrame(request)))
                }
            })
        )
        server.start(InetAddress.getByName("0.0.0.0"), 0)
        try {
            val wsUrl = "ws://${instrumentationDeviceHostAddress()}:${server.port}/mobile"
            val intent = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .setClass(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
                .putExtra(Intent.EXTRA_TEXT, wsUrl)

            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                waitUntil("fake host websocket opens") {
                    scenario.evaluateScript("document.getElementById('connectionText').textContent")
                        .contains("open")
                }
                waitUntil("fake host workspace list renders") {
                    scenario.evaluateScript("document.getElementById('workspaceList').textContent")
                        .contains("Fake Workspace")
                }

                onWebView()
                    .withElement(findElement(Locator.ID, "connectionText"))
                    .check(webMatches(getText(), containsString("open")))

                onWebView()
                    .withElement(findElement(Locator.ID, "hostText"))
                    .check(webMatches(getText(), containsString("Fake Android Host")))

                onWebView()
                    .withElement(findElement(Locator.ID, "workspaceList"))
                    .check(webMatches(getText(), containsString("Fake Workspace")))

                onWebView()
                    .withElement(findElement(Locator.ID, "workspaceList"))
                    .check(webMatches(getText(), containsString("Fake Shell")))

                scenario.evaluateScript("openTerminal('workspace-fake', 'terminal-fake'); true;")
                waitUntil("fake host terminal replay renders") {
                    scenario.evaluateScript("document.getElementById('terminalOutput').textContent")
                        .contains("fake replay ready")
                }

                onWebView()
                    .withElement(findElement(Locator.ID, "terminalOutput"))
                    .check(webMatches(getText(), containsString("fake replay ready")))

                scenario.evaluateScript(
                    """
                    document.getElementById('terminalInput').value = 'echo from android';
                    document.getElementById('sendInput').click();
                    true;
                    """.trimIndent()
                )
                waitUntil("fake host receives terminal input") {
                    observedMethods.contains("mobile.terminal.input")
                }

                scenario.evaluateScript(
                    """
                    document.getElementById('sendFeedback').click();
                    document.getElementById('feedbackText').value = 'android fake host feedback';
                    document.getElementById('submitFeedback').click();
                    true;
                    """.trimIndent()
                )
                waitUntil("fake host receives dogfood feedback") {
                    observedMethods.contains("dogfood.feedback.submit")
                }

                scenario.evaluateScript("document.getElementById('showChat').click(); true;")
                waitUntil("fake host chat session renders") {
                    scenario.evaluateScript("document.getElementById('chatSessionList').textContent")
                        .contains("Fake Agent Chat")
                }
                scenario.evaluateScript(
                    """
                    document.querySelector('[data-chat-session="chat-fake"]').click();
                    true;
                    """.trimIndent()
                )
                waitUntil("fake host chat history renders") {
                    scenario.evaluateScript("document.getElementById('chatMessages').textContent")
                        .contains("hello from fake chat")
                }
                scenario.evaluateScript(
                    """
                    document.getElementById('chatInput').value = 'reply from android chat';
                    document.getElementById('sendChat').click();
                    true;
                    """.trimIndent()
                )
                waitUntil("fake host receives chat send") {
                    observedMethods.contains("mobile.chat.send")
                }
            }

            val methods = observedMethods.toList()
            check("mobile.events.subscribe" in methods) { methods }
            check("mobile.host.status" in methods) { methods }
            check("mobile.workspace.list" in methods) { methods }
            check("mobile.terminal.replay" in methods) { methods }
            check("dogfood.feedback.submit" in methods) { methods }
            check("mobile.chat.sessions" in methods) { methods }
            check("mobile.chat.history" in methods) { methods }
            check("mobile.chat.send" in methods) { methods }
        } finally {
            try {
                server.shutdown()
            } catch (error: IOException) {
                if (error.message != "Gave up waiting for queue to shut down") {
                    throw error
                }
            }
        }
    }

    @Test
    fun processTextStoresPairedMacInWebView() {
        val intent = Intent(Intent.ACTION_PROCESS_TEXT)
            .setType("text/plain")
            .setClass(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
            .putExtra(Intent.EXTRA_PROCESS_TEXT, "100.64.0.78:58465")

        ActivityScenario.launch<MainActivity>(intent).use {
            onWebView()
                .withElement(findElement(Locator.ID, "pairedList"))
                .check(webMatches(getText(), containsString("100.64.0.78:58465")))
        }
    }

    @Test
    fun sharedAttachLinkFromNewIntentStoresPairedMacInWebView() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val intent = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(
                    Intent.EXTRA_TEXT,
                    "cmux-ios://attach?v=2&ub=instrumentation-user&pc=1&av=1.2.3&ab=42&r=100.64.0.88:58465"
                )

            scenario.onActivity { activity ->
                activity.handleIncomingIntent(intent)
            }

            onWebView()
                .withElement(findElement(Locator.ID, "pairedList"))
                .check(webMatches(getText(), containsString("100.64.0.88:58465")))
        }
    }

    @Test
    fun manualHostPortPairingStoresPairedMacInWebView() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onWebView()
                .withElement(findElement(Locator.ID, "pairingCode"))
                .perform(clearElement())
                .perform(webKeys("100.64.0.44:58465"))

            onWebView()
                .withElement(findElement(Locator.ID, "pairButton"))
                .perform(webClick())

            onWebView()
                .withElement(findElement(Locator.ID, "pairedList"))
                .check(webMatches(getText(), containsString("100.64.0.44:58465")))
        }
    }

    @Test
    fun pairedMacListShowsSupportedRouteByPriority() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.emitNativeEvent(
                """
                {
                  "type": "pairedMacs",
                  "payload": {
                    "macs": [
                      {
                        "id": "mac-websocket",
                        "display_name": "Studio Mac",
                        "routes": [
                          {
                            "id": "iroh",
                            "kind": "iroh",
                            "host": "ignored.example.test",
                            "port": 58465,
                            "priority": 0
                          },
                          {
                            "id": "websocket",
                            "kind": "websocket",
                            "host": "",
                            "port": 0,
                            "url": "wss://cmux.example.test/mobile",
                            "priority": 1
                          },
                          {
                            "id": "tailscale",
                            "kind": "tailscale",
                            "host": "100.64.0.12",
                            "port": 58465,
                            "priority": 10
                          }
                        ]
                      }
                    ]
                  }
                }
                """.trimIndent()
            )

            onWebView()
                .withElement(findElement(Locator.ID, "pairedList"))
                .check(webMatches(getText(), containsString("Studio Mac")))

            onWebView()
                .withElement(findElement(Locator.ID, "pairedList"))
                .check(webMatches(getText(), containsString("wss://cmux.example.test/mobile")))
        }
    }

    @Test
    fun workspaceFiltersShowUnreadAndPinnedSubsets() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.emitNativeEvent(
                """
                {
                  "type": "rpcResult",
                  "payload": {
                    "id": 1,
                    "method": "mobile.workspace.list",
                    "result": {
                      "workspaces": [
                        {
                          "id": "workspace-normal",
                          "title": "Normal Work",
                          "preview": "ready",
                          "is_pinned": false,
                          "has_unread": false,
                          "terminals": []
                        },
                        {
                          "id": "workspace-unread",
                          "title": "Unread Work",
                          "preview": "needs attention",
                          "is_pinned": false,
                          "has_unread": true,
                          "terminals": []
                        },
                        {
                          "id": "workspace-pinned",
                          "title": "Pinned Work",
                          "preview": "important",
                          "is_pinned": true,
                          "has_unread": false,
                          "terminals": []
                        }
                      ],
                      "groups": []
                    }
                  }
                }
                """.trimIndent()
            )

            onWebView()
                .withElement(findElement(Locator.ID, "workspaceList"))
                .check(webMatches(getText(), containsString("Normal Work")))

            onWebView()
                .withElement(findElement(Locator.XPATH, "//*[@data-workspace-filter='unread']"))
                .perform(webClick())

            val unreadList = scenario.evaluateScript("document.getElementById('workspaceList').textContent")
            check(unreadList.contains("Unread Work"))
            check(!unreadList.contains("Normal Work"))
            check(!unreadList.contains("Pinned Work"))

            onWebView()
                .withElement(findElement(Locator.XPATH, "//*[@data-workspace-filter='pinned']"))
                .perform(webClick())

            val pinnedList = scenario.evaluateScript("document.getElementById('workspaceList').textContent")
            check(pinnedList.contains("Pinned Work"))
            check(!pinnedList.contains("Normal Work"))
            check(!pinnedList.contains("Unread Work"))

            onWebView()
                .withElement(findElement(Locator.XPATH, "//*[@data-workspace-filter='all']"))
                .perform(webClick())

            val allList = scenario.evaluateScript("document.getElementById('workspaceList').textContent")
            check(allList.contains("Normal Work"))
            check(allList.contains("Unread Work"))
            check(allList.contains("Pinned Work"))
        }
    }

    @Test
    fun workspaceSearchMatchesWorkspaceAndTerminalFields() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.emitNativeEvent(
                """
                {
                  "type": "rpcResult",
                  "payload": {
                    "id": 1,
                    "method": "mobile.workspace.list",
                    "result": {
                      "workspaces": [
                        {
                          "id": "workspace-alpha",
                          "title": "Alpha Plan",
                          "preview": "planning notes",
                          "is_pinned": false,
                          "has_unread": false,
                          "terminals": [
                            {
                              "id": "terminal-alpha",
                              "title": "Planner",
                              "current_directory": "/repo/alpha"
                            }
                          ]
                        },
                        {
                          "id": "workspace-build",
                          "title": "Build Work",
                          "preview": "compilers",
                          "is_pinned": false,
                          "has_unread": true,
                          "terminals": [
                            {
                              "id": "terminal-build",
                              "title": "Gradle",
                              "current_directory": "/repo/android"
                            }
                          ]
                        }
                      ],
                      "groups": []
                    }
                  }
                }
                """.trimIndent()
            )

            onWebView()
                .withElement(findElement(Locator.ID, "workspaceSearch"))
                .perform(webKeys("alpha"))

            val alphaList = scenario.evaluateScript("document.getElementById('workspaceList').textContent")
            check(alphaList.contains("Alpha Plan"))
            check(!alphaList.contains("Build Work"))

            onWebView()
                .withElement(findElement(Locator.ID, "workspaceSearch"))
                .perform(clearElement())
                .perform(webKeys("android"))

            val terminalDirectoryList = scenario.evaluateScript("document.getElementById('workspaceList').textContent")
            check(terminalDirectoryList.contains("Build Work"))
            check(!terminalDirectoryList.contains("Alpha Plan"))

            onWebView()
                .withElement(findElement(Locator.XPATH, "//*[@data-workspace-filter='pinned']"))
                .perform(webClick())

            onWebView()
                .withElement(findElement(Locator.ID, "workspaceList"))
                .check(webMatches(getText(), containsString("No matching workspaces")))
        }
    }

    @Test
    fun workspaceUpdatedPushCoalescesRefreshUntilListReturns() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.evaluateScript(
                """
                window.__refreshWorkspaceCount = 0;
                window.cmuxAndroid = {
                  initialState: function() {},
                  refreshWorkspaces: function() {
                    window.__refreshWorkspaceCount += 1;
                  }
                };
                true;
                """.trimIndent()
            )

            scenario.emitNativeEvent(
                """
                {
                  "type": "push",
                  "payload": {
                    "type": "workspace.updated",
                    "payload": {}
                  }
                }
                """.trimIndent()
            )

            scenario.emitNativeEvent(
                """
                {
                  "type": "push",
                  "payload": {
                    "type": "workspace.updated",
                    "payload": {}
                  }
                }
                """.trimIndent()
            )

            val refreshesBeforeList = scenario.evaluateScript("window.__refreshWorkspaceCount")
            check(refreshesBeforeList == "1")

            scenario.emitNativeEvent(
                """
                {
                  "type": "rpcResult",
                  "payload": {
                    "id": 2,
                    "method": "mobile.workspace.list",
                    "result": {
                      "workspaces": [],
                      "groups": []
                    }
                  }
                }
                """.trimIndent()
            )

            scenario.emitNativeEvent(
                """
                {
                  "type": "push",
                  "payload": {
                    "type": "workspace.updated",
                    "payload": {}
                  }
                }
                """.trimIndent()
            )

            val refreshesAfterList = scenario.evaluateScript("window.__refreshWorkspaceCount")
            check(refreshesAfterList == "2")
        }
    }

    @Test
    fun workspaceListKeepsInferredUnreadCountFreshUntilBadgeArrives() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.emitNativeEvent(
                """
                {
                  "type": "rpcResult",
                  "payload": {
                    "id": 1,
                    "method": "mobile.workspace.list",
                    "result": {
                      "workspaces": [
                        {
                          "id": "workspace-unread",
                          "title": "Unread Work",
                          "preview": "needs attention",
                          "has_unread": true,
                          "terminals": []
                        }
                      ],
                      "groups": []
                    }
                  }
                }
                """.trimIndent()
            )

            onWebView()
                .withElement(findElement(Locator.ID, "notificationText"))
                .check(webMatches(getText(), containsString("1 unread notification")))

            scenario.emitNativeEvent(
                """
                {
                  "type": "rpcResult",
                  "payload": {
                    "id": 2,
                    "method": "mobile.workspace.list",
                    "result": {
                      "workspaces": [
                        {
                          "id": "workspace-unread",
                          "title": "Unread Work",
                          "preview": "caught up",
                          "has_unread": false,
                          "terminals": []
                        }
                      ],
                      "groups": []
                    }
                  }
                }
                """.trimIndent()
            )

            onWebView()
                .withElement(findElement(Locator.ID, "notificationText"))
                .check(webMatches(getText(), containsString("No unread notifications")))

            scenario.emitNativeEvent(
                """
                {
                  "type": "push",
                  "payload": {
                    "type": "notification.badge",
                    "payload": {
                      "unread_count": 3
                    }
                  }
                }
                """.trimIndent()
            )

            scenario.emitNativeEvent(
                """
                {
                  "type": "rpcResult",
                  "payload": {
                    "id": 3,
                    "method": "mobile.workspace.list",
                    "result": {
                      "workspaces": [
                        {
                          "id": "workspace-unread",
                          "title": "Unread Work",
                          "preview": "still caught up",
                          "has_unread": false,
                          "terminals": []
                        }
                      ],
                      "groups": []
                    }
                  }
                }
                """.trimIndent()
            )

            onWebView()
                .withElement(findElement(Locator.ID, "notificationText"))
                .check(webMatches(getText(), containsString("3 unread notifications")))
        }
    }

    @Test
    fun notificationDismissedPushAcceptsHandledIds() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.evaluateScript(
                """
                window.__dismissedRefreshCount = 0;
                const nativeBridge = window.cmuxAndroid;
                window.cmuxAndroid = Object.assign({}, nativeBridge, {
                  refreshWorkspaces: function() {
                    window.__dismissedRefreshCount += 1;
                  }
                });
                window.cmuxNativeEvent({
                  type: 'connection',
                  payload: { state: 'open' }
                });
                window.cmuxNativeEvent({
                  type: 'rpcResult',
                  payload: {
                    id: 1,
                    method: 'mobile.host.status',
                    result: { capabilities: ['notification.dismiss.v1'] }
                  }
                });
                state.deliveredNotificationIds = ['notification-1'];
                state.authoritativeUnreadNotificationCount = 2;
                renderNotificationStatus();
                true;
                """.trimIndent()
            )

            val initialState = scenario.evaluateScript(
                """
                JSON.stringify({
                  notificationText: document.getElementById('notificationText').textContent,
                  dismissDisabled: document.getElementById('dismissNotifications').disabled
                })
                """.trimIndent()
            )
            check(initialState.contains("\"notificationText\":\"2 unread notifications\"")) { initialState }
            check(initialState.contains("\"dismissDisabled\":false")) { initialState }

            scenario.evaluateScript(
                """
                window.cmuxNativeEvent({
                  type: 'push',
                  payload: {
                    type: 'notification.dismissed',
                    payload: {
                      handled_ids: ['notification-1'],
                      unread_count: 1
                    }
                  }
                });
                true;
                """.trimIndent()
            )

            val dismissedState = scenario.evaluateScript(
                """
                JSON.stringify({
                  notificationText: document.getElementById('notificationText').textContent,
                  dismissDisabled: document.getElementById('dismissNotifications').disabled,
                  refreshes: window.__dismissedRefreshCount
                })
                """.trimIndent()
            )
            check(dismissedState.contains("\"notificationText\":\"1 unread notification\""))
            check(dismissedState.contains("\"dismissDisabled\":true"))
            check(dismissedState.contains("\"refreshes\":1"))
        }
    }

    @Test
    fun notificationControlsRequireHostCapabilities() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.evaluateScript(
                """
                window.cmuxNativeEvent({
                  type: 'connection',
                  payload: { state: 'open' }
                });
                window.cmuxNativeEvent({
                  type: 'rpcResult',
                  payload: {
                    id: 1,
                    method: 'mobile.host.status',
                    result: { capabilities: [] }
                  }
                });
                state.deliveredNotificationIds = ['notification-1'];
                renderNotificationStatus();
                true;
                """.trimIndent()
            )

            val disabledState = scenario.evaluateScript(
                """
                JSON.stringify({
                  syncDisabled: document.getElementById('syncNotifications').disabled,
                  dismissDisabled: document.getElementById('dismissNotifications').disabled
                })
                """.trimIndent()
            )
            check(disabledState.contains("\"syncDisabled\":true")) { disabledState }
            check(disabledState.contains("\"dismissDisabled\":true")) { disabledState }

            scenario.evaluateScript(
                """
                window.cmuxNativeEvent({
                  type: 'rpcResult',
                  payload: {
                    id: 2,
                    method: 'mobile.host.status',
                    result: {
                      capabilities: ['notification.reconcile.v1', 'notification.dismiss.v1']
                    }
                  }
                });
                renderNotificationStatus();
                true;
                """.trimIndent()
            )

            val enabledState = scenario.evaluateScript(
                """
                JSON.stringify({
                  syncDisabled: document.getElementById('syncNotifications').disabled,
                  dismissDisabled: document.getElementById('dismissNotifications').disabled
                })
                """.trimIndent()
            )
            check(enabledState.contains("\"syncDisabled\":false")) { enabledState }
            check(enabledState.contains("\"dismissDisabled\":false")) { enabledState }
        }
    }

    @Test
    fun workspaceRefreshKeepsActiveTerminalVisibleAndCurrent() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.emitNativeEvent(
                """
                {
                  "type": "connection",
                  "payload": {
                    "state": "open"
                  }
                }
                """.trimIndent()
            )

            scenario.emitNativeEvent(
                """
                {
                  "type": "rpcResult",
                  "payload": {
                    "id": 1,
                    "method": "mobile.workspace.list",
                    "result": {
                      "workspaces": [
                        {
                          "id": "workspace-1",
                          "title": "Android QA",
                          "preview": "ready",
                          "terminals": [
                            {
                              "id": "terminal-1",
                              "title": "Build shell",
                              "current_directory": "/repo"
                            }
                          ]
                        }
                      ],
                      "groups": []
                    }
                  }
                }
                """.trimIndent()
            )

            scenario.evaluateScript("openTerminal('workspace-1', 'terminal-1'); true;")

            onWebView()
                .withElement(findElement(Locator.ID, "terminalTitle"))
                .check(webMatches(getText(), containsString("Build shell")))

            scenario.emitNativeEvent(
                """
                {
                  "type": "rpcResult",
                  "payload": {
                    "id": 2,
                    "method": "mobile.workspace.list",
                    "result": {
                      "workspaces": [
                        {
                          "id": "workspace-1",
                          "title": "Android QA Renamed",
                          "preview": "still ready",
                          "terminals": [
                            {
                              "id": "terminal-1",
                              "title": "Deploy shell",
                              "current_directory": "/repo/deploy"
                            }
                          ]
                        }
                      ],
                      "groups": []
                    }
                  }
                }
                """.trimIndent()
            )

            val activeTerminalState = scenario.evaluateScript(
                """
                JSON.stringify({
                  terminalHidden: document.getElementById('terminalView').classList.contains('hidden'),
                  workspaceHidden: document.getElementById('workspaceView').classList.contains('hidden'),
                  title: document.getElementById('terminalTitle').textContent,
                  meta: document.getElementById('terminalMeta').textContent
                })
                """.trimIndent()
            )
            check(activeTerminalState.contains("\"terminalHidden\":false"))
            check(activeTerminalState.contains("\"workspaceHidden\":true"))
            check(activeTerminalState.contains("\"title\":\"Deploy shell\""))
            check(activeTerminalState.contains("\"meta\":\"Android QA Renamed\""))
        }
    }

    @Test
    fun workspaceListRpcErrorRendersVisibleFailure() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.emitNativeEvent(
                """
                {
                  "type": "rpcError",
                  "payload": {
                    "id": 1,
                    "method": "mobile.workspace.list",
                    "code": "transport_error",
                    "message": "network lost"
                  }
                }
                """.trimIndent()
            )

            val workspaceErrorText = scenario.evaluateScript("document.getElementById('workspaceList').textContent")
            check(workspaceErrorText.contains("Could not load workspaces.")) { workspaceErrorText }
        }
    }

    @Test
    fun terminalSetFontPushUpdatesActiveTerminalFontOnly() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.evaluateScript(
                """
                window.__reportedViewports = [];
                window.__replayedTerminals = [];
                const nativeBridge = window.cmuxAndroid;
                window.cmuxAndroid = Object.assign({}, nativeBridge, {
                  reportViewport: function(workspaceId, terminalId, columns, rows) {
                    window.__reportedViewports.push({ workspaceId, terminalId, columns, rows });
                  },
                  replayTerminal: function(workspaceId, terminalId, columns, rows) {
                    window.__replayedTerminals.push({ workspaceId, terminalId, columns, rows });
                  }
                });
                const output = document.getElementById('terminalOutput');
                Object.defineProperty(output, 'clientWidth', { configurable: true, value: 420 });
                Object.defineProperty(output, 'clientHeight', { configurable: true, value: 240 });
                true;
                """.trimIndent()
            )

            scenario.emitNativeEvent(
                """
                {
                  "type": "rpcResult",
                  "payload": {
                    "id": 1,
                    "method": "mobile.workspace.list",
                    "result": {
                      "workspaces": [
                        {
                          "id": "workspace-1",
                          "title": "Android QA",
                          "preview": "ready",
                          "terminals": [
                            {
                              "id": "terminal-1",
                              "title": "Build shell",
                              "current_directory": "/repo"
                            }
                          ]
                        }
                      ],
                      "groups": []
                    }
                  }
                }
                """.trimIndent()
            )

            scenario.evaluateScript("openTerminal('workspace-1', 'terminal-1'); true;")

            scenario.emitNativeEvent(
                """
                {
                  "type": "push",
                  "payload": {
                    "type": "terminal.set_font",
                    "payload": {
                      "surface_id": "other-terminal",
                      "font_size": 20
                    }
                  }
                }
                """.trimIndent()
            )

            val unchangedFontSize = scenario.evaluateScript("document.getElementById('terminalOutput').style.fontSize")
            check(unchangedFontSize == "\"\"")

            scenario.emitNativeEvent(
                """
                {
                  "type": "push",
                  "payload": {
                    "type": "terminal.set_font",
                    "payload": {
                      "surface_id": "terminal-1",
                      "font_size": 18
                    }
                  }
                }
                """.trimIndent()
            )

            val activeFontSize = scenario.evaluateScript("document.getElementById('terminalOutput').style.fontSize")
            check(activeFontSize == "\"18px\"")
            val viewports = scenario.evaluateScript("JSON.stringify(window.__reportedViewports)")
            check(viewports.contains("\"columns\":40"))
            check(viewports.contains("\"rows\":9"))
            val replays = scenario.evaluateScript("JSON.stringify(window.__replayedTerminals)")
            check(replays.contains("\"columns\":40"))
            check(replays.contains("\"rows\":9"))
        }
    }

    @Test
    fun staleTerminalRenderGridPushDoesNotReplaceNewerFrame() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.emitNativeEvent(
                """
                {
                  "type": "rpcResult",
                  "payload": {
                    "id": 1,
                    "method": "mobile.workspace.list",
                    "result": {
                      "workspaces": [
                        {
                          "id": "workspace-1",
                          "title": "Android QA",
                          "preview": "ready",
                          "terminals": [
                            {
                              "id": "terminal-1",
                              "title": "Build shell",
                              "current_directory": "/repo"
                            }
                          ]
                        }
                      ],
                      "groups": []
                    }
                  }
                }
                """.trimIndent()
            )

            scenario.evaluateScript("openTerminal('workspace-1', 'terminal-1'); true;")

            scenario.emitNativeEvent(
                """
                {
                  "type": "push",
                  "payload": {
                    "type": "terminal.render_grid",
                    "payload": {
                      "surface_id": "terminal-1",
                      "state_seq": 2,
                      "full": true,
                      "rows": 1,
                      "columns": 20,
                      "row_spans": [
                        { "row": 0, "column": 0, "text": "new frame" }
                      ]
                    }
                  }
                }
                """.trimIndent()
            )

            scenario.emitNativeEvent(
                """
                {
                  "type": "push",
                  "payload": {
                    "type": "terminal.render_grid",
                    "payload": {
                      "surface_id": "terminal-1",
                      "state_seq": 1,
                      "full": true,
                      "rows": 1,
                      "columns": 20,
                      "row_spans": [
                        { "row": 0, "column": 0, "text": "old frame" }
                      ]
                    }
                  }
                }
                """.trimIndent()
            )

            onWebView()
                .withElement(findElement(Locator.ID, "terminalOutput"))
                .check(webMatches(getText(), containsString("new frame")))

            val terminalText = scenario.evaluateScript("document.getElementById('terminalOutput').textContent")
            check(!terminalText.contains("old frame"))
        }
    }

    @Test
    fun staleTerminalReplayDoesNotReplaceCurrentTerminalFrame() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.emitNativeEvent(
                """
                {
                  "type": "rpcResult",
                  "payload": {
                    "id": 1,
                    "method": "mobile.workspace.list",
                    "result": {
                      "workspaces": [
                        {
                          "id": "workspace-1",
                          "title": "Android QA",
                          "preview": "ready",
                          "terminals": [
                            {
                              "id": "terminal-1",
                              "title": "Build shell",
                              "current_directory": "/repo"
                            },
                            {
                              "id": "terminal-2",
                              "title": "Deploy shell",
                              "current_directory": "/repo/deploy"
                            }
                          ]
                        }
                      ],
                      "groups": []
                    }
                  }
                }
                """.trimIndent()
            )

            scenario.evaluateScript("openTerminal('workspace-1', 'terminal-1'); true;")
            scenario.evaluateScript("openTerminal('workspace-1', 'terminal-2'); true;")

            scenario.emitNativeEvent(
                """
                {
                  "type": "rpcResult",
                  "payload": {
                    "id": 2,
                    "method": "mobile.terminal.replay",
                    "result": {
                      "render_grid": {
                        "surface_id": "terminal-2",
                        "state_seq": 2,
                        "full": true,
                        "rows": 1,
                        "columns": 22,
                        "row_spans": [
                          { "row": 0, "column": 0, "text": "deploy current" }
                        ]
                      }
                    }
                  }
                }
                """.trimIndent()
            )

            scenario.emitNativeEvent(
                """
                {
                  "type": "rpcResult",
                  "payload": {
                    "id": 3,
                    "method": "mobile.terminal.replay",
                    "result": {
                      "render_grid": {
                        "surface_id": "terminal-1",
                        "state_seq": 3,
                        "full": true,
                        "rows": 1,
                        "columns": 22,
                        "row_spans": [
                          { "row": 0, "column": 0, "text": "build stale" }
                        ]
                      }
                    }
                  }
                }
                """.trimIndent()
            )

            onWebView()
                .withElement(findElement(Locator.ID, "terminalOutput"))
                .check(webMatches(getText(), containsString("deploy current")))

            val terminalText = scenario.evaluateScript("document.getElementById('terminalOutput').textContent")
            check(!terminalText.contains("build stale"))
        }
    }

    @Test
    fun invalidTerminalReplayBase64ShowsEmptyTerminal() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.emitNativeEvent(
                """
                {
                  "type": "rpcResult",
                  "payload": {
                    "id": 1,
                    "method": "mobile.workspace.list",
                    "result": {
                      "workspaces": [
                        {
                          "id": "workspace-1",
                          "title": "Android QA",
                          "preview": "ready",
                          "terminals": [
                            {
                              "id": "terminal-1",
                              "title": "Build shell",
                              "current_directory": "/repo"
                            }
                          ]
                        }
                      ],
                      "groups": []
                    }
                  }
                }
                """.trimIndent()
            )

            scenario.evaluateScript("openTerminal('workspace-1', 'terminal-1'); true;")

            scenario.emitNativeEvent(
                """
                {
                  "type": "rpcResult",
                  "payload": {
                    "id": 2,
                    "method": "mobile.terminal.replay",
                    "result": {
                      "data_b64": "not valid base64!"
                    }
                  }
                }
                """.trimIndent()
            )

            onWebView()
                .withElement(findElement(Locator.ID, "terminalOutput"))
                .check(webMatches(getText(), containsString("(terminal is empty)")))
        }
    }

    @Test
    fun staleTerminalScrollResultDoesNotReplaceCurrentTerminalFrame() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.emitNativeEvent(
                """
                {
                  "type": "rpcResult",
                  "payload": {
                    "id": 1,
                    "method": "mobile.workspace.list",
                    "result": {
                      "workspaces": [
                        {
                          "id": "workspace-1",
                          "title": "Android QA",
                          "preview": "ready",
                          "terminals": [
                            {
                              "id": "terminal-1",
                              "title": "Build shell",
                              "current_directory": "/repo"
                            },
                            {
                              "id": "terminal-2",
                              "title": "Deploy shell",
                              "current_directory": "/repo/deploy"
                            }
                          ]
                        }
                      ],
                      "groups": []
                    }
                  }
                }
                """.trimIndent()
            )

            scenario.evaluateScript("openTerminal('workspace-1', 'terminal-2'); true;")

            scenario.emitNativeEvent(
                """
                {
                  "type": "rpcResult",
                  "payload": {
                    "id": 2,
                    "method": "mobile.terminal.scroll",
                    "result": {
                      "render_grid": {
                        "surface_id": "terminal-2",
                        "state_seq": 2,
                        "full": true,
                        "rows": 1,
                        "columns": 24,
                        "row_spans": [
                          { "row": 0, "column": 0, "text": "deploy scroll" }
                        ]
                      }
                    }
                  }
                }
                """.trimIndent()
            )

            scenario.emitNativeEvent(
                """
                {
                  "type": "rpcResult",
                  "payload": {
                    "id": 3,
                    "method": "mobile.terminal.scroll",
                    "result": {
                      "render_grid": {
                        "surface_id": "terminal-1",
                        "state_seq": 3,
                        "full": true,
                        "rows": 1,
                        "columns": 24,
                        "row_spans": [
                          { "row": 0, "column": 0, "text": "build scroll stale" }
                        ]
                      }
                    }
                  }
                }
                """.trimIndent()
            )

            onWebView()
                .withElement(findElement(Locator.ID, "terminalOutput"))
                .check(webMatches(getText(), containsString("deploy scroll")))

            val terminalText = scenario.evaluateScript("document.getElementById('terminalOutput').textContent")
            check(!terminalText.contains("build scroll stale"))
        }
    }

    @Test
    fun connectionCloseClearsActiveTerminalSurface() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.emitNativeEvent(
                """
                {
                  "type": "connection",
                  "payload": {
                    "state": "open"
                  }
                }
                """.trimIndent()
            )

            scenario.emitNativeEvent(
                """
                {
                  "type": "rpcResult",
                  "payload": {
                    "id": 1,
                    "method": "mobile.workspace.list",
                    "result": {
                      "workspaces": [
                        {
                          "id": "workspace-1",
                          "title": "Android QA",
                          "preview": "ready",
                          "terminals": [
                            {
                              "id": "terminal-1",
                              "title": "Build shell",
                              "current_directory": "/repo"
                            }
                          ]
                        }
                      ],
                      "groups": []
                    }
                  }
                }
                """.trimIndent()
            )

            scenario.evaluateScript("openTerminal('workspace-1', 'terminal-1'); true;")

            scenario.emitNativeEvent(
                """
                {
                  "type": "push",
                  "payload": {
                    "type": "terminal.render_grid",
                    "payload": {
                      "surface_id": "terminal-1",
                      "state_seq": 1,
                      "full": true,
                      "rows": 1,
                      "columns": 20,
                      "row_spans": [
                        { "row": 0, "column": 0, "text": "old session" }
                      ]
                    }
                  }
                }
                """.trimIndent()
            )

            onWebView()
                .withElement(findElement(Locator.ID, "terminalOutput"))
                .check(webMatches(getText(), containsString("old session")))

            scenario.emitNativeEvent(
                """
                {
                  "type": "connection",
                  "payload": {
                    "state": "closed",
                    "detail": "network lost"
                  }
                }
                """.trimIndent()
            )

            val closedState = scenario.evaluateScript(
                """
                JSON.stringify({
                  terminalHidden: document.getElementById('terminalView').classList.contains('hidden'),
                  workspaceHidden: document.getElementById('workspaceView').classList.contains('hidden'),
                  terminalOutput: document.getElementById('terminalOutput').textContent,
                  openTerminalDisabled: document.querySelector('[data-open-terminal="workspace-1"]')?.disabled ?? true
                })
                """.trimIndent()
            )
            check(closedState.contains("\"terminalHidden\":true")) { closedState }
            check(closedState.contains("\"workspaceHidden\":false")) { closedState }
            check(closedState.contains("\"terminalOutput\":\"\"")) { closedState }
            check(closedState.contains("\"openTerminalDisabled\":true")) { closedState }
        }
    }

    @Test
    fun createdTerminalOpensContainingWorkspace() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.emitNativeEvent(
                """
                {
                  "type": "rpcResult",
                  "payload": {
                    "id": 1,
                    "method": "mobile.terminal.create",
                    "result": {
                      "created_terminal_id": "terminal-new",
                      "workspaces": [
                        {
                          "id": "workspace-first",
                          "title": "First Workspace",
                          "preview": "no new terminal here",
                          "terminals": [
                            {
                              "id": "terminal-old",
                              "title": "Old shell",
                              "current_directory": "/old"
                            }
                          ]
                        },
                        {
                          "id": "workspace-target",
                          "title": "Target Workspace",
                          "preview": "new terminal lives here",
                          "terminals": [
                            {
                              "id": "terminal-new",
                              "title": "New shell",
                              "current_directory": "/target"
                            }
                          ]
                        }
                      ],
                      "groups": []
                    }
                  }
                }
                """.trimIndent()
            )

            val activeTerminalState = scenario.evaluateScript(
                """
                JSON.stringify({
                  terminalHidden: document.getElementById('terminalView').classList.contains('hidden'),
                  workspaceHidden: document.getElementById('workspaceView').classList.contains('hidden'),
                  title: document.getElementById('terminalTitle').textContent,
                  meta: document.getElementById('terminalMeta').textContent
                })
                """.trimIndent()
            )
            check(activeTerminalState.contains("\"terminalHidden\":false"))
            check(activeTerminalState.contains("\"workspaceHidden\":true"))
            check(activeTerminalState.contains("\"title\":\"New shell\""))
            check(activeTerminalState.contains("\"meta\":\"Target Workspace\""))
        }
    }

    @Test
    fun terminalBytesPushUpdatesActiveTerminalFallback() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.emitNativeEvent(
                """
                {
                  "type": "rpcResult",
                  "payload": {
                    "id": 1,
                    "method": "mobile.workspace.list",
                    "result": {
                      "workspaces": [
                        {
                          "id": "workspace-1",
                          "title": "Android QA",
                          "preview": "ready",
                          "terminals": [
                            {
                              "id": "terminal-1",
                              "title": "Build shell",
                              "current_directory": "/repo"
                            }
                          ]
                        }
                      ],
                      "groups": []
                    }
                  }
                }
                """.trimIndent()
            )

            scenario.evaluateScript("openTerminal('workspace-1', 'terminal-1'); true;")

            scenario.emitNativeEvent(
                """
                {
                  "type": "push",
                  "payload": {
                    "type": "terminal.bytes",
                    "payload": {
                      "surface_id": "terminal-1",
                      "data_b64": "cmF3IGJ5dGVzIGZhbGxiYWNr"
                    }
                  }
                }
                """.trimIndent()
            )

            onWebView()
                .withElement(findElement(Locator.ID, "terminalOutput"))
                .check(webMatches(getText(), containsString("raw bytes fallback")))
        }
    }

    @Test
    fun terminalBytesPushSkipsDuplicateSequenceOverlap() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.emitNativeEvent(
                """
                {
                  "type": "rpcResult",
                  "payload": {
                    "id": 1,
                    "method": "mobile.workspace.list",
                    "result": {
                      "workspaces": [
                        {
                          "id": "workspace-1",
                          "title": "Android QA",
                          "preview": "ready",
                          "terminals": [
                            {
                              "id": "terminal-1",
                              "title": "Build shell",
                              "current_directory": "/repo"
                            }
                          ]
                        }
                      ],
                      "groups": []
                    }
                  }
                }
                """.trimIndent()
            )

            scenario.evaluateScript("openTerminal('workspace-1', 'terminal-1'); true;")

            scenario.emitNativeEvent(
                """
                {
                  "type": "push",
                  "payload": {
                    "type": "terminal.bytes",
                    "payload": {
                      "surface_id": "terminal-1",
                      "seq": 0,
                      "data_b64": "aGVsbG8="
                    }
                  }
                }
                """.trimIndent()
            )
            scenario.emitNativeEvent(
                """
                {
                  "type": "push",
                  "payload": {
                    "type": "terminal.bytes",
                    "payload": {
                      "surface_id": "terminal-1",
                      "seq": 0,
                      "data_b64": "aGVsbG8="
                    }
                  }
                }
                """.trimIndent()
            )
            scenario.emitNativeEvent(
                """
                {
                  "type": "push",
                  "payload": {
                    "type": "terminal.bytes",
                    "payload": {
                      "surface_id": "terminal-1",
                      "seq": 5,
                      "data_b64": "IQ=="
                    }
                  }
                }
                """.trimIndent()
            )

            val terminalText = scenario.evaluateScript("document.getElementById('terminalOutput').textContent")
            check(terminalText.contains("hello!")) { terminalText }
        }
    }

    @Test
    fun newWorkspaceButtonRequiresHostCapability() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.emitNativeEvent(
                """
                {
                  "type": "connection",
                  "payload": {
                    "state": "open"
                  }
                }
                """.trimIndent()
            )
            scenario.emitNativeEvent(
                """
                {
                  "type": "rpcResult",
                  "payload": {
                    "id": 1,
                    "method": "mobile.host.status",
                    "result": {
                      "capabilities": []
                    }
                  }
                }
                """.trimIndent()
            )
            scenario.evaluateScript(
                """
                window.__createWorkspaceCalls = 0;
                window.cmuxAndroid = {
                  createWorkspace: function() {
                    window.__createWorkspaceCalls += 1;
                  }
                };
                true;
                """.trimIndent()
            )

            val capabilityState = scenario.evaluateScript(
                """
                JSON.stringify({
                  calls: window.__createWorkspaceCalls,
                  disabled: document.getElementById('createWorkspace').disabled,
                  toast: document.getElementById('toast').textContent
                })
                """.trimIndent()
            )
            check(capabilityState.contains("\"calls\":0"))
            check(capabilityState.contains("\"disabled\":true"))
        }
    }

    @Test
    fun newTerminalButtonRequiresHostCapability() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.emitNativeEvent(
                """
                {
                  "type": "connection",
                  "payload": {
                    "state": "open"
                  }
                }
                """.trimIndent()
            )
            scenario.emitNativeEvent(
                """
                {
                  "type": "rpcResult",
                  "payload": {
                    "id": 1,
                    "method": "mobile.host.status",
                    "result": {
                      "capabilities": []
                    }
                  }
                }
                """.trimIndent()
            )
            scenario.emitNativeEvent(
                """
                {
                  "type": "rpcResult",
                  "payload": {
                    "id": 2,
                    "method": "mobile.workspace.list",
                    "result": {
                      "workspaces": [
                        {
                          "id": "workspace-empty",
                          "title": "Empty Workspace",
                          "terminals": []
                        }
                      ],
                      "groups": []
                    }
                  }
                }
                """.trimIndent()
            )
            scenario.evaluateScript(
                """
                window.__createTerminalCalls = 0;
                window.cmuxAndroid = {
                  createTerminal: function() {
                    window.__createTerminalCalls += 1;
                  }
                };
                true;
                """.trimIndent()
            )

            val capabilityState = scenario.evaluateScript(
                """
                JSON.stringify({
                  calls: window.__createTerminalCalls,
                  disabled: document.querySelector('[data-create-terminal="workspace-empty"]').disabled,
                  toast: document.getElementById('toast').textContent
                })
                """.trimIndent()
            )
            check(capabilityState.contains("\"calls\":0"))
            check(capabilityState.contains("\"disabled\":true"))
        }
    }

    @Test
    fun workspaceGroupsRequireHostCapability() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.emitNativeEvent(
                """
                {
                  "type": "connection",
                  "payload": {
                    "state": "open"
                  }
                }
                """.trimIndent()
            )
            scenario.emitNativeEvent(
                """
                {
                  "type": "rpcResult",
                  "payload": {
                    "id": 1,
                    "method": "mobile.host.status",
                    "result": {
                      "capabilities": []
                    }
                  }
                }
                """.trimIndent()
            )
            scenario.emitNativeEvent(
                """
                {
                  "type": "rpcResult",
                  "payload": {
                    "id": 2,
                    "method": "mobile.workspace.list",
                    "result": {
                      "workspaces": [
                        {
                          "id": "workspace-grouped",
                          "title": "Grouped Workspace",
                          "group_id": "group-1",
                          "terminals": []
                        }
                      ],
                      "groups": [
                        {
                          "id": "group-1",
                          "name": "Grouped Section",
                          "is_collapsed": false
                        }
                      ]
                    }
                  }
                }
                """.trimIndent()
            )
            scenario.evaluateScript(
                """
                window.__groupToggleCalls = 0;
                window.cmuxAndroid = {
                  setWorkspaceGroupCollapsed: function() {
                    window.__groupToggleCalls += 1;
                  }
                };
                true;
                """.trimIndent()
            )

            val groupState = scenario.evaluateScript(
                """
                JSON.stringify({
                  groupHeader: document.querySelector('[data-toggle-group="group-1"]') !== null,
                  groupDisabled: document.querySelector('[data-toggle-group="group-1"]').disabled,
                  groupedWorkspace: document.getElementById('workspaceList').textContent.includes('Grouped Workspace')
                })
                """.trimIndent()
            )
            check(groupState.contains("\"groupHeader\":true"))
            check(groupState.contains("\"groupDisabled\":true"))
            check(groupState.contains("\"groupedWorkspace\":true"))
            val calls = scenario.evaluateScript("window.__groupToggleCalls")
            check(calls == "0")
        }
    }

    @Test
    fun nativeWorkspaceAndTerminalEventsRenderInWebView() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onWebView()
                .withElement(findElement(Locator.ID, "pairingView"))
                .check(webMatches(getText(), containsString("Pair a Mac")))

            scenario.emitNativeEvent(
                """
                {
                  "type": "rpcResult",
                  "payload": {
                    "id": 1,
                    "method": "mobile.workspace.list",
                    "result": {
                      "workspaces": [
                        {
                          "id": "workspace-1",
                          "title": "Android QA",
                          "preview": "ready",
                          "terminals": [
                            {
                              "id": "terminal-1",
                              "title": "Build shell",
                              "current_directory": "/repo"
                            }
                          ]
                        }
                      ],
                      "groups": []
                    }
                  }
                }
                """.trimIndent()
            )

            onWebView()
                .withElement(findElement(Locator.ID, "workspaceList"))
                .check(webMatches(getText(), containsString("Android QA")))

            onWebView()
                .withElement(findElement(Locator.ID, "workspaceList"))
                .check(webMatches(getText(), containsString("Build shell")))

            scenario.emitNativeEvent(
                """
                {
                  "type": "pairedMacs",
                  "payload": {
                    "macs": [
                      {
                        "id": "mac-switcher",
                        "display_name": "Switcher Mac",
                        "routes": [
                          {
                            "id": "tailscale",
                            "kind": "tailscale",
                            "host": "100.64.0.99",
                            "port": 58465,
                            "priority": 10
                          }
                        ]
                      }
                    ]
                  }
                }
                """.trimIndent()
            )
            scenario.emitNativeEvent(
                """
                {
                  "type": "connection",
                  "payload": {
                    "state": "open"
                  }
                }
                """.trimIndent()
            )

            scenario.evaluateScript("document.getElementById('showPairedMacs').click(); true;")

            val pairingScreenState = scenario.evaluateScript(
                """
                JSON.stringify({
                  pairingHidden: document.getElementById('pairingView').classList.contains('hidden'),
                  workspaceHidden: document.getElementById('workspaceView').classList.contains('hidden'),
                  backHidden: document.getElementById('backToWorkspacesFromPairing').classList.contains('hidden'),
                  pairedText: document.getElementById('pairedList').textContent
                })
                """.trimIndent()
            )
            check(pairingScreenState.contains("\"pairingHidden\":false")) { pairingScreenState }
            check(pairingScreenState.contains("\"workspaceHidden\":true")) { pairingScreenState }
            check(pairingScreenState.contains("\"backHidden\":false")) { pairingScreenState }
            check(pairingScreenState.contains("Switcher Mac")) { pairingScreenState }

            scenario.evaluateScript("document.getElementById('backToWorkspacesFromPairing').click(); true;")

            val workspaceScreenState = scenario.evaluateScript(
                """
                JSON.stringify({
                  pairingHidden: document.getElementById('pairingView').classList.contains('hidden'),
                  workspaceHidden: document.getElementById('workspaceView').classList.contains('hidden')
                })
                """.trimIndent()
            )
            check(workspaceScreenState.contains("\"pairingHidden\":true"))
            check(workspaceScreenState.contains("\"workspaceHidden\":false"))

            scenario.evaluateScript("openTerminal('workspace-1', 'terminal-1'); true;")

            scenario.emitNativeEvent(
                """
                {
                  "type": "push",
                  "payload": {
                    "type": "terminal.render_grid",
                    "payload": {
                      "surface_id": "terminal-1",
                      "rows": 1,
                      "columns": 14,
                      "row_spans": [
                        { "row": 0, "column": 0, "text": "hello android" }
                      ]
                    }
                  }
                }
                """.trimIndent()
            )

            val toastAfterLiveFrame = scenario.evaluateScript("document.getElementById('toast').textContent")
            check(!toastAfterLiveFrame.contains("Live terminal update received"))

            onWebView()
                .withElement(findElement(Locator.ID, "terminalTitle"))
                .check(webMatches(getText(), containsString("Build shell")))

            onWebView()
                .withElement(findElement(Locator.ID, "terminalOutput"))
                .check(webMatches(getText(), containsString("hello android")))

            scenario.emitNativeEvent(
                """
                {
                  "type": "connection",
                  "payload": {
                    "state": "closed",
                    "detail": "network lost"
                  }
                }
                """.trimIndent()
            )

            val closedControlState = scenario.evaluateScript(
                """
                JSON.stringify({
                  refresh: document.getElementById('refreshWorkspaces').disabled,
                  create: document.getElementById('createWorkspace').disabled,
                  openTerminal: document.querySelector('[data-open-terminal="workspace-1"]')?.disabled ?? true,
                  ctrlC: document.querySelector('[data-terminal-key="ctrl-c"]').disabled,
                  terminalInput: document.getElementById('terminalInput').disabled,
                  imageInput: document.getElementById('imageInput').disabled
                })
                """.trimIndent()
            )
            check(closedControlState.contains("\"refresh\":true")) { closedControlState }
            check(closedControlState.contains("\"create\":true")) { closedControlState }
            check(closedControlState.contains("\"openTerminal\":true")) { closedControlState }
            check(closedControlState.contains("\"ctrlC\":true")) { closedControlState }
            check(closedControlState.contains("\"terminalInput\":true")) { closedControlState }
            check(closedControlState.contains("\"imageInput\":true")) { closedControlState }

            scenario.emitNativeEvent(
                """
                {
                  "type": "connection",
                  "payload": {
                    "state": "open"
                  }
                }
                """.trimIndent()
            )
            scenario.emitNativeEvent(
                """
                {
                  "type": "rpcResult",
                  "payload": {
                    "id": 3,
                    "method": "mobile.workspace.list",
                    "result": {
                      "workspaces": [
                        {
                          "id": "workspace-1",
                          "title": "Android QA",
                          "preview": "ready",
                          "terminals": [
                            {
                              "id": "terminal-1",
                              "title": "Build shell",
                              "current_directory": "/repo"
                            }
                          ]
                        }
                      ],
                      "groups": []
                    }
                  }
                }
                """.trimIndent()
            )
            scenario.evaluateScript("openTerminal('workspace-1', 'terminal-1'); true;")

            scenario.evaluateScript(
                """
                window.__lastSendInput = null;
                window.__sentInputs = [];
                window.__lastPasteText = null;
                window.cmuxAndroid = {
                  sendInput: function(workspaceId, terminalId, text, columns, rows) {
                    window.__lastSendInput = { workspaceId, terminalId, text, columns, rows };
                    window.__sentInputs.push(window.__lastSendInput);
                  },
                  pasteText: function(workspaceId, terminalId, text, submitKey, columns, rows) {
                    window.__lastPasteText = { workspaceId, terminalId, text, submitKey, columns, rows };
                  }
                };
                true;
                """.trimIndent()
            )

            scenario.evaluateScript("document.querySelector('[data-terminal-key=\"ctrl-c\"]').click(); true;")

            val sentInput = scenario.evaluateScript("JSON.stringify(window.__lastSendInput)")
            check(sentInput.contains("\"workspaceId\":\"workspace-1\"")) { sentInput }
            check(sentInput.contains("\"terminalId\":\"terminal-1\"")) { sentInput }
            check(sentInput.contains("\"text\":\"\\u0003\"")) { sentInput }

            scenario.evaluateScript(
                """
                ['arrow-up', 'backspace', 'ctrl-a', 'ctrl-e', 'ctrl-u', 'ctrl-w', 'page-up', 'end']
                  .forEach((key) => document.querySelector('[data-terminal-key="' + key + '"]').click());
                true;
                """.trimIndent()
            )

            val sentInputs = scenario.evaluateScript("JSON.stringify(window.__sentInputs)")
            check(sentInputs.contains("\"text\":\"\\u001b[A\"")) { sentInputs }
            check(sentInputs.contains("\"text\":\"\u007f\"")) { sentInputs }
            check(sentInputs.contains("\"text\":\"\\u0001\"")) { sentInputs }
            check(sentInputs.contains("\"text\":\"\\u0005\"")) { sentInputs }
            check(sentInputs.contains("\"text\":\"\\u0015\"")) { sentInputs }
            check(sentInputs.contains("\"text\":\"\\u0017\"")) { sentInputs }
            check(sentInputs.contains("\"text\":\"\\u001b[5~\"")) { sentInputs }
            check(sentInputs.contains("\"text\":\"\\u001b[F\"")) { sentInputs }

            scenario.evaluateScript(
                """
                const input = document.getElementById('terminalInput');
                input.value = 'echo keyboard';
                input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', ctrlKey: true, bubbles: true, cancelable: true }));
                true;
                """.trimIndent()
            )

            val keyboardSendInput = scenario.evaluateScript("JSON.stringify(window.__lastSendInput)")
            check(keyboardSendInput.contains("\"text\":\"echo keyboard\""))
            val clearedAfterKeyboardSend = scenario.evaluateScript("document.getElementById('terminalInput').value")
            check(clearedAfterKeyboardSend == "\"\"")

            scenario.emitNativeEvent(
                """
                {
                  "type": "rpcError",
                  "payload": {
                    "id": 50,
                    "method": "mobile.terminal.input",
                    "code": "host_error",
                    "message": "input failed"
                  }
                }
                """.trimIndent()
            )

            val restoredAfterInputError = scenario.evaluateScript("document.getElementById('terminalInput').value")
            check(restoredAfterInputError == "\"echo keyboard\"")

            scenario.evaluateScript(
                """
                window.__lastPasteText = null;
                document.getElementById('terminalInput').value = 'button paste';
                true;
                """.trimIndent()
            )

            onWebView()
                .withElement(findElement(Locator.ID, "pasteInput"))
                .perform(webClick())

            val buttonPasteText = scenario.evaluateScript("JSON.stringify(window.__lastPasteText)")
            check(buttonPasteText.contains("\"text\":\"button paste\""))
            check(buttonPasteText.contains("\"submitKey\":\"return\""))
            val clearedAfterButtonPaste = scenario.evaluateScript("document.getElementById('terminalInput').value")
            check(clearedAfterButtonPaste == "\"\"")

            scenario.evaluateScript(
                """
                window.__lastSendInput = null;
                window.__lastPasteText = null;
                document.getElementById('terminalInput').value = 'discard me';
                true;
                """.trimIndent()
            )

            scenario.evaluateScript("document.getElementById('clearTerminalInput').click(); true;")

            val clearedByButton = scenario.evaluateScript("document.getElementById('terminalInput').value")
            check(clearedByButton == "\"\"") { clearedByButton }
            val noBridgeCallsAfterClear = scenario.evaluateScript("window.__lastSendInput === null && window.__lastPasteText === null")
            check(noBridgeCallsAfterClear == "true") { noBridgeCallsAfterClear }
        }
    }

    private fun ActivityScenario<MainActivity>.emitNativeEvent(json: String) {
        val script = "window.cmuxNativeEvent && window.cmuxNativeEvent($json)"
        evaluateScript(script)
    }

    private fun MainActivity.setPendingAuthStateForTest(state: String) {
        val bridgeField = MainActivity::class.java.getDeclaredField("bridge")
        bridgeField.isAccessible = true
        val bridge = bridgeField.get(this)
        val stateField = bridge.javaClass.getDeclaredField("pendingAuthState")
        stateField.isAccessible = true
        stateField.set(bridge, state)
    }

    private fun readSingleFrame(bytes: ByteArray): String {
        check(bytes.size >= 4) { "Missing frame length" }
        val length = ByteBuffer.wrap(bytes, 0, 4).int
        check(length > 0 && bytes.size >= 4 + length) { "Invalid frame length: $length" }
        return String(bytes, 4, length, StandardCharsets.UTF_8)
    }

    private fun rpcResponseFrame(request: JSONObject): ByteArray {
        val id = request.getInt("id")
        val method = request.getString("method")
        val result = when (method) {
            "mobile.host.status" -> JSONObject()
                .put("mac_display_name", "Fake Android Host")
                .put("capabilities", org.json.JSONArray()
                    .put("events.v1")
                    .put("terminal.create.v1")
                    .put("terminal.paste_image.v1")
                    .put("terminal.render_grid.v1")
                    .put("terminal.replay.v1")
                    .put("terminal.viewport.v1")
                    .put("workspace.create.v1")
                    .put("workspace.actions.v1")
                    .put("workspace.read_state.v1")
                    .put("workspace.close.v1")
                    .put("dogfood.v1")
                    .put("workspace.groups.v1"))
            "mobile.workspace.list" -> JSONObject()
                .put("workspaces", org.json.JSONArray().put(
                    JSONObject()
                        .put("id", "workspace-fake")
                        .put("title", "Fake Workspace")
                        .put("preview", "connected through websocket")
                        .put("terminals", org.json.JSONArray().put(
                            JSONObject()
                                .put("id", "terminal-fake")
                                .put("title", "Fake Shell")
                                .put("current_directory", "/fake")
                        ))
                ))
                .put("groups", org.json.JSONArray())
            "mobile.terminal.replay" -> JSONObject()
                .put("workspace_id", "workspace-fake")
                .put("surface_id", "terminal-fake")
                .put("seq", 1)
                .put(
                    "data_b64",
                    "fake replay ready\n".toByteArray(StandardCharsets.UTF_8).let {
                        android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP)
                    }
                )
            "mobile.terminal.input" -> JSONObject()
                .put("workspace_id", "workspace-fake")
                .put("surface_id", "terminal-fake")
                .put("queued", false)
                .put("terminal_seq", 2)
            "mobile.terminal.viewport" -> JSONObject()
                .put("workspace_id", "workspace-fake")
                .put("surface_id", "terminal-fake")
                .put("columns", 80)
                .put("rows", 24)
            "dogfood.feedback.submit" -> JSONObject()
                .put("accepted", true)
            "mobile.chat.sessions" -> JSONObject()
                .put("sessions", org.json.JSONArray().put(
                    JSONObject()
                        .put("session_id", "chat-fake")
                        .put("agent_kind", "codex")
                        .put("title", "Fake Agent Chat")
                        .put("workspace_id", "workspace-fake")
                        .put("terminal_id", "terminal-fake")
                        .put("cwd", "/fake")
                        .put("state", JSONObject().put("state", "idle"))
                        .put("version", 1)
                ))
            "mobile.chat.history" -> JSONObject()
                .put("messages", org.json.JSONArray().put(
                    JSONObject()
                        .put("id", "message-fake")
                        .put("seq", 1)
                        .put("role", "agent")
                        .put("timestamp", "2026-07-10T00:00:00Z")
                        .put("kind", JSONObject()
                            .put("type", "prose")
                            .put("text", "hello from fake chat"))
                ))
                .put("has_more", false)
            "mobile.chat.send" -> JSONObject()
                .put("submitted", true)
            "mobile.events.subscribe" -> JSONObject().put("already_subscribed", false)
            else -> JSONObject()
        }
        val payload = JSONObject()
            .put("id", id)
            .put("ok", true)
            .put("result", result)
            .toString()
            .toByteArray(StandardCharsets.UTF_8)
        return ByteBuffer.allocate(4 + payload.size)
            .putInt(payload.size)
            .put(payload)
            .array()
    }

    private fun instrumentationDeviceHostAddress(): String {
        val addresses = NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .map { it.hostAddress }
            .filter { address ->
                address != null &&
                    !address.startsWith("127.") &&
                    address != "0.0.0.0"
            }
        return checkNotNull(addresses.firstOrNull()) { "No non-loopback IPv4 address found" }
    }

    private fun waitUntil(description: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            if (condition()) return
            Thread.sleep(100)
        }
        error("Timed out waiting for $description")
    }

    private fun ActivityScenario<MainActivity>.evaluateScript(script: String): String {
        waitForWebAppReady()
        val latch = CountDownLatch(1)
        val result = AtomicReference<String>()
        onActivity { activity ->
            val content = activity.findViewById<ViewGroup>(android.R.id.content)
            (content.getChildAt(0) as WebView).evaluateJavascript(script) {
                result.set(it)
                latch.countDown()
            }
        }
        check(latch.await(5, TimeUnit.SECONDS)) { "Timed out while evaluating WebView script" }
        return normalizeEvaluatedScriptResult(result.get().orEmpty())
    }

    private fun normalizeEvaluatedScriptResult(value: String): String {
        if (value.length < 2 || value.first() != '"' || value.last() != '"') return value
        val inner = value.substring(1, value.length - 1)
        val looksLikeJsonString = inner.startsWith("{") || inner.startsWith("[")
        if (!looksLikeJsonString) return value
        return inner
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }

    private fun ActivityScenario<MainActivity>.waitForWebAppReady() {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        var lastState = ""
        while (System.nanoTime() < deadline) {
            val latch = CountDownLatch(1)
            val result = AtomicReference<String>()
            onActivity { activity ->
                val content = activity.findViewById<ViewGroup>(android.R.id.content)
                (content.getChildAt(0) as WebView).evaluateJavascript(
                    """
                    document.readyState === 'complete' &&
                      typeof window.cmuxNativeEvent === 'function' &&
                      document.getElementById('pairingView') !== null &&
                      document.getElementById('workspaceList') !== null
                    """.trimIndent()
                ) {
                    result.set(it)
                    latch.countDown()
                }
            }
            if (latch.await(1, TimeUnit.SECONDS)) {
                lastState = result.get().orEmpty()
                if (lastState == "true") {
                    return
                }
            }
            Thread.sleep(50)
        }
        error("Timed out waiting for WebView app readiness: $lastState")
    }
}
