package com.cmux.android

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class MobileWebBridge(context: Context, private val webView: WebView) : MobileRpcSession.Callback {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val store = PairedMacStore(context)
    private val parser = PairingParser()
    private val session = MobileRpcSession(this)
    private var activeMac: PairedMac? = null
    private var pageReady = false
    private var pendingPairingURL: String? = null
    private var streamId = UUID.randomUUID().toString()

    @JavascriptInterface
    fun initialState() {
        pageReady = true
        emit("pairedMacs", JSONObject().put("macs", pairedMacsJson()))
        pendingPairingURL?.let { rawValue ->
            pendingPairingURL = null
            pair(rawValue)
        }
    }

    fun handlePairingURL(rawValue: String?) {
        val value = rawValue?.trim().orEmpty()
        if (value.isEmpty()) return
        if (pageReady) {
            pair(value)
        } else {
            pendingPairingURL = value
        }
    }

    @JavascriptInterface
    fun pair(rawValue: String) {
        runCatching {
            val mac = parser.parse(rawValue)
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
    fun connect(macId: String) {
        val mac = store.list().firstOrNull { it.id == macId }
        if (mac == null) {
            emit("error", JSONObject().put("message_key", "paired.notFound"))
            return
        }
        activeMac = mac
        connectMac(mac)
    }

    @JavascriptInterface
    fun forget(macId: String) {
        if (activeMac?.id == macId) {
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
    fun createTerminal(workspaceId: String) {
        session.request("mobile.terminal.create", JSONObject().put("workspace_id", workspaceId))
    }

    @JavascriptInterface
    fun replayTerminal(workspaceId: String, terminalId: String, columns: Int, rows: Int) {
        session.request(
            "mobile.terminal.replay",
            terminalParams(workspaceId, terminalId, columns, rows)
        )
    }

    @JavascriptInterface
    fun reportViewport(workspaceId: String, terminalId: String, columns: Int, rows: Int) {
        session.request(
            "mobile.terminal.viewport",
            terminalParams(workspaceId, terminalId, columns, rows)
        )
    }

    @JavascriptInterface
    fun clearViewport(workspaceId: String, terminalId: String) {
        session.request(
            "mobile.terminal.viewport",
            JSONObject()
                .put("workspace_id", workspaceId)
                .put("surface_id", terminalId)
                .put("terminal_id", terminalId)
                .put("client_id", CLIENT_ID)
                .put("clear", true)
        )
    }

    @JavascriptInterface
    fun sendInput(workspaceId: String, terminalId: String, text: String, columns: Int, rows: Int) {
        session.request(
            "mobile.terminal.input",
            terminalParams(workspaceId, terminalId, columns, rows).put("text", text)
        )
    }

    @JavascriptInterface
    fun pasteText(workspaceId: String, terminalId: String, text: String, submitKey: String, columns: Int, rows: Int) {
        session.request(
            "mobile.terminal.paste",
            terminalParams(workspaceId, terminalId, columns, rows)
                .put("text", text)
                .put("submit_key", submitKey.ifBlank { "return" })
        )
    }

    @JavascriptInterface
    fun closeConnection() {
        session.close("closed by webview")
    }

    fun close(reason: String = "activity destroyed") {
        session.close(reason)
        session.shutdown()
    }

    override fun onConnectionState(state: String, detail: String?) {
        emit("connection", JSONObject().put("state", state).put("detail", detail))
        if (state == "open") {
            subscribeToEvents()
            session.request("mobile.host.status")
            session.request("mobile.workspace.list")
        }
    }

    override fun onRpcResult(requestId: Int, method: String, result: JSONObject) {
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
        val route = mac.primaryRoute
        if (route == null) {
            emit("error", JSONObject().put("message_key", "paired.noRoute"))
            return
        }
        session.connect(route)
    }

    private fun terminalParams(workspaceId: String, terminalId: String, columns: Int, rows: Int): JSONObject {
        return JSONObject()
            .put("workspace_id", workspaceId)
            .put("terminal_id", terminalId)
            .put("surface_id", terminalId)
            .put("client_id", CLIENT_ID)
            .put("viewport_columns", columns.coerceIn(20, 300))
            .put("viewport_rows", rows.coerceIn(5, 120))
    }

    private fun subscribeToEvents() {
        streamId = UUID.randomUUID().toString()
        val topics = JSONArray()
            .put("workspace.updated")
            .put("terminal.render_grid")
        session.request(
            "mobile.events.subscribe",
            JSONObject()
                .put("stream_id", streamId)
                .put("topics", topics)
        )
    }

    private fun pairedMacsJson(macs: List<PairedMac> = store.list()): JSONArray {
        val array = JSONArray()
        macs.forEach { array.put(it.toJson()) }
        return array
    }

    private companion object {
        const val CLIENT_ID = "cmux-android-webview"
    }
}
