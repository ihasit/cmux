package com.cmux.android

import android.content.Context
import android.content.Intent
import android.net.Uri
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
}
