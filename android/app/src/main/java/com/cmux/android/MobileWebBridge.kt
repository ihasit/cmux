package com.cmux.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class MobileWebBridge(private val context: Context, private val webView: WebView) : MobileRpcSession.Callback {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val store = PairedMacStore(context)
    private val authStore = MobileAuthStore(context)
    private val notificationBridge = MobileNotificationBridge(context)
    private val parser = PairingParser()
    private val authCallbackParser = AuthCallbackParser()
    private var stackAccessToken: String? = authStore.stackAccessToken()
    private val stackTokenProvider = StoredStackAccessTokenProvider(authStore) {
        stackAccessToken = authStore.stackAccessToken()
        emit("auth", authStateJson())
    }
    private val reconnectPolicy = MobileReconnectPolicy()
    private val session = MobileRpcSession(
        callback = this,
        stackAccessTokenProvider = stackTokenProvider
    )
    private var activeMac: PairedMac? = null
    private var reconnectRunnable: Runnable? = null
    private var userRequestedDisconnect = false
    private var pageReady = false
    private var pendingPairingURL: String? = null
    private var pendingAuthState: String? = null
    private var streamId = UUID.randomUUID().toString()
    private var hostStatusCapabilities = MobileEventTopics.HostStatusCapabilities()

    @JavascriptInterface
    fun initialState() {
        pageReady = true
        val macs = store.list()
        emit("pairedMacs", JSONObject().put("macs", pairedMacsJson(macs)))
        emit("auth", authStateJson())
        emitNotificationPermissionState(requested = false)
        pendingPairingURL?.let { rawValue ->
            pendingPairingURL = null
            pair(rawValue)
            return
        }
        if (activeMac == null) {
            PairedMacSelector.startupMac(macs)?.let { mac ->
                activeMac = mac
                userRequestedDisconnect = false
                reconnectPolicy.reset()
                connectMac(mac)
            }
        }
    }

    @JavascriptInterface
    fun saveStackAccessToken(token: String) {
        val saved = authStore.saveStackAccessToken(token)
        if (!saved) {
            emit("error", JSONObject().put("message_key", "auth.error.empty"))
            return
        }
        stackAccessToken = authStore.stackAccessToken()
        emit("auth", authStateJson())
        emit("toast", JSONObject().put("message_key", "auth.saved"))
        if (activeMac != null) {
            session.request("mobile.host.status")
            session.request("mobile.workspace.list")
        }
    }

    @JavascriptInterface
    fun startStackSignIn() {
        val state = UUID.randomUUID().toString()
        pendingAuthState = state
        val callback = Uri.Builder()
            .scheme("cmux-ios")
            .authority("auth-callback")
            .appendQueryParameter("cmux_auth_state", state)
            .build()
        val afterSignIn = Uri.parse(authOrigin())
            .buildUpon()
            .appendEncodedPath("handler/after-sign-in")
            .appendQueryParameter("native_app_return_to", callback.toString())
            .build()
        val signInUrl = Uri.parse(authOrigin())
            .buildUpon()
            .appendEncodedPath("handler/native-sign-in")
            .appendQueryParameter("after_auth_return_to", afterSignIn.toString())
            .build()
        val intent = Intent(Intent.ACTION_VIEW, signInUrl)
        mainHandler.post {
            runCatching { context.startActivity(intent) }
                .onFailure {
                    pendingAuthState = null
                    emit("error", JSONObject().put("message_key", "auth.error.openSignIn"))
                }
        }
    }

    @JavascriptInterface
    fun clearStackAccessToken() {
        authStore.clearStackAccessToken()
        stackAccessToken = null
        emit("auth", authStateJson())
        emit("toast", JSONObject().put("message_key", "auth.cleared"))
    }

    @JavascriptInterface
    fun requestNotificationPermission() {
        mainHandler.post {
            (context as? MainActivity)?.requestNotificationPermission()
                ?: emitNotificationPermissionState(requested = true)
        }
    }

    fun handleNotificationPermissionResult(granted: Boolean) {
        emitNotificationPermissionState(requested = true, overrideEnabled = granted)
    }

    fun handlePairingURL(rawValue: String?) {
        val value = rawValue?.trim().orEmpty()
        if (value.isEmpty()) return
        if (handleAuthCallback(value)) return
        if (pageReady) {
            pair(value)
        } else {
            pendingPairingURL = value
        }
    }

    private fun handleAuthCallback(rawValue: String): Boolean {
        if (!authCallbackParser.isAuthCallback(rawValue)) return false
        val expectedState = pendingAuthState
        if (expectedState == null) {
            emit("error", JSONObject().put("message_key", "auth.error.callback"))
            return true
        }
        val tokens = authCallbackParser.parse(rawValue, expectedState)
        if (tokens == null) {
            pendingAuthState = null
            emit("error", JSONObject().put("message_key", "auth.error.callback"))
            return true
        }
        pendingAuthState = null
        if (!authStore.saveStackTokens(tokens)) {
            emit("error", JSONObject().put("message_key", "auth.error.callback"))
            return true
        }
        stackAccessToken = authStore.stackAccessToken()
        emit("auth", authStateJson())
        emit("toast", JSONObject().put("message_key", "auth.signedIn"))
        if (activeMac != null) {
            session.request("mobile.host.status")
            session.request("mobile.workspace.list")
        }
        return true
    }

    @JavascriptInterface
    fun pair(rawValue: String) {
        runCatching {
            val mac = parser.parse(rawValue)
            userRequestedDisconnect = false
            cancelReconnect()
            reconnectPolicy.reset()
            activeMac = mac
            emit("pairedMacs", JSONObject().put("macs", pairedMacsJson(store.save(mac))))
            emit("paired", JSONObject().put("mac", mac.toJson()))
            connectMac(mac)
        }.onFailure { error ->
            val messageKey = (error as? PairingException)?.messageKey ?: "pair.error.failed"
            emit("error", JSONObject().put("message_key", messageKey))
        }
    }

    @JavascriptInterface
    fun scanPairingCode() {
        mainHandler.post {
            (context as? MainActivity)?.startPairingScan()
                ?: emit("error", JSONObject().put("message_key", "pair.scanUnavailable"))
        }
    }

    fun handleScannedPairingCode(rawValue: String?) {
        val value = rawValue?.trim().orEmpty()
        if (value.isEmpty()) {
            emit("error", JSONObject().put("message_key", "pair.scanCanceled"))
            return
        }
        pair(value)
    }

    @JavascriptInterface
    fun connect(macId: String) {
        val mac = store.list().firstOrNull { it.id == macId }
        if (mac == null) {
            emit("error", JSONObject().put("message_key", "paired.notFound"))
            return
        }
        activeMac = mac
        userRequestedDisconnect = false
        cancelReconnect()
        reconnectPolicy.reset()
        connectMac(mac)
    }

    @JavascriptInterface
    fun forget(macId: String) {
        if (activeMac?.id == macId) {
            userRequestedDisconnect = true
            cancelReconnect()
            session.close("forgotten")
            activeMac = null
        }
        emit("pairedMacs", JSONObject().put("macs", pairedMacsJson(store.forget(macId))))
    }

    @JavascriptInterface
    fun refreshWorkspaces() {
        session.request("mobile.workspace.list")
    }

    @JavascriptInterface
    fun createWorkspace() {
        session.request("workspace.create", MobileRpcParams.createWorkspace())
    }

    @JavascriptInterface
    fun submitDogfoodFeedback(text: String, terminalText: String, buildStamp: String) {
        session.request(
            "dogfood.feedback.submit",
            MobileRpcParams.dogfoodFeedback(text, terminalText, buildStamp)
        )
    }

    @JavascriptInterface
    fun listChatSessions(workspaceId: String) {
        session.request("mobile.chat.sessions", MobileRpcParams.chatSessions(workspaceId))
    }

    @JavascriptInterface
    fun loadChatHistory(sessionId: String, limit: Int) {
        session.request("mobile.chat.history", MobileRpcParams.chatHistory(sessionId, limit))
    }

    @JavascriptInterface
    fun sendChatMessage(sessionId: String, text: String) {
        session.request("mobile.chat.send", MobileRpcParams.chatSend(sessionId, text))
    }

    @JavascriptInterface
    fun interruptChat(sessionId: String, hard: Boolean) {
        session.request("mobile.chat.interrupt", MobileRpcParams.chatInterrupt(sessionId, hard))
    }

    @JavascriptInterface
    fun answerChat(sessionId: String, optionIndex: Int) {
        session.request("mobile.chat.answer", MobileRpcParams.chatAnswer(sessionId, optionIndex))
    }

    @JavascriptInterface
    fun renameWorkspace(workspaceId: String, title: String) {
        session.request(
            "workspace.action",
            MobileRpcParams.workspaceAction(workspaceId, "rename")
                .put("title", title.trim())
        )
    }

    @JavascriptInterface
    fun setWorkspacePinned(workspaceId: String, pinned: Boolean) {
        session.request(
            "workspace.action",
            MobileRpcParams.workspaceAction(workspaceId, if (pinned) "pin" else "unpin")
        )
    }

    @JavascriptInterface
    fun setWorkspaceUnread(workspaceId: String, unread: Boolean) {
        session.request(
            "workspace.action",
            MobileRpcParams.workspaceAction(workspaceId, if (unread) "mark_unread" else "mark_read")
        )
    }

    @JavascriptInterface
    fun closeWorkspace(workspaceId: String) {
        session.request(
            "workspace.close",
            MobileRpcParams.closeWorkspace(workspaceId)
        )
    }

    @JavascriptInterface
    fun reconcileNotifications(deliveredIdsJson: String) {
        val deliveredIds = runCatching { JSONArray(deliveredIdsJson) }.getOrElse { JSONArray() }
        session.request(
            "notification.reconcile",
            MobileRpcParams.reconcileNotifications(deliveredIds)
        )
    }

    @JavascriptInterface
    fun dismissNotifications(notificationIdsJson: String) {
        val notificationIds = runCatching { JSONArray(notificationIdsJson) }.getOrElse { JSONArray() }
        session.request(
            "notification.dismiss",
            MobileRpcParams.dismissNotifications(notificationIds)
        )
    }

    @JavascriptInterface
    fun setWorkspaceGroupCollapsed(groupId: String, collapsed: Boolean) {
        session.request(
            if (collapsed) "workspace.group.collapse" else "workspace.group.expand",
            MobileRpcParams.workspaceGroup(groupId)
        )
    }

    @JavascriptInterface
    fun createTerminal(workspaceId: String) {
        session.request("mobile.terminal.create", MobileRpcParams.createTerminal(workspaceId))
    }

    @JavascriptInterface
    fun replayTerminal(workspaceId: String, terminalId: String, columns: Int, rows: Int) {
        session.request(
            "mobile.terminal.replay",
            MobileRpcParams.terminalViewport(workspaceId, terminalId, columns, rows)
        )
    }

    @JavascriptInterface
    fun reportViewport(workspaceId: String, terminalId: String, columns: Int, rows: Int) {
        session.request(
            "mobile.terminal.viewport",
            MobileRpcParams.terminalViewport(workspaceId, terminalId, columns, rows)
        )
    }

    @JavascriptInterface
    fun clearViewport(workspaceId: String, terminalId: String) {
        session.request(
            "mobile.terminal.viewport",
            MobileRpcParams.clearTerminalViewport(workspaceId, terminalId)
        )
    }

    @JavascriptInterface
    fun sendInput(workspaceId: String, terminalId: String, text: String, columns: Int, rows: Int) {
        session.request(
            "mobile.terminal.input",
            MobileRpcParams.terminalInput(workspaceId, terminalId, text, columns, rows)
        )
    }

    @JavascriptInterface
    fun pasteText(workspaceId: String, terminalId: String, text: String, submitKey: String, columns: Int, rows: Int) {
        session.request(
            "mobile.terminal.paste",
            MobileRpcParams.terminalPaste(workspaceId, terminalId, text, submitKey, columns, rows)
        )
    }

    @JavascriptInterface
    fun pasteImage(
        workspaceId: String,
        terminalId: String,
        imageBase64: String,
        imageFormat: String,
        columns: Int,
        rows: Int
    ) {
        session.request(
            "mobile.terminal.paste_image",
            MobileRpcParams.terminalPasteImage(workspaceId, terminalId, imageBase64, imageFormat, columns, rows)
        )
    }

    @JavascriptInterface
    fun scrollTerminal(
        workspaceId: String,
        terminalId: String,
        deltaLines: Double,
        column: Int,
        row: Int,
        maxScrollbackRows: Int,
        columns: Int,
        rows: Int
    ) {
        session.request(
            "mobile.terminal.scroll",
            MobileRpcParams.terminalScroll(
                workspaceId = workspaceId,
                terminalId = terminalId,
                deltaLines = deltaLines,
                column = column,
                row = row,
                maxScrollbackRows = maxScrollbackRows,
                columns = columns,
                rows = rows
            )
        )
    }

    @JavascriptInterface
    fun clickTerminal(workspaceId: String, terminalId: String, column: Int, row: Int) {
        session.request(
            "mobile.terminal.mouse",
            MobileRpcParams.terminalMouse(workspaceId, terminalId, column, row)
        )
    }

    @JavascriptInterface
    fun closeConnection() {
        userRequestedDisconnect = true
        cancelReconnect()
        session.close("closed by webview")
    }

    fun close(reason: String = "activity destroyed") {
        userRequestedDisconnect = true
        cancelReconnect()
        session.close(reason)
        session.shutdown()
    }

    override fun onConnectionState(state: String, detail: String?) {
        if (state != "open") {
            notificationBridge.applyUnreadCount(0)
        }
        emit("connection", JSONObject().put("state", state).put("detail", detail))
        if (state == "open") {
            reconnectPolicy.reset()
            cancelReconnect()
            userRequestedDisconnect = false
            hostStatusCapabilities = MobileEventTopics.HostStatusCapabilities()
            streamId = UUID.randomUUID().toString()
            subscribeToEvents(hostStatusCapabilities)
            session.request("mobile.host.status")
            session.request("mobile.workspace.list")
        } else if (state == "closed") {
            scheduleReconnectIfNeeded(detail)
        }
    }

    override fun onRpcResult(requestId: Int, method: String, result: JSONObject) {
        if (method == "mobile.host.status") {
            hostStatusCapabilities = result.hostStatusCapabilities()
            subscribeToEvents(hostStatusCapabilities)
        }
        emit("rpcResult", JSONObject().put("id", requestId).put("method", method).put("result", result))
    }

    override fun onRpcError(requestId: Int?, method: String?, code: String, message: String) {
        emit(
            "rpcError",
            JSONObject()
                .put("id", requestId)
                .put("method", method)
                .put("code", code)
                .put("message", message)
        )
    }

    override fun onPushEvent(type: String, payload: JSONObject) {
        if (type == "notification.badge") {
            notificationBridge.applyUnreadCount(payload.optNullableInt("unread_count"))
        } else if (type == "notification.dismissed") {
            notificationBridge.cancelDismissed(payload.optFirstNotificationIdArray("ids", "handled_ids", "notification_ids"))
            notificationBridge.applyUnreadCount(payload.optNullableInt("unread_count"))
        }
        emit("push", JSONObject().put("type", type).put("payload", payload))
    }

    private fun emit(type: String, payload: JSONObject) {
        if (!pageReady && type != "pairedMacs") {
            return
        }
        val event = JSONObject()
            .put("type", type)
            .put("payload", payload)
            .toString()
        val script = "window.cmuxNativeEvent && window.cmuxNativeEvent($event)"
        mainHandler.post {
            webView.evaluateJavascript(script, null)
        }
    }

    private fun connectMac(mac: PairedMac) {
        val routes = mac.supportedRoutes()
        if (routes.isEmpty()) {
            emit("error", JSONObject().put("message_key", "paired.noRoute"))
            return
        }
        cancelReconnect()
        session.connect(routes)
    }

    private fun scheduleReconnectIfNeeded(detail: String?) {
        val mac = activeMac ?: return
        if (!reconnectPolicy.shouldReconnect(detail, hasActiveMac = true, userRequestedDisconnect)) return
        cancelReconnect()
        val runnable = Runnable {
            reconnectRunnable = null
            if (activeMac?.id == mac.id && !userRequestedDisconnect) {
                connectMac(mac)
            }
        }
        reconnectRunnable = runnable
        mainHandler.postDelayed(runnable, reconnectPolicy.nextDelayMillis())
    }

    private fun cancelReconnect() {
        val runnable = reconnectRunnable ?: return
        mainHandler.removeCallbacks(runnable)
        reconnectRunnable = null
    }

    private fun subscribeToEvents(capabilities: MobileEventTopics.HostStatusCapabilities) {
        session.request(
            "mobile.events.subscribe",
            MobileRpcParams.eventSubscription(
                streamId = streamId,
                topics = MobileEventTopics.topicsForHostStatus(capabilities)
            )
        )
    }

    private fun pairedMacsJson(macs: List<PairedMac> = store.list()): JSONArray {
        val array = JSONArray()
        macs.forEach { array.put(it.toJson()) }
        return array
    }

    private fun authStateJson(): JSONObject {
        return JSONObject()
            .put("stack_access_token_configured", !stackAccessToken.isNullOrBlank())
            .put("stack_refresh_token_configured", !authStore.stackRefreshToken().isNullOrBlank())
    }

    private fun emitNotificationPermissionState(requested: Boolean, overrideEnabled: Boolean? = null) {
        val state = notificationBridge.permissionState()
        emit(
            "notificationPermission",
            JSONObject()
                .put("enabled", overrideEnabled ?: state.enabled)
                .put("can_request", state.canRequest)
                .put("requested", requested)
        )
    }

    private fun authOrigin(): String {
        return BuildConfig.CMUX_AUTH_ORIGIN.trim().trimEnd('/').ifEmpty { "https://cmux.com" }
    }

    private companion object {
        const val CLIENT_ID = MobileRpcParams.CLIENT_ID
    }
}

internal fun JSONObject.optNotificationIdArray(name: String): List<String> {
    val array = optJSONArray(name) ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        when (val value = array.opt(index)) {
            is String -> value.trim()
            is Int, is Long, is Double, is Float -> value.toString().trim()
            else -> ""
        }.takeIf { it.isNotEmpty() }
    }
}

internal fun JSONObject.optFirstNotificationIdArray(vararg names: String): List<String> {
    for (name in names) {
        val values = optNotificationIdArray(name)
        if (values.isNotEmpty()) return values
    }
    return emptyList()
}
