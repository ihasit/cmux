package com.cmux.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileReconnectPolicyTest {
    @Test
    fun nextDelayUsesConfiguredBackoffAndCapsAtLastDelay() {
        val policy = MobileReconnectPolicy(listOf(10L, 20L))

        assertEquals(10L, policy.nextDelayMillis())
        assertEquals(20L, policy.nextDelayMillis())
        assertEquals(20L, policy.nextDelayMillis())
    }

    @Test
    fun resetStartsBackoffFromFirstDelayAgain() {
        val policy = MobileReconnectPolicy(listOf(10L, 20L))

        policy.nextDelayMillis()
        policy.nextDelayMillis()
        policy.reset()

        assertEquals(10L, policy.nextDelayMillis())
    }

    @Test
    fun reconnectsAfterUnexpectedCloseWithActiveMac() {
        val policy = MobileReconnectPolicy()

        assertTrue(
            policy.shouldReconnect(
                closedDetail = "remote closed",
                hasActiveMac = true,
                userRequestedDisconnect = false
            )
        )
    }

    @Test
    fun doesNotReconnectAfterManualDisconnect() {
        val policy = MobileReconnectPolicy()

        assertFalse(
            policy.shouldReconnect(
                closedDetail = "closed by webview",
                hasActiveMac = true,
                userRequestedDisconnect = true
            )
        )
    }

    @Test
    fun doesNotReconnectWithoutActiveMac() {
        val policy = MobileReconnectPolicy()

        assertFalse(
            policy.shouldReconnect(
                closedDetail = "remote closed",
                hasActiveMac = false,
                userRequestedDisconnect = false
            )
        )
    }

    @Test
    fun doesNotReconnectWhenNoSupportedRouteExists() {
        val policy = MobileReconnectPolicy()

        assertFalse(
            policy.shouldReconnect(
                closedDetail = "no supported route",
                hasActiveMac = true,
                userRequestedDisconnect = false
            )
        )
    }
}
