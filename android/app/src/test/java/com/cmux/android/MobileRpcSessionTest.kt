package com.cmux.android

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
            stackAccessTokenProvider = { "  stack-token-1  " },
            clientFactory = { _, _ -> client }
        )
        session.connect(tcpRoute())

        session.request("mobile.workspace.list")

        val sent = JSONObject(client.sentFrames.single())
        assertEquals("mobile.workspace.list", sent.getString("method"))
        assertEquals("stack-token-1", sent.getJSONObject("auth").getString("stack_access_token"))
    }

    @Test
    fun requestOmitsAuthWhenTokenIsMissing() {
        val client = RecordingFrameClient()
        val session = MobileRpcSession(
            callback = NoopCallback,
            stackAccessTokenProvider = { "   " },
            clientFactory = { _, _ -> client }
        )
        session.connect(tcpRoute())

        session.request("mobile.workspace.list")

        val sent = JSONObject(client.sentFrames.single())
        assertEquals("mobile.workspace.list", sent.getString("method"))
        assertFalse(sent.has("auth"))
    }

    @Test
    fun hostStatusCanCarryConfiguredStackAccessTokenForIdentityPreflight() {
        val client = RecordingFrameClient()
        val session = MobileRpcSession(
            callback = NoopCallback,
            stackAccessTokenProvider = { "status-token" },
            clientFactory = { _, _ -> client }
        )
        session.connect(tcpRoute())

        session.request("mobile.host.status")

        val sent = JSONObject(client.sentFrames.single())
        assertEquals("mobile.host.status", sent.getString("method"))
        assertTrue(sent.has("auth"))
        assertEquals("status-token", sent.getJSONObject("auth").getString("stack_access_token"))
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

    private class RecordingFrameClient : MobileFrameClient {
        val sentFrames = mutableListOf<String>()

        override fun connect(route: CmuxRoute) = Unit

        override fun sendFrame(payload: String) {
            sentFrames.add(payload)
        }

        override fun close(reason: String) = Unit

        override fun shutdown() = Unit
    }

    private object NoopCallback : MobileRpcSession.Callback {
        override fun onConnectionState(state: String, detail: String?) = Unit

        override fun onRpcResult(requestId: Int, method: String, result: JSONObject) = Unit

        override fun onRpcError(requestId: Int?, method: String?, code: String, message: String) = Unit

        override fun onPushEvent(type: String, payload: JSONObject) = Unit
    }
}
