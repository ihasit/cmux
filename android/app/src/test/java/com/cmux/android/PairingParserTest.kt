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
    fun parseManualHostPortKeepsTailscaleRoute() {
        val mac = parser.parse("100.64.0.5:58465")

        val route = mac.routes.single()
        assertEquals("manual", route.id)
        assertEquals("tailscale", route.kind)
        assertEquals("100.64.0.5", route.host)
        assertEquals(58465, route.port)
        assertEquals(10, route.priority)
        assertEquals(route, mac.primaryRoute)
    }

    @Test
    fun parseManualDomainHostPortKeepsHost() {
        val mac = parser.parse("cmux-host.test:58466")

        val route = mac.routes.single()
        assertEquals("cmux-host.test", route.host)
        assertEquals(58466, route.port)
    }

    @Test
    fun parseManualHostPortTrimsHostAndPortWhitespace() {
        val mac = parser.parse(" 100.64.0.5 : 58465 ")

        val route = mac.routes.single()
        assertEquals("100.64.0.5", route.host)
        assertEquals(58465, route.port)
        assertEquals(route, mac.primaryRoute)
    }

    @Test
    fun parseManualIpv6HostPortKeepsHost() {
        val mac = parser.parse("[fd7a:115c:a1e0::42]:58465")

        val route = mac.routes.single()
        assertEquals("fd7a:115c:a1e0::42", route.host)
        assertEquals(58465, route.port)
    }

    @Test
    fun parseManualIpv6HostPortTrimsBracketSpacing() {
        val mac = parser.parse(" [fd7a:115c:a1e0::42] : 58465 ")

        val route = mac.routes.single()
        assertEquals("fd7a:115c:a1e0::42", route.host)
        assertEquals(58465, route.port)
    }

    @Test
    fun parseManualWebSocketUrlKeepsWebSocketRoute() {
        val mac = parser.parse("wss://cmux.example.test/mobile")

        val route = mac.routes.single()
        assertEquals("manual_websocket", route.id)
        assertEquals("websocket", route.kind)
        assertEquals("", route.host)
        assertEquals(0, route.port)
        assertEquals(5, route.priority)
        assertEquals("wss://cmux.example.test/mobile", route.url)
        assertEquals(route, mac.primaryRoute)
    }

    @Test
    fun parseManualInsecureWebSocketUrlKeepsWebSocketRoute() {
        val mac = parser.parse("ws://100.64.0.5:58465/mobile")

        val route = mac.routes.single()
        assertEquals("websocket", route.kind)
        assertEquals("ws://100.64.0.5:58465/mobile", route.url)
    }

    @Test
    fun parseManualWebSocketUrlRejectsLoopbackRoutes() {
        val error = assertThrows(PairingException::class.java) {
            parser.parse("ws://127.0.0.1:58465/mobile")
        }

        assertEquals("pair.error.loopback", error.messageKey)
    }

    @Test
    fun parseManualWebSocketUrlAcceptsUppercaseScheme() {
        val mac = parser.parse("WSS://cmux.example.test/mobile")

        val route = mac.routes.single()
        assertEquals("websocket", route.kind)
        assertEquals("WSS://cmux.example.test/mobile", route.url)
    }

    @Test
    fun parseManualHostPortRejectsLoopbackRoutes() {
        val error = assertThrows(PairingException::class.java) {
            parser.parse("127.0.0.1:58465")
        }

        assertEquals("pair.error.loopback", error.messageKey)
    }

    @Test
    fun parsePlainTextWithoutRouteStillReportsSchemeError() {
        val error = assertThrows(PairingException::class.java) {
            parser.parse("not a pairing code")
        }

        assertEquals("pair.error.scheme", error.messageKey)
    }

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
    fun parseAttachV2AcceptsUppercaseScheme() {
        val mac = parser.parse("CMUX-IOS://attach?v=2&r=100.64.0.5:58465")

        assertEquals("tailscale", mac.routes.single().kind)
        assertEquals("100.64.0.5", mac.routes.single().host)
        assertEquals(58465, mac.routes.single().port)
    }

    @Test
    fun parseAttachV2AcceptsUppercaseHost() {
        val mac = parser.parse("cmux-ios://ATTACH?v=2&r=100.64.0.5:58465")

        assertEquals("tailscale", mac.routes.single().kind)
        assertEquals("100.64.0.5", mac.routes.single().host)
        assertEquals(58465, mac.routes.single().port)
    }

    @Test
    fun parseAttachV2RejectsLoopbackRoutes() {
        val error = assertThrows(PairingException::class.java) {
            parser.parse("cmux-ios://attach?v=2&r=127.0.0.1:58465")
        }

        assertEquals("pair.error.loopback", error.messageKey)
    }

    @Test
    fun parseAttachPayloadRejectsMalformedPayloadWithPairingError() {
        val error = assertThrows(PairingException::class.java) {
            parser.parse("cmux-ios://attach?payload=not-base64")
        }

        assertEquals("pair.error.invalidRoute", error.messageKey)
    }

    @Test
    fun parseLegacyPairPayloadRejectsMalformedPayloadWithPairingError() {
        val error = assertThrows(PairingException::class.java) {
            parser.parse("cmux-ios://pair?payload=not-base64")
        }

        assertEquals("pair.error.invalidRoute", error.messageKey)
    }

    @Test
    fun parseCompactTicketRejectsLoopbackHostPortRoute() {
        val payload = JSONObject()
            .put("v", 1)
            .put("d", "mac-loopback")
            .put("r", JSONArray().put(
                JSONObject()
                    .put("i", "local")
                    .put("k", "tailscale")
                    .put("p", 1)
                    .put("e", JSONObject().put("h", "127.0.0.1").put("p", 58465))
            ))

        val error = assertThrows(PairingException::class.java) {
            parser.parse("cmux-ios://attach?payload=${base64Url(payload)}")
        }

        assertEquals("pair.error.loopback", error.messageKey)
    }

    @Test
    fun parseFullTicketRejectsLoopbackHostPortRoute() {
        val payload = JSONObject()
            .put("macDeviceID", "mac-loopback")
            .put("routes", JSONArray().put(
                JSONObject()
                    .put("id", "local")
                    .put("kind", "tailscale")
                    .put("priority", 1)
                    .put("endpoint", JSONObject()
                        .put("type", "host_port")
                        .put("host", "localhost")
                        .put("port", 58465))
            ))

        val error = assertThrows(PairingException::class.java) {
            parser.parse("cmux-ios://attach?payload=${base64Url(payload)}")
        }

        assertEquals("pair.error.loopback", error.messageKey)
    }

    @Test
    fun parseLegacyPairPayloadRejectsLoopbackRoute() {
        val payload = JSONObject()
            .put("host", "127.0.0.1")
            .put("port", 58465)
            .put("transport", "tailscale")
            .put("mac_device_id", "mac-loopback")

        val error = assertThrows(PairingException::class.java) {
            parser.parse("cmux-ios://pair?payload=${base64Url(payload)}")
        }

        assertEquals("pair.error.loopback", error.messageKey)
    }

    @Test
    fun parseCompactTicketAllowsExplicitDebugLoopbackRoute() {
        val payload = JSONObject()
            .put("v", 1)
            .put("d", "mac-debug")
            .put("r", JSONArray().put(
                JSONObject()
                    .put("i", "debug")
                    .put("k", "debug_loopback")
                    .put("p", 1)
                    .put("e", JSONObject().put("h", "127.0.0.1").put("p", 58465))
            ))

        val mac = parser.parse("cmux-ios://attach?payload=${base64Url(payload)}")

        assertEquals("debug_loopback", mac.routes.single().kind)
        assertEquals("127.0.0.1", mac.routes.single().host)
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
    fun parseCompactTicketTrimsRouteKind() {
        val payload = JSONObject()
            .put("v", 1)
            .put("d", "mac-trimmed-kind")
            .put("r", JSONArray().put(
                JSONObject()
                    .put("i", "trimmed")
                    .put("k", " tailscale ")
                    .put("p", 1)
                    .put("e", JSONObject().put("h", "100.64.0.10").put("p", 58465))
            ))

        val mac = parser.parse("cmux-ios://attach?payload=${base64Url(payload)}")

        assertEquals("tailscale", mac.routes.single().kind)
        assertEquals(mac.routes.single(), mac.primaryRoute)
    }

    @Test
    fun parseCompactTicketNormalizesRouteKindCase() {
        val payload = JSONObject()
            .put("v", 1)
            .put("d", "mac-uppercase-kind")
            .put("r", JSONArray().put(
                JSONObject()
                    .put("i", "uppercase")
                    .put("k", " TAILSCALE ")
                    .put("p", 1)
                    .put("e", JSONObject().put("h", "100.64.0.10").put("p", 58465))
            ))

        val mac = parser.parse("cmux-ios://attach?payload=${base64Url(payload)}")

        assertEquals("tailscale", mac.routes.single().kind)
        assertEquals(mac.routes.single(), mac.primaryRoute)
    }

    @Test
    fun parseCompactTicketRejectsInvalidWebSocketUrlRoute() {
        val payload = JSONObject()
            .put("v", 1)
            .put("d", "mac-invalid-websocket")
            .put("r", JSONArray().put(
                JSONObject()
                    .put("i", "ws")
                    .put("k", "websocket")
                    .put("p", 1)
                    .put("e", JSONObject().put("u", "https://cmux.example.test/mobile"))
            ))

        val error = assertThrows(PairingException::class.java) {
            parser.parse("cmux-ios://attach?payload=${base64Url(payload)}")
        }

        assertEquals("pair.error.invalidRoute", error.messageKey)
    }

    @Test
    fun parseCompactTicketDropsWebSocketRouteWithoutUrlEndpoint() {
        val payload = JSONObject()
            .put("v", 1)
            .put("d", "mac-websocket-hostport")
            .put("r", JSONArray().put(
                JSONObject()
                    .put("i", "ws-hostport")
                    .put("k", "websocket")
                    .put("p", 1)
                    .put("e", JSONObject().put("h", "100.64.0.10").put("p", 58465))
            ))

        val error = assertThrows(PairingException::class.java) {
            parser.parse("cmux-ios://attach?payload=${base64Url(payload)}")
        }

        assertEquals("pair.error.noRoutes", error.messageKey)
    }

    @Test
    fun parseFullTicketRejectsLoopbackWebSocketUrlRoute() {
        val payload = JSONObject()
            .put("macDeviceID", "mac-loopback-websocket")
            .put("routes", JSONArray().put(
                JSONObject()
                    .put("id", "websocket")
                    .put("kind", "websocket")
                    .put("priority", 1)
                    .put("endpoint", JSONObject()
                        .put("type", "url")
                        .put("url", "ws://127.0.0.1:58465/mobile"))
            ))

        val error = assertThrows(PairingException::class.java) {
            parser.parse("cmux-ios://attach?payload=${base64Url(payload)}")
        }

        assertEquals("pair.error.loopback", error.messageKey)
    }

    @Test
    fun parseFullTicketRejectsWebSocketUrlWithUserInfo() {
        val payload = JSONObject()
            .put("macDeviceID", "mac-userinfo-websocket")
            .put("routes", JSONArray().put(
                JSONObject()
                    .put("id", "websocket")
                    .put("kind", "websocket")
                    .put("priority", 1)
                    .put("endpoint", JSONObject()
                        .put("type", "url")
                        .put("url", "wss://user:pass@cmux.example.test/mobile"))
            ))

        val error = assertThrows(PairingException::class.java) {
            parser.parse("cmux-ios://attach?payload=${base64Url(payload)}")
        }

        assertEquals("pair.error.invalidRoute", error.messageKey)
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
    fun parseLegacyPairPayloadNormalizesRouteKindCase() {
        val payload = JSONObject()
            .put("host", "100.64.0.9")
            .put("port", 58465)
            .put("transport", " TAILSCALE ")
            .put("mac_device_id", "mac-legacy-uppercase")

        val mac = parser.parse("cmux-ios://pair?payload=${base64Url(payload)}")

        assertEquals("mac-legacy-uppercase", mac.id)
        assertEquals("tailscale", mac.routes.single().kind)
        assertEquals(mac.routes.single(), mac.primaryRoute)
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

    @Test
    fun pairedMacJsonDropsInvalidStoredWebSocketRoutes() {
        val decoded = PairedMac.fromJson(
            JSONObject()
                .put("id", "mac-stored-routes")
                .put("routes", JSONArray()
                    .put(JSONObject()
                        .put("id", "bad-websocket")
                        .put("kind", "websocket")
                        .put("priority", 1)
                        .put("url", "https://cmux.example.test/mobile"))
                    .put(JSONObject()
                        .put("id", "tailscale")
                        .put("kind", "tailscale")
                        .put("host", "100.64.0.9")
                        .put("port", 58465)
                        .put("priority", 10)))
        )

        assertEquals(1, decoded.routes.size)
        assertEquals("tailscale", decoded.routes.single().id)
        assertEquals(decoded.routes.single(), decoded.primaryRoute)
    }

    @Test
    fun pairedMacJsonDropsStoredWebSocketRoutesWithUserInfo() {
        val decoded = PairedMac.fromJson(
            JSONObject()
                .put("id", "mac-stored-userinfo-websocket")
                .put("routes", JSONArray()
                    .put(JSONObject()
                        .put("id", "bad-websocket")
                        .put("kind", "websocket")
                        .put("priority", 1)
                        .put("url", "wss://user:pass@cmux.example.test/mobile"))
                    .put(JSONObject()
                        .put("id", "tailscale")
                        .put("kind", "tailscale")
                        .put("host", "100.64.0.9")
                        .put("port", 58465)
                        .put("priority", 10)))
        )

        assertEquals(1, decoded.routes.size)
        assertEquals("tailscale", decoded.routes.single().id)
        assertEquals(decoded.routes.single(), decoded.primaryRoute)
    }

    @Test
    fun pairedMacJsonFallsBackToWebSocketUrlWhenIdIsMissing() {
        val decoded = PairedMac.fromJson(
            JSONObject()
                .put("routes", JSONArray()
                    .put(JSONObject()
                        .put("id", "websocket")
                        .put("kind", "websocket")
                        .put("priority", 1)
                        .put("url", "wss://cmux.example.test/mobile")))
        )

        assertEquals("wss://cmux.example.test/mobile", decoded.id)
        assertEquals("wss://cmux.example.test/mobile", decoded.primaryRoute?.url)
    }

    @Test
    fun pairedMacJsonDropsHostPortRoutesWithoutValidHostPortEvenWhenUrlIsPresent() {
        val decoded = PairedMac.fromJson(
            JSONObject()
                .put("id", "mac-invalid-hostport")
                .put("routes", JSONArray()
                    .put(JSONObject()
                        .put("id", "bad-tailscale")
                        .put("kind", "tailscale")
                        .put("priority", 1)
                        .put("url", "wss://cmux.example.test/mobile"))
                    .put(JSONObject()
                        .put("id", "debug")
                        .put("kind", "debug_loopback")
                        .put("host", "127.0.0.1")
                        .put("port", 58465)
                        .put("priority", 10)))
        )

        assertEquals(1, decoded.routes.size)
        assertEquals("debug", decoded.routes.single().id)
        assertEquals(decoded.routes.single(), decoded.primaryRoute)
    }

    @Test
    fun pairedMacJsonDropsStoredLoopbackHostPortRoutesUnlessExplicitDebug() {
        val decoded = PairedMac.fromJson(
            JSONObject()
                .put("id", "mac-stored-loopback")
                .put("routes", JSONArray()
                    .put(JSONObject()
                        .put("id", "local-tailscale")
                        .put("kind", "tailscale")
                        .put("host", "127.0.0.1")
                        .put("port", 58465)
                        .put("priority", 1))
                    .put(JSONObject()
                        .put("id", "local-debug")
                        .put("kind", "debug_loopback")
                        .put("host", "127.0.0.1")
                        .put("port", 58465)
                        .put("priority", 2)))
        )

        assertEquals(1, decoded.routes.size)
        assertEquals("local-debug", decoded.routes.single().id)
        assertEquals(decoded.routes.single(), decoded.primaryRoute)
    }

    @Test
    fun pairedMacJsonTrimsStoredRouteKind() {
        val decoded = PairedMac.fromJson(
            JSONObject()
                .put("id", "mac-trimmed-stored-kind")
                .put("routes", JSONArray()
                    .put(JSONObject()
                        .put("id", "stored-tailscale")
                        .put("kind", " tailscale ")
                        .put("host", "100.64.0.9")
                        .put("port", 58465)
                        .put("priority", 1)))
        )

        assertEquals(1, decoded.routes.size)
        assertEquals("tailscale", decoded.routes.single().kind)
        assertEquals(decoded.routes.single(), decoded.primaryRoute)
    }

    @Test
    fun pairedMacJsonNormalizesStoredRouteKindCase() {
        val decoded = PairedMac.fromJson(
            JSONObject()
                .put("id", "mac-uppercase-stored-kind")
                .put("routes", JSONArray()
                    .put(JSONObject()
                        .put("id", "stored-tailscale")
                        .put("kind", " TAILSCALE ")
                        .put("host", "100.64.0.9")
                        .put("port", 58465)
                        .put("priority", 1)))
        )

        assertEquals(1, decoded.routes.size)
        assertEquals("tailscale", decoded.routes.single().kind)
        assertEquals(decoded.routes.single(), decoded.primaryRoute)
    }

    private fun base64Url(json: JSONObject): String {
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(json.toString().toByteArray(Charsets.UTF_8))
    }
}
