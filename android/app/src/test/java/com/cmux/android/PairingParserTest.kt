package com.cmux.android

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Base64

class PairingParserTest {
    private val parser = PairingParser()

    @Test
    fun parseAttachV2KeepsTailscaleRoutesInPriorityOrder() {
        val mac = parser.parse(
            "cmux-ios://attach?v=2&ub=user-1&pc=1&av=1.2.3&ab=42" +
                "&r=100.64.0.5:58465&r=cmux-host.test:58466"
        )

        assertEquals("user-1", mac.userId)
        assertEquals(1, mac.pairingCompatibilityVersion)
        assertEquals("1.2.3", mac.appVersion)
        assertEquals("42", mac.appBuild)
        assertEquals(2, mac.routes.size)
        assertEquals("tailscale", mac.routes[0].id)
        assertEquals("100.64.0.5", mac.routes[0].host)
        assertEquals(58465, mac.routes[0].port)
        assertEquals(10, mac.routes[0].priority)
        assertEquals("tailscale_2", mac.routes[1].id)
        assertEquals("cmux-host.test", mac.routes[1].host)
        assertEquals(58466, mac.routes[1].port)
        assertEquals(mac.routes[0], mac.primaryRoute)
    }

    @Test
    fun parseAttachV2RejectsLoopbackRoutes() {
        val error = assertThrows(PairingException::class.java) {
            parser.parse("cmux-ios://attach?v=2&r=127.0.0.1:58465")
        }

        assertEquals("pair.error.loopback", error.messageKey)
    }

    @Test
    fun parseCompactTicketKeepsWebSocketUrlRoute() {
        val payload = JSONObject()
            .put("v", 1)
            .put("d", "mac-1")
            .put("r", JSONArray().put(
                JSONObject()
                    .put("i", "ws")
                    .put("k", "websocket")
                    .put("p", 1)
                    .put("e", JSONObject().put("u", "wss://cmux.example.test/mobile"))
            ))
        val mac = parser.parse("cmux-ios://attach?payload=${base64Url(payload)}")

        assertEquals("mac-1", mac.id)
        assertEquals(1, mac.routes.size)
        val route = mac.routes.single()
        assertEquals("ws", route.id)
        assertEquals("websocket", route.kind)
        assertEquals(1, route.priority)
        assertEquals("wss://cmux.example.test/mobile", route.url)
        assertEquals("", route.host)
        assertEquals(0, route.port)
        assertEquals(route, mac.primaryRoute)
    }

    @Test
    fun parseFullTicketKeepsMixedHostPortAndWebSocketRoutes() {
        val payload = JSONObject()
            .put("macDeviceID", "mac-2")
            .put("routes", JSONArray()
                .put(JSONObject()
                    .put("id", "tailscale")
                    .put("kind", "tailscale")
                    .put("priority", 10)
                    .put("endpoint", JSONObject()
                        .put("type", "host_port")
                        .put("host", "100.64.0.8")
                        .put("port", 58465)))
                .put(JSONObject()
                    .put("id", "websocket")
                    .put("kind", "websocket")
                    .put("priority", 1)
                    .put("endpoint", JSONObject()
                        .put("type", "url")
                        .put("url", "wss://cmux.example.test/mobile"))))
        val mac = parser.parse("cmux-ios://attach?payload=${base64Url(payload)}")

        assertEquals("mac-2", mac.id)
        assertEquals(2, mac.routes.size)
        assertEquals("websocket", mac.primaryRoute?.kind)
        assertEquals("wss://cmux.example.test/mobile", mac.primaryRoute?.url)
    }

    @Test
    fun parseLegacyPairPayloadKeepsHostPortRoute() {
        val payload = JSONObject()
            .put("host", "100.64.0.9")
            .put("port", 58465)
            .put("transport", "tailscale")
            .put("mac_device_id", "mac-legacy")

        val mac = parser.parse("cmux-ios://pair?payload=${base64Url(payload)}")

        assertEquals("mac-legacy", mac.id)
        assertEquals(1, mac.routes.size)
        assertEquals("tailscale", mac.routes.single().kind)
        assertEquals("100.64.0.9", mac.routes.single().host)
        assertEquals(58465, mac.routes.single().port)
    }

    @Test
    fun pairedMacJsonRoundTripsWebSocketRoutes() {
        val mac = PairedMac(
            id = "mac-3",
            displayName = "Studio",
            userId = "user-3",
            userEmail = null,
            pairingCompatibilityVersion = 1,
            appVersion = "1.0",
            appBuild = "7",
            routes = listOf(
                CmuxRoute(
                    id = "websocket",
                    kind = "websocket",
                    host = "",
                    port = 0,
                    priority = 0,
                    url = "wss://cmux.example.test/mobile"
                )
            )
        )

        val decoded = PairedMac.fromJson(mac.toJson())

        assertEquals(mac.id, decoded.id)
        assertEquals(mac.displayName, decoded.displayName)
        assertEquals(1, decoded.routes.size)
        assertEquals("websocket", decoded.routes.single().kind)
        assertEquals("wss://cmux.example.test/mobile", decoded.routes.single().url)
        assertNull(decoded.routes.single().url?.takeIf { it.isBlank() })
        assertNotNull(decoded.primaryRoute)
    }

    private fun base64Url(json: JSONObject): String {
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(json.toString().toByteArray(Charsets.UTF_8))
    }
}
