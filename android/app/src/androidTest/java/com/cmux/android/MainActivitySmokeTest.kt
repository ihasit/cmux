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
import org.hamcrest.CoreMatchers.containsString
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
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
        ActivityScenario.launch(MainActivity::class.java).use {
            onWebView()
                .withElement(findElement(Locator.ID, "pairingView"))
                .check(webMatches(getText(), containsString("Pair a Mac")))

            onWebView()
                .withElement(findElement(Locator.ID, "authTitle"))
                .check(webMatches(getText(), containsString("Stack Auth token")))

            onWebView()
                .withElement(findElement(Locator.ID, "enableNotifications"))
                .check(webMatches(getText(), containsString("Enable alerts")))
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
            .putExtra(Intent.EXTRA_TEXT, "100.64.0.79:58465")

        ActivityScenario.launch<MainActivity>(intent).use {
            onWebView()
                .withElement(findElement(Locator.ID, "pairedList"))
                .check(webMatches(getText(), containsString("100.64.0.79:58465")))
        }
    }

    @Test
    fun processTextStoresPairedMacInWebView() {
        val intent = Intent(Intent.ACTION_PROCESS_TEXT)
            .setType("text/plain")
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

            onWebView()
                .withElement(findElement(Locator.XPATH, "//*[@data-open-terminal='workspace-1']"))
                .perform(webClick())

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

            onWebView()
                .withElement(findElement(Locator.ID, "showPairedMacs"))
                .perform(webClick())

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
            check(pairingScreenState.contains("\"pairingHidden\":false"))
            check(pairingScreenState.contains("\"workspaceHidden\":true"))
            check(pairingScreenState.contains("\"backHidden\":false"))
            check(pairingScreenState.contains("Switcher Mac"))

            onWebView()
                .withElement(findElement(Locator.ID, "backToWorkspacesFromPairing"))
                .perform(webClick())

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

            onWebView()
                .withElement(findElement(Locator.XPATH, "//*[@data-open-terminal='workspace-1']"))
                .perform(webClick())

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

            onWebView()
                .withElement(findElement(Locator.ID, "terminalTitle"))
                .check(webMatches(getText(), containsString("Build shell")))

            onWebView()
                .withElement(findElement(Locator.ID, "terminalOutput"))
                .check(webMatches(getText(), containsString("hello android")))

            scenario.evaluateScript(
                """
                window.__copiedTerminalText = null;
                navigator.clipboard = null;
                document.execCommand = function(command) {
                  if (command === 'copy') {
                    window.__copiedTerminalText = document.activeElement.value;
                    return true;
                  }
                  return false;
                };
                true;
                """.trimIndent()
            )

            onWebView()
                .withElement(findElement(Locator.ID, "copyTerminalOutput"))
                .perform(webClick())

            val copiedTerminalText = scenario.evaluateScript("window.__copiedTerminalText")
            check(copiedTerminalText.contains("hello android"))

            onWebView()
                .withElement(findElement(Locator.ID, "toast"))
                .check(webMatches(getText(), containsString("Terminal output copied")))

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
                  openTerminal: document.querySelector('[data-open-terminal="workspace-1"]').disabled,
                  ctrlC: document.querySelector('[data-terminal-key="ctrl-c"]').disabled,
                  terminalInput: document.getElementById('terminalInput').disabled,
                  imageInput: document.getElementById('imageInput').disabled
                })
                """.trimIndent()
            )
            check(closedControlState.contains("\"refresh\":true"))
            check(closedControlState.contains("\"create\":true"))
            check(closedControlState.contains("\"openTerminal\":true"))
            check(closedControlState.contains("\"ctrlC\":true"))
            check(closedControlState.contains("\"terminalInput\":true"))
            check(closedControlState.contains("\"imageInput\":true"))

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

            onWebView()
                .withElement(findElement(Locator.XPATH, "//*[@data-terminal-key='ctrl-c']"))
                .perform(webClick())

            val sentInput = scenario.evaluateScript("JSON.stringify(window.__lastSendInput)")
            check(sentInput.contains("\"workspaceId\":\"workspace-1\""))
            check(sentInput.contains("\"terminalId\":\"terminal-1\""))
            check(sentInput.contains("\"text\":\"\\u0003\""))

            onWebView()
                .withElement(findElement(Locator.XPATH, "//*[@data-terminal-key='arrow-up']"))
                .perform(webClick())

            onWebView()
                .withElement(findElement(Locator.XPATH, "//*[@data-terminal-key='backspace']"))
                .perform(webClick())

            onWebView()
                .withElement(findElement(Locator.XPATH, "//*[@data-terminal-key='page-up']"))
                .perform(webClick())

            onWebView()
                .withElement(findElement(Locator.XPATH, "//*[@data-terminal-key='end']"))
                .perform(webClick())

            val sentInputs = scenario.evaluateScript("JSON.stringify(window.__sentInputs)")
            check(sentInputs.contains("\"text\":\"\\u001b[A\""))
            check(sentInputs.contains("\"text\":\"\\u007f\""))
            check(sentInputs.contains("\"text\":\"\\u001b[5~\""))
            check(sentInputs.contains("\"text\":\"\\u001b[F\""))

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

            scenario.evaluateScript(
                """
                const input = document.getElementById('terminalInput');
                input.value = 'multi\\nline';
                input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', ctrlKey: true, shiftKey: true, bubbles: true, cancelable: true }));
                true;
                """.trimIndent()
            )

            val keyboardPasteText = scenario.evaluateScript("JSON.stringify(window.__lastPasteText)")
            check(keyboardPasteText.contains("\"text\":\"multi\\nline\""))
            check(keyboardPasteText.contains("\"submitKey\":\"return\""))

            scenario.evaluateScript(
                """
                window.__lastSendInput = null;
                window.__lastPasteText = null;
                document.getElementById('terminalInput').value = 'discard me';
                true;
                """.trimIndent()
            )

            onWebView()
                .withElement(findElement(Locator.ID, "clearTerminalInput"))
                .perform(webClick())

            val clearedByButton = scenario.evaluateScript("document.getElementById('terminalInput').value")
            check(clearedByButton == "\"\"")
            val sendAfterClear = scenario.evaluateScript("JSON.stringify(window.__lastSendInput)")
            val pasteAfterClear = scenario.evaluateScript("JSON.stringify(window.__lastPasteText)")
            check(sendAfterClear == "null")
            check(pasteAfterClear == "null")
        }
    }

    private fun ActivityScenario<MainActivity>.emitNativeEvent(json: String) {
        val script = "window.cmuxNativeEvent && window.cmuxNativeEvent($json)"
        evaluateScript(script)
    }

    private fun ActivityScenario<MainActivity>.evaluateScript(script: String): String {
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
        return result.get().orEmpty()
    }
}
