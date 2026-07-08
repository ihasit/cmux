package com.cmux.android

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.ComponentActivity
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var bridge: MobileWebBridge
    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        bridge.handleScannedPairingCode(result.contents)
    }

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

    fun startPairingScan() {
        val options = ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt("")
            .setBeepEnabled(false)
            .setOrientationLocked(false)
        scanLauncher.launch(options)
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
