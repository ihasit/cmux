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

    private data class ActiveSubscription(
        val streamId: String,
        val params: JSONObject
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
    private var activeSubscription: ActiveSubscription? = null
    private val pendingSubscriptions = ConcurrentHashMap<Int, ActiveSubscription>()

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
        } else if (sendRequestFrame(activeClient, requestId, method, request)) {
            trackSubscriptionRequest(requestId, method, params)
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
        val subscriptionBeforeOpenCallback = activeSubscription
        callback.onConnectionState("open")
        if (activeSubscription == subscriptionBeforeOpenCallback) {
            resubscribeActiveStream()
        }
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
            val topic = pushTopicOrNull(json)
            if (topic == null) {
                callback.onRpcError(null, null, "parse_error", "Invalid push topic from host")
                return
            }
            val pushPayload = json.optJSONObject("payload")
            if (pushPayload == null && json.has("payload") && !json.isNull("payload")) {
                callback.onRpcError(null, null, "parse_error", "Invalid push payload from host")
                return
            }
            callback.onPushEvent(topic, pushPayload ?: json)
            return
        }
        val pendingCall = pending.remove(id)
        val method = pendingCall?.method ?: return
        val ok = strictBooleanOrNull(json, "ok")
        if (ok == null) {
            discardSubscriptionRequest(id)
            callback.onRpcError(id, method, "parse_error", "Invalid response status from host")
            return
        }
        if (ok) {
            val result = json.optJSONObject("result")
            if (result == null && json.has("result") && !json.isNull("result")) {
                discardSubscriptionRequest(id)
                callback.onRpcError(id, method, "parse_error", "Invalid response result from host")
                return
            }
            confirmSubscriptionRequest(id)
            callback.onRpcResult(id, method, result ?: JSONObject())
        } else {
            val error = json.optJSONObject("error")
            if (error == null && json.has("error") && !json.isNull("error")) {
                discardSubscriptionRequest(id)
                callback.onRpcError(id, method, "parse_error", "Invalid response error from host")
                return
            }
            val errorObject = error ?: JSONObject()
            val code = errorObject.optString("code", "host_error")
            if (shouldRetryAfterStackAuthRefresh(pendingCall, code)) {
                retryWithFreshStackToken(id, pendingCall)
                return
            }
            discardSubscriptionRequest(id)
            callback.onRpcError(
                id,
                method,
                code,
                errorObject.optString("message", "Host returned an error")
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
        activeSubscription = null
        pendingSubscriptions.clear()
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

    private fun trackSubscriptionRequest(requestId: Int, method: String, params: JSONObject) {
        if (method == "mobile.events.subscribe") {
            val streamId = params.optString("stream_id").trim()
            val subscription = streamId.takeIf { it.isNotEmpty() }
                ?.let { ActiveSubscription(it, JSONObject(params.toString())) }
            if (subscription != null) {
                pendingSubscriptions[requestId] = subscription
            }
        } else if (method == "mobile.events.unsubscribe") {
            val streamId = params.optString("stream_id").trim()
            if (streamId.isNotEmpty() && streamId == activeSubscription?.streamId) {
                activeSubscription = null
            }
        }
    }

    private fun confirmSubscriptionRequest(requestId: Int) {
        val subscription = pendingSubscriptions.remove(requestId) ?: return
        activeSubscription = subscription
    }

    private fun discardSubscriptionRequest(requestId: Int) {
        pendingSubscriptions.remove(requestId)
    }

    private fun unsubscribeActiveStream() {
        val subscription = activeSubscription
            ?: pendingSubscriptions.entries.maxByOrNull { it.key }?.value
            ?: return
        val streamId = subscription.streamId.takeIf { it.isNotBlank() } ?: return
        val activeClient = client ?: return
        val requestId = nextId.getAndIncrement()
        val request = requestEnvelope(
            requestId,
            "mobile.events.unsubscribe",
            JSONObject().put("stream_id", streamId)
        )
        sendRequestFrame(activeClient, requestId, "mobile.events.unsubscribe", request)
        activeSubscription = null
        pendingSubscriptions.clear()
    }

    private fun resubscribeActiveStream() {
        val subscription = activeSubscription ?: return
        val activeClient = client ?: return
        val requestId = nextId.getAndIncrement()
        val request = requestEnvelope(
            requestId,
            "mobile.events.subscribe",
            JSONObject(subscription.params.toString())
        )
        pending[requestId] = PendingCall(
            method = "mobile.events.subscribe",
            params = subscription.params,
            sentWithStackAuth = request.has("auth")
        )
        sendRequestFrame(activeClient, requestId, "mobile.events.subscribe", request)
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
            discardSubscriptionRequest(requestId)
            callback.onRpcError(requestId, pendingCall.method, "transport_error", "not connected")
        } else if (!sendRequestFrame(activeClient, requestId, pendingCall.method, retry)) {
            pending.remove(requestId)
            discardSubscriptionRequest(requestId)
        }
    }

    private fun sendRequestFrame(activeClient: MobileFrameClient, requestId: Int, method: String, request: JSONObject): Boolean {
        val framePayload = request.toString()
        if (framePayload.toByteArray(Charsets.UTF_8).size > MAX_FRAME_BYTES) {
            pending.remove(requestId)
            callback.onRpcError(requestId, method, "payload_too_large", "request frame too large")
            return false
        }
        return runCatching {
            activeClient.sendFrame(framePayload)
        }.fold(
            onSuccess = { true },
            onFailure = { error ->
                pending.remove(requestId)
                discardSubscriptionRequest(requestId)
                callback.onRpcError(
                    requestId,
                    method,
                    "transport_error",
                    error.message ?: error.javaClass.simpleName
                )
                false
            }
        )
    }

    private fun failPending(code: String, message: String) {
        val calls = pending.entries.map { it.key to it.value }
        pending.clear()
        pendingSubscriptions.clear()
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

    private fun pushTopicOrNull(json: JSONObject): String? {
        if (json.has("topic") && !json.isNull("topic")) {
            return json.opt("topic") as? String
        }
        if (json.has("type") && !json.isNull("type")) {
            return json.opt("type") as? String
        }
        return "event"
    }

    private companion object {
        const val MAX_FRAME_BYTES = 8 * 1024 * 1024
    }
}
