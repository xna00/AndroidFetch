package com.example.androidfetch

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge


class MainActivity : ComponentActivity() {
    var webView: WebView? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WebView.setWebContentsDebuggingEnabled(true)
        setContentView(R.layout.activity_main)
        webView = findViewById(R.id.webView)
        val webView = webView!!

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true
        webView.settings.databaseEnabled = true

        webView.webViewClient = WebViewClient()

        webView.loadUrl(getString(R.string.webview_url))
        webView.addJavascriptInterface(JavascriptBridge(webView, this), "bridge")
    }
}
