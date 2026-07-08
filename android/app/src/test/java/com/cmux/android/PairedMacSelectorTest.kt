package com.cmux.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairedMacSelectorTest {
    @Test
    fun startupMacUsesFirstMacWithSupportedRoute() {
        val unsupported = pairedMac(
            id = "unsupported",
            routes = listOf(route(kind = "iroh", id = "iroh"))
        )
        val supported = pairedMac(
            id = "supported",
            routes = listOf(route(kind = "tailscale", id = "tailscale"))
        )

        assertEquals(supported, PairedMacSelector.startupMac(listOf(unsupported, supported)))
    }

    @Test
    fun startupMacReturnsNullWhenNoMacHasSupportedRoutes() {
        val unsupported = pairedMac(
            id = "unsupported",
            routes = listOf(route(kind = "iroh", id = "iroh"))
        )

        assertNull(PairedMacSelector.startupMac(listOf(unsupported)))
    }

    private fun pairedMac(id: String, routes: List<CmuxRoute>): PairedMac {
        return PairedMac(
            id = id,
            displayName = "Mac",
            userId = "user",
            userEmail = "user@example.test",
            pairingCompatibilityVersion = 1,
            appVersion = "1.0",
            appBuild = "1",
            routes = routes
        )
    }

    private fun route(kind: String, id: String): CmuxRoute {
        return CmuxRoute(
            id = id,
            kind = kind,
            host = "100.64.0.5",
            port = 58465,
            priority = 1,
            url = null
        )
    }
}
