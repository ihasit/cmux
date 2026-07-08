package com.cmux.android

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileRpcSessionTest {
    @Test
    fun requestSendsStackAccessTokenWhenConfigured() {
        val client = RecordingFrameClient()
        val session = MobileRpcSession(
            callback = NoopCallback,
            stackAccessTokenProvider = FakeStackAccessTokenProvider(accessToken = "  stack-token-1  "),
            clientFactory = { _, _ -> client }
        )
        session.connect(tcpRoute())

        session.request("mobile.workspace.list")

        val sent = JSONObject(client.sentFrames.single())
        assertEquals("mobile.workspace.list", sent.getString("method"))
        assertEquals("stack-token-1", sent.getJSONObject("auth").getString("stack_access_token"))
    }

    @Test
    fun requestSendsStackAccessTokenOverSecureWebSocketRoute() {
        val client = RecordingFrameClient()
        val session = MobileRpcSession(
            callback = NoopCallback,
            stackAccessTokenProvider = FakeStackAccessTokenProvider(accessToken = "stack-token-2"),
            clientFactory = { _, _ -> client }
        )
        session.connect(webSocketRoute("wss://cmux.example.test/mobile"))

        session.request("mobile.workspace.list")

        val sent = JSONObject(client.sentFrames.single())
        assertEquals("stack-token-2", sent.getJSONObject("auth").getString("stack_access_token"))
    }

    @Test
    fun requestOmitsAuthWhenTokenIsMissing() {
        val client = RecordingFrameClient()
        val session = MobileRpcSession(
            callback = NoopCallback,
            stackAccessTokenProvider = FakeStackAccessTokenProvider(accessToken = "   "),
            clientFactory = { _, _ -> client }
        )
        session.connect(tcpRoute())

        session.request("mobile.workspace.list")

        val sent = JSONObject(client.sentFrames.single())
        assertEquals("mobile.workspace.list", sent.getString("method"))
        assertFalse(sent.has("auth"))
    }

    @Test
    fun requestOmitsAuthOnPlainLanRouteEvenWhenTokenIsConfigured() {
        val client = RecordingFrameClient()
        val provider = FakeStackAccessTokenProvider(accessToken = "stack-token-3")
        val session = MobileRpcSession(
            callback = NoopCallback,
            stackAccessTokenProvider = provider,
            clientFactory = { _, _ -> client }
        )
        session.connect(
            CmuxRoute(
                id = "lan",
                kind = "tailscale",
                host = "192.168.1.20",
                port = 58465,
                priority = 10,
                url = null
            )
        )

        session.request("mobile.workspace.list")

        val sent = JSONObject(client.sentFrames.single())
        assertFalse(sent.has("auth"))
        assertEquals(0, provider.likelyValidCalls)
    }

    @Test
    fun requestOmitsAuthOnPlainWebSocketRouteEvenWhenTokenIsConfigured() {
        val client = RecordingFrameClient()
        val provider = FakeStackAccessTokenProvider(accessToken = "stack-token-4")
        val session = MobileRpcSession(
            callback = NoopCallback,
            stackAccessTokenProvider = provider,
            clientFactory = { _, _ -> client }
        )
        session.connect(webSocketRoute("ws://cmux.example.test/mobile"))

        session.request("mobile.workspace.list")

        val sent = JSONObject(client.sentFrames.single())
        assertFalse(sent.has("auth"))
        assertEquals(0, provider.likelyValidCalls)
    }

    @Test
    fun hostStatusCanCarryConfiguredStackAccessTokenForIdentityPreflight() {
        val client = RecordingFrameClient()
        val session = MobileRpcSession(
            callback = NoopCallback,
            stackAccessTokenProvider = FakeStackAccessTokenProvider(accessToken = "status-token"),
            clientFactory = { _, _ -> client }
        )
        session.connect(tcpRoute())

        session.request("mobile.host.status")

        val sent = JSONObject(client.sentFrames.single())
        assertEquals("mobile.host.status", sent.getString("method"))
        assertTrue(sent.has("auth"))
        assertEquals("status-token", sent.getJSONObject("auth").getString("stack_access_token"))
    }

    @Test
    fun requestRejectsOversizedPayloadWithoutSendingFrame() {
        val client = RecordingFrameClient()
        val callback = RecordingCallback()
        val session = MobileRpcSession(
            callback = callback,
            clientFactory = { _, _ -> client }
        )
        session.connect(tcpRoute())

        val requestId = session.request(
            "mobile.terminal.paste_image",
            JSONObject().put("image_base64", "a".repeat(8 * 1024 * 1024 + 1))
        )

        assertTrue(client.sentFrames.isEmpty())
        assertEquals(
            listOf(RecordedError(requestId, "mobile.terminal.paste_image", "payload_too_large", "request frame too large")),
            callback.errors
        )
    }

    @Test
    fun requestUsesLikelyValidRefreshedTokenFromProvider() {
        val client = RecordingFrameClient()
        val provider = FakeStackAccessTokenProvider(accessToken = "fresh-token")
        val session = MobileRpcSession(
            callback = NoopCallback,
            stackAccessTokenProvider = provider,
            clientFactory = { _, _ -> client }
        )
        session.connect(tcpRoute())

        session.request("mobile.workspace.list")

        val sent = JSONObject(client.sentFrames.single())
        assertEquals(1, provider.likelyValidCalls)
        assertEquals(0, provider.forceRefreshCalls)
        assertEquals("fresh-token", sent.getJSONObject("auth").getString("stack_access_token"))
    }

    @Test
    fun unauthorizedResponseRefreshesAndRetriesRequestOnce() {
        val client = RecordingFrameClient()
        val provider = FakeStackAccessTokenProvider(
            accessToken = "stale-token",
            forceRefreshToken = "refreshed-token"
        )
        val callback = RecordingCallback()
        val session = MobileRpcSession(
            callback = callback,
            stackAccessTokenProvider = provider,
            clientFactory = { _, _ -> client }
        )
        session.connect(tcpRoute())

        val requestId = session.request("mobile.workspace.list", JSONObject().put("filter", "open"))
        session.onFrame(
            JSONObject()
                .put("id", requestId)
                .put("ok", false)
                .put("error", JSONObject().put("code", "unauthorized").put("message", "expired"))
                .toString()
        )

        assertEquals(2, client.sentFrames.size)
        assertEquals(1, provider.forceRefreshCalls)
        val retry = JSONObject(client.sentFrames.last())
        assertEquals(requestId, retry.getInt("id"))
        assertEquals("mobile.workspace.list", retry.getString("method"))
        assertEquals("open", retry.getJSONObject("params").getString("filter"))
        assertEquals("refreshed-token", retry.getJSONObject("auth").getString("stack_access_token"))
        assertTrue(callback.errors.isEmpty())
    }

    @Test
    fun unauthorizedResponseDoesNotLoopWhenRefreshRetryAlsoFails() {
        val client = RecordingFrameClient()
        val provider = FakeStackAccessTokenProvider(
            accessToken = "stale-token",
            forceRefreshToken = "refreshed-token"
        )
        val callback = RecordingCallback()
        val session = MobileRpcSession(
            callback = callback,
            stackAccessTokenProvider = provider,
            clientFactory = { _, _ -> client }
        )
        session.connect(tcpRoute())

        val requestId = session.request("mobile.workspace.list")
        session.onFrame(
            JSONObject()
                .put("id", requestId)
                .put("ok", false)
                .put("error", JSONObject().put("code", "unauthorized"))
                .toString()
        )
        session.onFrame(
            JSONObject()
                .put("id", requestId)
                .put("ok", false)
                .put("error", JSONObject().put("code", "unauthorized").put("message", "still unauthorized"))
                .toString()
        )

        assertEquals(2, client.sentFrames.size)
        assertEquals(1, provider.forceRefreshCalls)
        assertEquals(listOf("unauthorized"), callback.errors.map { it.code })
    }

    @Test
    fun oversizedAuthRefreshRetryReportsErrorWithoutSendingFrame() {
        val client = RecordingFrameClient()
        val callback = RecordingCallback()
        val session = MobileRpcSession(
            callback = callback,
            stackAccessTokenProvider = FakeStackAccessTokenProvider(
                accessToken = "stale-token",
                forceRefreshToken = "a".repeat(8 * 1024 * 1024 + 1)
            ),
            clientFactory = { _, _ -> client }
        )
        session.connect(tcpRoute())

        val requestId = session.request("mobile.workspace.list")
        session.onFrame(
            JSONObject()
                .put("id", requestId)
                .put("ok", false)
                .put("error", JSONObject().put("code", "unauthorized"))
                .toString()
        )

        assertEquals(1, client.sentFrames.size)
        assertEquals(
            listOf(RecordedError(requestId, "mobile.workspace.list", "payload_too_large", "request frame too large")),
            callback.errors
        )
    }

    @Test
    fun responseWithNonNumericIdReportsParseError() {
        val callback = RecordingCallback()
        val session = MobileRpcSession(
            callback = callback,
            clientFactory = { _, _ -> RecordingFrameClient() }
        )
        session.connect(tcpRoute())

        session.onFrame(JSONObject().put("id", "not-a-number").put("ok", true).toString())

        assertEquals(
            listOf(RecordedError(null, null, "parse_error", "Invalid response id from host")),
            callback.errors
        )
        assertTrue(callback.results.isEmpty())
    }

    @Test
    fun responseWithUnknownIdIsIgnored() {
        val callback = RecordingCallback()
        val session = MobileRpcSession(
            callback = callback,
            clientFactory = { _, _ -> RecordingFrameClient() }
        )
        session.connect(tcpRoute())

        session.onFrame(JSONObject().put("id", 99).put("ok", true).put("result", JSONObject().put("ignored", true)).toString())

        assertTrue(callback.results.isEmpty())
        assertTrue(callback.errors.isEmpty())
        assertTrue(callback.pushEvents.isEmpty())
    }

    @Test
    fun responseWithNonBooleanOkReportsParseError() {
        val callback = RecordingCallback()
        val session = MobileRpcSession(
            callback = callback,
            clientFactory = { _, _ -> RecordingFrameClient() }
        )
        session.connect(tcpRoute())
        val requestId = session.request("mobile.workspace.list")

        session.onFrame(JSONObject().put("id", requestId).put("ok", "true").toString())

        assertEquals(
            listOf(RecordedError(requestId, "mobile.workspace.list", "parse_error", "Invalid response status from host")),
            callback.errors
        )
        assertTrue(callback.results.isEmpty())
    }

    @Test
    fun successResponseWithNonObjectResultReportsParseError() {
        val callback = RecordingCallback()
        val session = MobileRpcSession(
            callback = callback,
            clientFactory = { _, _ -> RecordingFrameClient() }
        )
        session.connect(tcpRoute())
        val requestId = session.request("mobile.workspace.list")

        session.onFrame(JSONObject().put("id", requestId).put("ok", true).put("result", "not-an-object").toString())

        assertEquals(
            listOf(RecordedError(requestId, "mobile.workspace.list", "parse_error", "Invalid response result from host")),
            callback.errors
        )
        assertTrue(callback.results.isEmpty())
    }

    @Test
    fun errorResponseWithNonObjectErrorReportsParseError() {
        val callback = RecordingCallback()
        val session = MobileRpcSession(
            callback = callback,
            clientFactory = { _, _ -> RecordingFrameClient() }
        )
        session.connect(tcpRoute())
        val requestId = session.request("mobile.workspace.list")

        session.onFrame(JSONObject().put("id", requestId).put("ok", false).put("error", "not-an-object").toString())

        assertEquals(
            listOf(RecordedError(requestId, "mobile.workspace.list", "parse_error", "Invalid response error from host")),
            callback.errors
        )
    }

    @Test
    fun pushEventWithNonStringTopicReportsParseError() {
        val callback = RecordingCallback()
        val session = MobileRpcSession(
            callback = callback,
            clientFactory = { _, _ -> RecordingFrameClient() }
        )
        session.connect(tcpRoute())

        session.onFrame(JSONObject().put("topic", 123).put("payload", JSONObject().put("ok", true)).toString())

        assertEquals(
            listOf(RecordedError(null, null, "parse_error", "Invalid push topic from host")),
            callback.errors
        )
        assertTrue(callback.pushEvents.isEmpty())
    }

    @Test
    fun pushEventWithNonObjectPayloadReportsParseError() {
        val callback = RecordingCallback()
        val session = MobileRpcSession(
            callback = callback,
            clientFactory = { _, _ -> RecordingFrameClient() }
        )
        session.connect(tcpRoute())

        session.onFrame(JSONObject().put("topic", "workspace.updated").put("payload", "not-an-object").toString())

        assertEquals(
            listOf(RecordedError(null, null, "parse_error", "Invalid push payload from host")),
            callback.errors
        )
        assertTrue(callback.pushEvents.isEmpty())
    }

    @Test
    fun remoteCloseFailsPendingRequests() {
        val client = RecordingFrameClient()
        val callback = RecordingCallback()
        val session = MobileRpcSession(
            callback = callback,
            clientFactory = { _, _ -> client }
        )
        session.connect(tcpRoute())

        val requestId = session.request("mobile.terminal.replay")
        session.onClose("network lost")

        assertEquals(
            listOf(RecordedError(requestId, "mobile.terminal.replay", "transport_error", "network lost")),
            callback.errors
        )
    }

    @Test
    fun remoteCloseClearsActiveClientBeforeLaterRequests() {
        val client = RecordingFrameClient()
        val callback = RecordingCallback()
        val session = MobileRpcSession(
            callback = callback,
            clientFactory = { _, _ -> client }
        )
        session.connect(tcpRoute())
        session.onOpen()

        session.onClose("remote closed")
        val requestId = session.request("mobile.workspace.list")

        assertTrue(client.sentFrames.isEmpty())
        assertEquals(
            listOf(RecordedError(requestId, "mobile.workspace.list", "transport_error", "not connected")),
            callback.errors
        )
    }

    @Test
    fun manualCloseFailsPendingRequests() {
        val client = RecordingFrameClient()
        val callback = RecordingCallback()
        val session = MobileRpcSession(
            callback = callback,
            clientFactory = { _, _ -> client }
        )
        session.connect(tcpRoute())

        val requestId = session.request("mobile.workspace.list")
        session.close("closed by user")

        assertEquals(
            listOf(RecordedError(requestId, "mobile.workspace.list", "transport_error", "closed by user")),
            callback.errors
        )
    }

    @Test
    fun manualCloseSendsEventsUnsubscribeBeforeClosingTransport() {
        val client = RecordingFrameClient()
        val session = MobileRpcSession(
            callback = NoopCallback,
            clientFactory = { _, _ -> client }
        )
        session.connect(tcpRoute())
        session.request(
            "mobile.events.subscribe",
            JSONObject()
                .put("stream_id", "stream-android-1")
                .put("topics", JSONArray().put("workspace.updated"))
        )

        session.close("closed by user")

        assertEquals(2, client.sentFrames.size)
        val subscribe = JSONObject(client.sentFrames[0])
        val unsubscribe = JSONObject(client.sentFrames[1])
        assertEquals("mobile.events.subscribe", subscribe.getString("method"))
        assertEquals("mobile.events.unsubscribe", unsubscribe.getString("method"))
        assertEquals("stream-android-1", unsubscribe.getJSONObject("params").getString("stream_id"))
    }

    @Test
    fun manualCloseRejectsOversizedEventsUnsubscribeFrame() {
        val client = RecordingFrameClient()
        val callback = RecordingCallback()
        val provider = SequencedStackAccessTokenProvider(
            "subscribe-token",
            "a".repeat(8 * 1024 * 1024 + 1)
        )
        val session = MobileRpcSession(
            callback = callback,
            stackAccessTokenProvider = provider,
            clientFactory = { _, _ -> client }
        )
        session.connect(tcpRoute())
        session.request(
            "mobile.events.subscribe",
            JSONObject()
                .put("stream_id", "stream-android-1")
                .put("topics", JSONArray().put("workspace.updated"))
        )

        session.close("closed by user")

        assertEquals(1, client.sentFrames.size)
        assertEquals("mobile.events.subscribe", JSONObject(client.sentFrames.single()).getString("method"))
        assertEquals(
            listOf(
                RecordedError(2, "mobile.events.unsubscribe", "payload_too_large", "request frame too large"),
                RecordedError(1, "mobile.events.subscribe", "transport_error", "closed by user")
            ),
            callback.errors
        )
    }

    @Test
    fun manualCloseDoesNotFailOverToNextRoute() {
        val connectedRoutes = mutableListOf<CmuxRoute>()
        val callback = RecordingCallback()
        val session = MobileRpcSession(
            callback = callback,
            clientFactory = { _, route ->
                connectedRoutes.add(route)
                RecordingFrameClient()
            }
        )
        session.connect(listOf(
            tcpRoute().copy(id = "first", priority = 1, host = "100.64.0.10"),
            tcpRoute().copy(id = "second", priority = 2, host = "100.64.0.11")
        ))

        session.close("closed by user")

        assertEquals(listOf("first"), connectedRoutes.map { it.id })
        assertEquals("closed", callback.connectionStates.last().state)
    }

    @Test
    fun shutdownFailsPendingRequests() {
        val client = RecordingFrameClient()
        val callback = RecordingCallback()
        val session = MobileRpcSession(
            callback = callback,
            clientFactory = { _, _ -> client }
        )
        session.connect(tcpRoute())

        val requestId = session.request("mobile.host.status")
        session.shutdown()

        assertEquals(
            listOf(RecordedError(requestId, "mobile.host.status", "transport_error", "session shutdown")),
            callback.errors
        )
    }

    @Test
    fun connectRoutesTriesCandidatesInPriorityOrder() {
        val connectedRoutes = mutableListOf<CmuxRoute>()
        val session = MobileRpcSession(
            callback = NoopCallback,
            clientFactory = { _, route ->
                connectedRoutes.add(route)
                RecordingFrameClient()
            }
        )

        session.connect(listOf(
            tcpRoute().copy(id = "slow", priority = 20, host = "100.64.0.20"),
            webSocketRoute("wss://cmux.example.test/mobile").copy(id = "fast", priority = 1),
            tcpRoute().copy(id = "unsupported", kind = "iroh", priority = 0, host = "100.64.0.30")
        ))

        assertEquals(listOf("fast"), connectedRoutes.map { it.id })
    }

    @Test
    fun closeBeforeOpenFailsOverToNextRoute() {
        val clients = mutableListOf<RecordingFrameClient>()
        val connectedRoutes = mutableListOf<CmuxRoute>()
        val callback = RecordingCallback()
        val session = MobileRpcSession(
            callback = callback,
            clientFactory = { _, route ->
                connectedRoutes.add(route)
                RecordingFrameClient().also { clients.add(it) }
            }
        )

        session.connect(listOf(
            tcpRoute().copy(id = "first", priority = 1, host = "100.64.0.10"),
            tcpRoute().copy(id = "second", priority = 2, host = "100.64.0.11")
        ))
        session.onClose("first refused")

        assertEquals(listOf("first", "second"), connectedRoutes.map { it.id })
        assertEquals(listOf("connecting", "retrying", "connecting"), callback.connectionStates.map { it.state })
        assertTrue(callback.errors.isEmpty())
        assertEquals(1, clients.first().shutdownCalls)
    }

    @Test
    fun errorThenCloseBeforeOpenFailsOverToNextRoute() {
        val connectedRoutes = mutableListOf<CmuxRoute>()
        val callback = RecordingCallback()
        val session = MobileRpcSession(
            callback = callback,
            clientFactory = { _, route ->
                connectedRoutes.add(route)
                RecordingFrameClient()
            }
        )

        session.connect(listOf(
            tcpRoute().copy(id = "first", priority = 1, host = "100.64.0.10"),
            tcpRoute().copy(id = "second", priority = 2, host = "100.64.0.11")
        ))
        session.onError("first failed")
        assertEquals(listOf("first"), connectedRoutes.map { it.id })
        assertTrue(callback.errors.isEmpty())

        session.onClose("connect failed")

        assertEquals(listOf("first", "second"), connectedRoutes.map { it.id })
        assertEquals(RecordedConnectionState("retrying", "closed: first failed"), callback.connectionStates[1])
        assertTrue(callback.errors.isEmpty())
    }

    @Test
    fun failoverIgnoresSynchronousShutdownCloseFromFailedClient() {
        val firstClient = SynchronousCloseFrameClient()
        val connectedRoutes = mutableListOf<CmuxRoute>()
        val callback = RecordingCallback()
        val session = MobileRpcSession(
            callback = callback,
            clientFactory = { frameCallback, route ->
                connectedRoutes.add(route)
                if (connectedRoutes.size == 1) {
                    firstClient.callback = frameCallback
                    firstClient
                } else {
                    RecordingFrameClient()
                }
            }
        )

        session.connect(listOf(
            tcpRoute().copy(id = "first", priority = 1, host = "100.64.0.10"),
            tcpRoute().copy(id = "second", priority = 2, host = "100.64.0.11"),
            tcpRoute().copy(id = "third", priority = 3, host = "100.64.0.12")
        ))
        session.onClose("first refused")

        assertEquals(listOf("first", "second"), connectedRoutes.map { it.id })
        assertEquals(
            listOf("connecting", "retrying", "connecting"),
            callback.connectionStates.map { it.state }
        )
        assertEquals(1, firstClient.shutdownCalls)
    }

    @Test
    fun closeAfterOpenClosesWhenNoNextRouteExists() {
        val connectedRoutes = mutableListOf<CmuxRoute>()
        val callback = RecordingCallback()
        val session = MobileRpcSession(
            callback = callback,
            clientFactory = { _, route ->
                connectedRoutes.add(route)
                RecordingFrameClient()
            }
        )

        session.connect(tcpRoute().copy(id = "first", priority = 1, host = "100.64.0.10"))
        session.onOpen()
        session.onClose("lost after open")

        assertEquals(listOf("first"), connectedRoutes.map { it.id })
        assertEquals("closed", callback.connectionStates.last().state)
    }

    @Test
    fun errorAfterOpenClosesWhenNoNextRouteExists() {
        val client = RecordingFrameClient()
        val callback = RecordingCallback()
        val session = MobileRpcSession(
            callback = callback,
            clientFactory = { _, _ -> client }
        )

        session.connect(tcpRoute().copy(id = "first", priority = 1, host = "100.64.0.10"))
        session.onOpen()
        session.onError("send failed")
        val requestId = session.request("mobile.workspace.list")

        assertTrue(client.sentFrames.isEmpty())
        assertEquals(
            listOf(
                RecordedError(null, null, "transport_error", "send failed"),
                RecordedError(requestId, "mobile.workspace.list", "transport_error", "not connected")
            ),
            callback.errors
        )
        assertEquals("closed", callback.connectionStates.last().state)
        assertEquals("send failed", callback.connectionStates.last().detail)
    }

    @Test
    fun closeAfterOpenFailsOverToNextRouteWhenAvailable() {
        val clients = mutableListOf<RecordingFrameClient>()
        val connectedRoutes = mutableListOf<CmuxRoute>()
        val callback = RecordingCallback()
        val session = MobileRpcSession(
            callback = callback,
            clientFactory = { _, route ->
                connectedRoutes.add(route)
                RecordingFrameClient().also { clients.add(it) }
            }
        )
        session.connect(listOf(
            tcpRoute().copy(id = "first", priority = 1, host = "100.64.0.10"),
            tcpRoute().copy(id = "second", priority = 2, host = "100.64.0.11")
        ))
        session.onOpen()
        val requestId = session.request("mobile.workspace.list")

        session.onClose("lost after open")

        assertEquals(listOf("first", "second"), connectedRoutes.map { it.id })
        assertEquals(
            listOf("connecting", "open", "retrying", "connecting"),
            callback.connectionStates.map { it.state }
        )
        assertEquals(
            listOf(RecordedError(requestId, "mobile.workspace.list", "transport_error", "lost after open")),
            callback.errors
        )
        assertEquals(1, clients.first().shutdownCalls)
    }

    @Test
    fun failoverAfterOpenResubscribesActiveEventStream() {
        val clients = mutableListOf<RecordingFrameClient>()
        val connectedRoutes = mutableListOf<CmuxRoute>()
        val session = MobileRpcSession(
            callback = NoopCallback,
            clientFactory = { _, route ->
                connectedRoutes.add(route)
                RecordingFrameClient().also { clients.add(it) }
            }
        )
        session.connect(listOf(
            tcpRoute().copy(id = "first", priority = 1, host = "100.64.0.10"),
            tcpRoute().copy(id = "second", priority = 2, host = "100.64.0.11")
        ))
        session.onOpen()
        val subscribeId = session.request(
            "mobile.events.subscribe",
            JSONObject()
                .put("stream_id", "stream-android-1")
                .put("topics", JSONArray().put("workspace.updated").put("terminal.frame"))
        )
        session.onFrame(
            JSONObject()
                .put("id", subscribeId)
                .put("ok", true)
                .put("result", JSONObject())
                .toString()
        )

        session.onClose("lost after open")
        session.onOpen()

        assertEquals(listOf("first", "second"), connectedRoutes.map { it.id })
        assertEquals(1, clients[1].sentFrames.size)
        val resubscribe = JSONObject(clients[1].sentFrames.single())
        assertEquals("mobile.events.subscribe", resubscribe.getString("method"))
        assertEquals("stream-android-1", resubscribe.getJSONObject("params").getString("stream_id"))
        assertEquals(
            listOf("workspace.updated", "terminal.frame"),
            resubscribe.getJSONObject("params").getJSONArray("topics").asStrings()
        )
    }

    @Test
    fun errorAfterOpenFailsOverToNextRouteWhenAvailable() {
        val clients = mutableListOf<RecordingFrameClient>()
        val connectedRoutes = mutableListOf<CmuxRoute>()
        val callback = RecordingCallback()
        val session = MobileRpcSession(
            callback = callback,
            clientFactory = { _, route ->
                connectedRoutes.add(route)
                RecordingFrameClient().also { clients.add(it) }
            }
        )
        session.connect(listOf(
            tcpRoute().copy(id = "first", priority = 1, host = "100.64.0.10"),
            tcpRoute().copy(id = "second", priority = 2, host = "100.64.0.11")
        ))
        session.onOpen()
        val requestId = session.request("mobile.workspace.list")

        session.onError("send failed")

        assertEquals(listOf("first", "second"), connectedRoutes.map { it.id })
        assertEquals(
            listOf("connecting", "open", "retrying", "connecting"),
            callback.connectionStates.map { it.state }
        )
        assertEquals(
            listOf(
                RecordedError(null, null, "transport_error", "send failed"),
                RecordedError(requestId, "mobile.workspace.list", "transport_error", "send failed")
            ),
            callback.errors
        )
        assertEquals(1, clients.first().shutdownCalls)
    }

    @Test
    fun connectRoutesReportsClosedWhenNoSupportedRouteExists() {
        val callback = RecordingCallback()
        val session = MobileRpcSession(
            callback = callback,
            clientFactory = { _, _ -> RecordingFrameClient() }
        )

        session.connect(listOf(tcpRoute().copy(id = "iroh", kind = "iroh")))

        assertEquals(listOf(RecordedConnectionState("closed", "no supported route")), callback.connectionStates)
    }

    @Test
    fun replacingConnectionIgnoresSynchronousShutdownCloseFromOldClient() {
        val firstClient = SynchronousCloseFrameClient()
        val secondClient = RecordingFrameClient()
        val connectedRoutes = mutableListOf<CmuxRoute>()
        val callback = RecordingCallback()
        val session = MobileRpcSession(
            callback = callback,
            clientFactory = { callback, route ->
                connectedRoutes.add(route)
                if (connectedRoutes.size == 1) {
                    firstClient.callback = callback
                    firstClient
                } else {
                    secondClient
                }
            }
        )
        session.connect(tcpRoute().copy(id = "first", priority = 1, host = "100.64.0.10"))

        session.connect(listOf(
            tcpRoute().copy(id = "second", priority = 1, host = "100.64.0.11"),
            tcpRoute().copy(id = "third", priority = 2, host = "100.64.0.12")
        ))

        assertEquals(listOf("first", "second"), connectedRoutes.map { it.id })
        assertEquals(
            listOf(
                RecordedConnectionState("connecting", "100.64.0.10:58465"),
                RecordedConnectionState("connecting", "100.64.0.11:58465")
            ),
            callback.connectionStates
        )
        assertEquals(1, firstClient.shutdownCalls)
    }

    @Test
    fun manualCloseIgnoresSynchronousClientCloseCallback() {
        val client = SynchronousCloseFrameClient()
        val callback = RecordingCallback()
        val session = MobileRpcSession(
            callback = callback,
            clientFactory = { frameCallback, _ ->
                client.callback = frameCallback
                client
            }
        )
        session.connect(listOf(
            tcpRoute().copy(id = "first", priority = 1, host = "100.64.0.10"),
            tcpRoute().copy(id = "second", priority = 2, host = "100.64.0.11")
        ))

        session.close("closed by user")

        assertEquals(1, client.closeCalls)
        assertEquals(
            listOf(
                RecordedConnectionState("connecting", "100.64.0.10:58465"),
                RecordedConnectionState("closed", "closed by user")
            ),
            callback.connectionStates
        )
    }

    private fun tcpRoute(): CmuxRoute {
        return CmuxRoute(
            id = "tailscale",
            kind = "tailscale",
            host = "100.64.0.5",
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

    private fun JSONArray.asStrings(): List<String> {
        return (0 until length()).map { getString(it) }
    }

    private class RecordingFrameClient : MobileFrameClient {
        val sentFrames = mutableListOf<String>()
        var shutdownCalls = 0
            private set

        override fun connect(route: CmuxRoute) = Unit

        override fun sendFrame(payload: String) {
            sentFrames.add(payload)
        }

        override fun close(reason: String) = Unit

        override fun shutdown() {
            shutdownCalls += 1
        }
    }

    private class SynchronousCloseFrameClient : MobileFrameClient {
        var callback: MobileFrameClient.Callback? = null
        var closeCalls = 0
            private set
        var shutdownCalls = 0
            private set

        override fun connect(route: CmuxRoute) = Unit

        override fun sendFrame(payload: String) = Unit

        override fun close(reason: String) {
            closeCalls += 1
            callback?.onClose(reason)
        }

        override fun shutdown() {
            shutdownCalls += 1
            callback?.onClose("activity destroyed")
        }
    }

    private class FakeStackAccessTokenProvider(
        private val accessToken: String?,
        private val forceRefreshToken: String? = null
    ) : StackAccessTokenProvider {
        var likelyValidCalls = 0
            private set
        var forceRefreshCalls = 0
            private set

        override fun likelyValidAccessToken(): StackTokenPair {
            likelyValidCalls += 1
            return StackTokenPair(refreshToken = "refresh-token", accessToken = accessToken)
        }

        override fun forceRefreshAccessToken(): StackTokenPair {
            forceRefreshCalls += 1
            return StackTokenPair(refreshToken = "refresh-token", accessToken = forceRefreshToken)
        }
    }

    private class SequencedStackAccessTokenProvider(
        vararg accessTokens: String?
    ) : StackAccessTokenProvider {
        private val tokens = ArrayDeque(accessTokens.toList())

        override fun likelyValidAccessToken(): StackTokenPair {
            return StackTokenPair(refreshToken = "refresh-token", accessToken = tokens.removeFirstOrNull())
        }

        override fun forceRefreshAccessToken(): StackTokenPair {
            return StackTokenPair(refreshToken = "refresh-token", accessToken = tokens.removeFirstOrNull())
        }
    }

    private data class RecordedError(
        val requestId: Int?,
        val method: String?,
        val code: String,
        val message: String
    )

    private data class RecordedConnectionState(
        val state: String,
        val detail: String?
    )

    private data class RecordedPushEvent(
        val type: String,
        val payload: JSONObject
    )

    private class RecordingCallback : MobileRpcSession.Callback {
        val errors = mutableListOf<RecordedError>()
        val connectionStates = mutableListOf<RecordedConnectionState>()
        val results = mutableListOf<Int>()
        val pushEvents = mutableListOf<RecordedPushEvent>()

        override fun onConnectionState(state: String, detail: String?) {
            connectionStates.add(RecordedConnectionState(state, detail))
        }

        override fun onRpcResult(requestId: Int, method: String, result: JSONObject) {
            results.add(requestId)
        }

        override fun onRpcError(requestId: Int?, method: String?, code: String, message: String) {
            errors.add(RecordedError(requestId, method, code, message))
        }

        override fun onPushEvent(type: String, payload: JSONObject) {
            pushEvents.add(RecordedPushEvent(type, payload))
        }
    }

    private object NoopCallback : MobileRpcSession.Callback {
        override fun onConnectionState(state: String, detail: String?) = Unit

        override fun onRpcResult(requestId: Int, method: String, result: JSONObject) = Unit

        override fun onRpcError(requestId: Int?, method: String?, code: String, message: String) = Unit

        override fun onPushEvent(type: String, payload: JSONObject) = Unit
    }
}
