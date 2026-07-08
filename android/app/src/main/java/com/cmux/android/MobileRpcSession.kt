package com.cmux.android

import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class MobileRpcSession(
    private val callback: Callback,
    private val stackAccessTokenProvider: StackAccessTokenProvider? = null,
    private val clientFactory: (MobileFrameClient.Callback, CmuxRoute) -> MobileFrameClient = { callback, route ->
        if (route.kind == "websocket") {
            MobileWebSocketClient(callback)
        } else {
            MobileTcpClient(callback)
        }
    }
) : MobileFrameClient.Callback {
    interface Callback {
        fun onConnectionState(state: String, detail: String? = null)
        fun onRpcResult(requestId: Int, method: String, result: JSONObject)
        fun onRpcError(requestId: Int?, method: String?, code: String, message: String)
        fun onPushEvent(type: String, payload: JSONObject)
    }

    private data class PendingCall(
        val method: String,
        val params: JSONObject,
        val sentWithStackAuth: Boolean,
        val retriedAfterAuthRefresh: Boolean = false
    )

    private val nextId = AtomicInteger(1)
    private val pending = ConcurrentHashMap<Int, PendingCall>()
    private var client: MobileFrameClient? = null
    private var activeRoute: CmuxRoute? = null
    private var routeCandidates: List<CmuxRoute> = emptyList()
    private var routeCandidateIndex: Int = -1
    private var openedActiveRoute: Boolean = false
    private var preOpenRouteError: String? = null
    private var allowRouteFailover: Boolean = false
    private var closedNotified: Boolean = false
    private var connectionGeneration: Int = 0
    private var subscribedStreamId: String? = null

    fun connect(route: CmuxRoute) {
        connect(listOf(route))
    }

    fun connect(routes: List<CmuxRoute>) {
        val candidates = routes.supportedRoutes()
        replaceActiveClient("reconnecting")
        if (candidates.isEmpty()) {
            notifyClosed("no supported route")
            return
        }
        routeCandidates = candidates
        routeCandidateIndex = 0
        connectRoute(candidates[routeCandidateIndex])
    }

    fun request(method: String, params: JSONObject = JSONObject()): Int {
        val requestId = nextId.getAndIncrement()
        val request = requestEnvelope(requestId, method, params)
        pending[requestId] = PendingCall(method, params, sentWithStackAuth = request.has("auth"))
        val activeClient = client
        if (activeClient == null) {
            pending.remove(requestId)
            callback.onRpcError(requestId, method, "transport_error", "not connected")
        } else {
            activeClient.sendFrame(request.toString())
            trackSubscriptionRequest(method, params)
        }
        return requestId
    }

    fun close(reason: String = "closed") {
        unsubscribeActiveStream()
        failPending("transport_error", reason)
        closeActiveClient(reason)
        notifyClosed(reason)
    }

    fun shutdown() {
        unsubscribeActiveStream()
        failPending("transport_error", "session shutdown")
        shutdownActiveClient()
    }

    override fun onOpen() {
        openedActiveRoute = true
        preOpenRouteError = null
        callback.onConnectionState("open")
    }

    override fun onFrame(payload: String) {
        val json = runCatching { JSONObject(payload) }.getOrElse {
            callback.onRpcError(null, null, "parse_error", "Invalid JSON from host")
            return
        }
        val id = strictIntOrNull(json, "id")
        if (id == null) {
            if (json.has("id") && !json.isNull("id")) {
                callback.onRpcError(null, null, "parse_error", "Invalid response id from host")
                return
            }
            val topic = json.optString("topic", json.optString("type", "event"))
            val payload = json.optJSONObject("payload") ?: json
            callback.onPushEvent(topic, payload)
            return
        }
        val pendingCall = pending.remove(id)
        val method = pendingCall?.method
        val ok = strictBooleanOrNull(json, "ok")
        if (ok == null) {
            callback.onRpcError(id, method, "parse_error", "Invalid response status from host")
            return
        }
        if (ok) {
            callback.onRpcResult(id, method ?: "unknown", json.optJSONObject("result") ?: JSONObject())
        } else {
            val error = json.optJSONObject("error") ?: JSONObject()
            val code = error.optString("code", "host_error")
            if (pendingCall != null && shouldRetryAfterStackAuthRefresh(pendingCall, code)) {
                retryWithFreshStackToken(id, pendingCall)
                return
            }
            callback.onRpcError(
                id,
                method,
                code,
                error.optString("message", "Host returned an error")
            )
        }
    }

    override fun onClose(reason: String) {
        val detail = preOpenRouteError ?: reason
        preOpenRouteError = null
        if (allowRouteFailover && !openedActiveRoute && connectNextRoute("closed: $detail")) {
            return
        }
        if (openedActiveRoute && connectNextRouteAfterOpen(detail)) {
            return
        }
        failPending("transport_error", detail)
        clearActiveClientState()
        notifyClosed(detail)
    }

    override fun onError(message: String) {
        if (!openedActiveRoute) {
            preOpenRouteError = message
            return
        }
        callback.onRpcError(null, null, "transport_error", message)
        if (connectNextRouteAfterOpen(message)) {
            return
        }
        failPending("transport_error", message)
        clearActiveClientState()
        notifyClosed(message)
    }

    private fun connectRoute(route: CmuxRoute) {
        connectionGeneration += 1
        val generation = connectionGeneration
        openedActiveRoute = false
        preOpenRouteError = null
        allowRouteFailover = true
        closedNotified = false
        activeRoute = route
        callback.onConnectionState("connecting", route.displayEndpoint())
        client = clientFactory(GenerationCallback(generation), route).also { it.connect(route) }
    }

    private fun notifyClosed(reason: String) {
        if (closedNotified) return
        closedNotified = true
        callback.onConnectionState("closed", reason)
    }

    private fun connectNextRoute(reason: String): Boolean {
        val nextIndex = routeCandidateIndex + 1
        if (nextIndex !in routeCandidates.indices) return false
        val closingClient = client
        connectionGeneration += 1
        allowRouteFailover = false
        preOpenRouteError = null
        client = null
        closingClient?.shutdown()
        routeCandidateIndex = nextIndex
        callback.onConnectionState("retrying", reason)
        connectRoute(routeCandidates[nextIndex])
        return true
    }

    private fun connectNextRouteAfterOpen(detail: String): Boolean {
        if (routeCandidateIndex + 1 !in routeCandidates.indices) return false
        failPending("transport_error", detail)
        return connectNextRoute("closed: $detail")
    }

    private fun replaceActiveClient(pendingReason: String) {
        unsubscribeActiveStream()
        failPending("transport_error", pendingReason)
        shutdownActiveClient()
        closedNotified = false
    }

    private fun closeActiveClient(reason: String) {
        val closingClient = client
        clearActiveClientState()
        closingClient?.close(reason)
    }

    private fun shutdownActiveClient() {
        val closingClient = client
        clearActiveClientState()
        closingClient?.shutdown()
    }

    private fun clearActiveClientState() {
        connectionGeneration += 1
        allowRouteFailover = false
        preOpenRouteError = null
        openedActiveRoute = false
        client = null
        activeRoute = null
        routeCandidates = emptyList()
        routeCandidateIndex = -1
        subscribedStreamId = null
    }

    private inner class GenerationCallback(
        private val generation: Int
    ) : MobileFrameClient.Callback {
        override fun onOpen() {
            if (generation == connectionGeneration) this@MobileRpcSession.onOpen()
        }

        override fun onFrame(payload: String) {
            if (generation == connectionGeneration) this@MobileRpcSession.onFrame(payload)
        }

        override fun onClose(reason: String) {
            if (generation == connectionGeneration) this@MobileRpcSession.onClose(reason)
        }

        override fun onError(message: String) {
            if (generation == connectionGeneration) this@MobileRpcSession.onError(message)
        }
    }

    private fun CmuxRoute.displayEndpoint(): String {
        return url ?: "${host}:${port}"
    }

    private fun List<CmuxRoute>.supportedRoutes(): List<CmuxRoute> {
        return filter { it.isSupportedMobileRoute() }
            .sortedWith(compareBy<CmuxRoute> { it.priority }.thenBy { it.id })
    }

    private fun requestEnvelope(requestId: Int, method: String, params: JSONObject): JSONObject {
        val request = JSONObject()
            .put("id", requestId)
            .put("method", method)
            .put("params", params)
        if (activeRoute?.let(MobileRouteAuthPolicy::routeAllowsStackAuth) != true) {
            return request
        }
        val stackAccessToken = stackAccessTokenProvider
            ?.likelyValidAccessToken()
            ?.accessToken
            ?.trim()
        if (!stackAccessToken.isNullOrEmpty()) {
            request.put("auth", JSONObject().put("stack_access_token", stackAccessToken))
        }
        return request
    }

    private fun trackSubscriptionRequest(method: String, params: JSONObject) {
        if (method == "mobile.events.subscribe") {
            subscribedStreamId = params.optString("stream_id").trim().takeIf { it.isNotEmpty() }
        } else if (method == "mobile.events.unsubscribe") {
            val streamId = params.optString("stream_id").trim()
            if (streamId.isNotEmpty() && streamId == subscribedStreamId) {
                subscribedStreamId = null
            }
        }
    }

    private fun unsubscribeActiveStream() {
        val streamId = subscribedStreamId?.takeIf { it.isNotBlank() } ?: return
        val activeClient = client ?: return
        val request = requestEnvelope(
            nextId.getAndIncrement(),
            "mobile.events.unsubscribe",
            JSONObject().put("stream_id", streamId)
        )
        activeClient.sendFrame(request.toString())
        subscribedStreamId = null
    }

    private fun shouldRetryAfterStackAuthRefresh(pendingCall: PendingCall, code: String): Boolean {
        if (!pendingCall.sentWithStackAuth || pendingCall.retriedAfterAuthRefresh) return false
        if (activeRoute?.let(MobileRouteAuthPolicy::routeAllowsStackAuth) != true) return false
        return code == "unauthorized" || code == "invalid_access_token"
    }

    private fun retryWithFreshStackToken(requestId: Int, pendingCall: PendingCall) {
        val freshAccessToken = stackAccessTokenProvider
            ?.forceRefreshAccessToken()
            ?.accessToken
            ?.trim()
        if (freshAccessToken.isNullOrEmpty()) {
            callback.onRpcError(
                requestId,
                pendingCall.method,
                "unauthorized",
                "Stack authorization failed"
            )
            return
        }

        val retry = JSONObject()
            .put("id", requestId)
            .put("method", pendingCall.method)
            .put("params", pendingCall.params)
            .put("auth", JSONObject().put("stack_access_token", freshAccessToken))
        pending[requestId] = pendingCall.copy(sentWithStackAuth = true, retriedAfterAuthRefresh = true)
        val activeClient = client
        if (activeClient == null) {
            pending.remove(requestId)
            callback.onRpcError(requestId, pendingCall.method, "transport_error", "not connected")
        } else {
            activeClient.sendFrame(retry.toString())
        }
    }

    private fun failPending(code: String, message: String) {
        val calls = pending.entries.map { it.key to it.value }
        pending.clear()
        for ((requestId, call) in calls) {
            callback.onRpcError(requestId, call.method, code, message)
        }
    }

    private fun strictIntOrNull(json: JSONObject, name: String): Int? {
        if (!json.has(name) || json.isNull(name)) return null
        return json.opt(name) as? Int
    }

    private fun strictBooleanOrNull(json: JSONObject, name: String): Boolean? {
        if (!json.has(name) || json.isNull(name)) return null
        return json.opt(name) as? Boolean
    }
}
