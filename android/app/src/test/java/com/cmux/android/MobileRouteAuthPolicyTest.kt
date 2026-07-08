package com.cmux.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileRouteAuthPolicyTest {
    @Test
    fun allowsTailscaleCgnatAndMagicDnsRoutes() {
        assertTrue(MobileRouteAuthPolicy.routeAllowsStackAuth(hostRoute("100.64.0.1", "tailscale")))
        assertTrue(MobileRouteAuthPolicy.routeAllowsStackAuth(hostRoute("100.127.255.254", "tailscale")))
        assertTrue(MobileRouteAuthPolicy.routeAllowsStackAuth(hostRoute("studio.tailnet.ts.net", "tailscale")))
    }

    @Test
    fun rejectsPlainLanAndPretendTailscaleRoutes() {
        assertFalse(MobileRouteAuthPolicy.routeAllowsStackAuth(hostRoute("192.168.1.20", "tailscale")))
        assertFalse(MobileRouteAuthPolicy.routeAllowsStackAuth(hostRoute("10.0.0.8", "tailscale")))
        assertFalse(MobileRouteAuthPolicy.routeAllowsStackAuth(hostRoute("100.128.0.1", "tailscale")))
        assertFalse(MobileRouteAuthPolicy.routeAllowsStackAuth(hostRoute("studio.local", "tailscale")))
    }

    @Test
    fun allowsOnlyLoopbackDebugRoutes() {
        assertTrue(MobileRouteAuthPolicy.routeAllowsStackAuth(hostRoute("127.0.0.1", "debug_loopback")))
        assertTrue(MobileRouteAuthPolicy.routeAllowsStackAuth(hostRoute("[::1]", "debug_loopback")))
        assertFalse(MobileRouteAuthPolicy.routeAllowsStackAuth(hostRoute("192.168.1.20", "debug_loopback")))
    }

    @Test
    fun allowsSecureWebSocketRoutesOnly() {
        assertTrue(MobileRouteAuthPolicy.routeAllowsStackAuth(webSocketRoute("wss://cmux.example.test/mobile")))
        assertFalse(MobileRouteAuthPolicy.routeAllowsStackAuth(webSocketRoute("ws://cmux.example.test/mobile")))
        assertFalse(MobileRouteAuthPolicy.routeAllowsStackAuth(webSocketRoute("https://cmux.example.test/mobile")))
    }

    @Test
    fun rejectsSecureWebSocketLoopbackRoutes() {
        assertFalse(MobileRouteAuthPolicy.routeAllowsStackAuth(webSocketRoute("wss://127.0.0.1:58465/mobile")))
        assertFalse(MobileRouteAuthPolicy.routeAllowsStackAuth(webSocketRoute("wss://localhost:58465/mobile")))
        assertFalse(MobileRouteAuthPolicy.routeAllowsStackAuth(webSocketRoute("wss://[::1]:58465/mobile")))
    }

    private fun hostRoute(host: String, kind: String): CmuxRoute {
        return CmuxRoute(
            id = kind,
            kind = kind,
            host = host,
            port = 58465,
            priority = 10,
            url = null
        )
    }

    private fun webSocketRoute(url: String): CmuxRoute {
        return CmuxRoute(
            id = "websocket",
            kind = "websocket",
            host = "",
            port = 0,
            priority = 0,
            url = url
        )
    }
}
