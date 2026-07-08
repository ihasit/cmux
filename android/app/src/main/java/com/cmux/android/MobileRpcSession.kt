package com.cmux.android

import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class MobileRpcSession(private val callback: Callback) : MobileFrameClient.Callback {
    interface Callback {
        fun onConnectionState(state: String, detail: String? = null)
        fun onRpcResult(requestId: Int, method: String, result: JSONObject)
        fun onRpcError(requestId: Int?, method: String?, code: String, message: String)
        fun onPushEvent(type: String, payload: JSONObject)
    }

    private data class PendingCall(val method: String)

    private val nextId = AtomicInteger(1)
    private val pending = ConcurrentHashMap<Int, PendingCall>()
    private var client: MobileFrameClient? = null

    fun connect(route: CmuxRoute) {
        callback.onConnectionState("connecting", route.displayEndpoint())
        client?.shutdown()
        client = clientFor(route).also { it.connect(route) }
    }

    fun request(method: String, params: JSONObject = JSONObject()): Int {
        val requestId = nextId.getAndIncrement()
        pending[requestId] = PendingCall(method)
        val request = JSONObject()
            .put("id", requestId)
            .put("method", method)
            .put("params", params)
        val activeClient = client
        if (activeClient == null) {
            pending.remove(requestId)
            callback.onRpcError(requestId, method, "transport_error", "not connected")
        } else {
            activeClient.sendFrame(request.toString())
        }
        return requestId
    }

    fun close(reason: String = "closed") {
        pending.clear()
        client?.close(reason)
    }

    fun shutdown() {
        pending.clear()
        client?.shutdown()
        client = null
    }

    override fun onOpen() {
        callback.onConnectionState("open")
    }

    override fun onFrame(payload: String) {
        val json = runCatching { JSONObject(payload) }.getOrElse {
            callback.onRpcError(null, null, "parse_error", "Invalid JSON from host")
            return
        }
        val id = if (json.has("id") && !json.isNull("id")) json.optInt("id") else null
        if (id == null) {
            val topic = json.optString("topic", json.optString("type", "event"))
            val payload = json.optJSONObject("payload") ?: json
            callback.onPushEvent(topic, payload)
            return
        }
        val pendingCall = pending.remove(id)
        val method = pendingCall?.method
        if (json.optBoolean("ok", false)) {
            callback.onRpcResult(id, method ?: "unknown", json.optJSONObject("result") ?: JSONObject())
        } else {
            val error = json.optJSONObject("error") ?: JSONObject()
            callback.onRpcError(
                id,
                method,
                error.optString("code", "host_error"),
                error.optString("message", "Host returned an error")
            )
        }
    }

    override fun onClose(reason: String) {
        pending.clear()
        callback.onConnectionState("closed", reason)
    }

    override fun onError(message: String) {
        callback.onRpcError(null, null, "transport_error", message)
    }

    private fun clientFor(route: CmuxRoute): MobileFrameClient {
        return if (route.kind == "websocket") {
            MobileWebSocketClient(this)
        } else {
            MobileTcpClient(this)
        }
    }

    private fun CmuxRoute.displayEndpoint(): String {
        return url ?: "${host}:${port}"
    }
}
