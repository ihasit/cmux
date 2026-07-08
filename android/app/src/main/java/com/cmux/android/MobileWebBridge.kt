package com.cmux.android

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONObject

class MobileWebBridge(private val webView: WebView) : MobileTcpClient.Callback {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val client = MobileTcpClient(this)

    @JavascriptInterface
    fun connect(host: String, port: Int) {
        client.connect(host.trim(), port)
    }

    @JavascriptInterface
    fun send(payload: String) {
        client.sendFrame(payload)
    }

    @JavascriptInterface
    fun close() {
        client.close("closed by webview")
    }

    fun close(reason: String = "activity destroyed") {
        client.close(reason)
        client.shutdown()
    }

    override fun onOpen() {
        emit("open", JSONObject())
    }

    override fun onFrame(payload: String) {
        emit("frame", JSONObject().put("payload", payload))
    }

    override fun onClose(reason: String) {
        emit("close", JSONObject().put("reason", reason))
    }

    override fun onError(message: String) {
        emit("error", JSONObject().put("message", message))
    }

    private fun emit(type: String, payload: JSONObject) {
        val event = JSONObject()
            .put("type", type)
            .put("payload", payload)
            .toString()
        val script = "window.cmuxNativeEvent && window.cmuxNativeEvent($event)"
        mainHandler.post {
            webView.evaluateJavascript(script, null)
        }
    }
}
