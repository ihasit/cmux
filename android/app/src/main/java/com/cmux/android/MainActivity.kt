package com.cmux.android

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var bridge: MobileWebBridge

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        bridge = MobileWebBridge(this, webView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        webView.webChromeClient = WebChromeClient()
        webView.addJavascriptInterface(bridge, "cmuxAndroid")

        setContentView(webView)
        webView.loadUrl("file:///android_asset/mobile/index.html")
        bridge.handlePairingURL(intent?.dataString)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        bridge.handlePairingURL(intent.dataString)
    }

    override fun onDestroy() {
        bridge.close()
        webView.destroy()
        super.onDestroy()
    }
}
