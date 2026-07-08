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
                  terminalInput: document.getElementById('terminalInput').disabled
                })
                """.trimIndent()
            )
            check(closedControlState.contains("\"refresh\":true"))
            check(closedControlState.contains("\"create\":true"))
            check(closedControlState.contains("\"openTerminal\":true"))
            check(closedControlState.contains("\"ctrlC\":true"))
            check(closedControlState.contains("\"terminalInput\":true"))

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
                window.cmuxAndroid = {
                  sendInput: function(workspaceId, terminalId, text, columns, rows) {
                    window.__lastSendInput = { workspaceId, terminalId, text, columns, rows };
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
