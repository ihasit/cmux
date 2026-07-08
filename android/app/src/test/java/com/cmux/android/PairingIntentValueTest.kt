package com.cmux.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingIntentValueTest {
    @Test
    fun sendTextUsesSharedTextExtra() {
        val value = PairingIntentValue.fromParts(
            action = "android.intent.action.SEND",
            type = "text/plain",
            dataString = null,
            extraText = "100.64.0.77:58465",
            processText = null
        )

        assertEquals("100.64.0.77:58465", value)
    }

    @Test
    fun sendNonTextFallsBackToDataStringBeforeExtraText() {
        val value = PairingIntentValue.fromParts(
            action = "android.intent.action.SEND",
            type = "image/png",
            dataString = "cmux-ios://attach?v=2&r=100.64.0.5:58465",
            extraText = "100.64.0.77:58465",
            processText = null
        )

        assertEquals("cmux-ios://attach?v=2&r=100.64.0.5:58465", value)
    }

    @Test
    fun processTextUsesSelectedText() {
        val value = PairingIntentValue.fromParts(
            action = "android.intent.action.PROCESS_TEXT",
            type = "text/plain",
            dataString = null,
            extraText = "ignored",
            processText = "100.64.0.78:58465"
        )

        assertEquals("100.64.0.78:58465", value)
    }

    @Test
    fun viewDeepLinkUsesDataString() {
        val value = PairingIntentValue.fromParts(
            action = "android.intent.action.VIEW",
            type = null,
            dataString = "cmux-ios://auth-callback?stack_refresh=r&stack_access=a",
            extraText = null,
            processText = null
        )

        assertEquals("cmux-ios://auth-callback?stack_refresh=r&stack_access=a", value)
    }

    @Test
    fun missingIntentPayloadReturnsNull() {
        val value = PairingIntentValue.fromParts(
            action = "android.intent.action.VIEW",
            type = null,
            dataString = null,
            extraText = null,
            processText = null
        )

        assertNull(value)
    }
}
