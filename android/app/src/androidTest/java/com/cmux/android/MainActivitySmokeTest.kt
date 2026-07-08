package com.cmux.android

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.web.assertion.WebViewAssertions.webMatches
import androidx.test.espresso.web.sugar.Web.onWebView
import androidx.test.espresso.web.webdriver.DriverAtoms.findElement
import androidx.test.espresso.web.webdriver.DriverAtoms.getText
import androidx.test.espresso.web.webdriver.Locator
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.CoreMatchers.containsString
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @Test
    fun launchShowsPairingAndStackAuthControls() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onWebView()
                .withElement(findElement(Locator.ID, "pairingView"))
                .check(webMatches(getText(), containsString("Pair a Mac")))

            onWebView()
                .withElement(findElement(Locator.ID, "authTitle"))
                .check(webMatches(getText(), containsString("Stack Auth token")))
        }
    }
}
