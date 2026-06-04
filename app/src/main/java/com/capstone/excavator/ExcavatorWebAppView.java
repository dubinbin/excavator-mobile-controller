package com.capstone.excavator;

import android.content.Context;
import android.util.AttributeSet;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;

public class ExcavatorWebAppView extends ExcavatorPostureView {
    private static final String WEB_APP_ENTRY_URL =
            "https://appassets.androidplatform.net/assets/web/excavator-web-app/index.html";

    public ExcavatorWebAppView(Context context) {
        super(context);
    }

    public ExcavatorWebAppView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ExcavatorWebAppView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected String getWebEntryUrl() {
        return WEB_APP_ENTRY_URL;
    }

    @Override
    protected void applyExtraWebSettings(WebSettings settings) {
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setTextZoom(100);
    }

    @Override
    protected void configureWebView(WebView webView) {
        NativeBridge bridge = new NativeBridge();
        webView.addJavascriptInterface(bridge, "AndroidWebViewBridge");
        webView.addJavascriptInterface(bridge, "Android");
    }

    private static final class NativeBridge {
        @JavascriptInterface
        public void postMessage(String message) {
            ExcavatorWebAppBridge.dispatchMessage(message);
        }

        @JavascriptInterface
        public void sendMessage(String message) {
            ExcavatorWebAppBridge.dispatchMessage(message);
        }
    }
}
