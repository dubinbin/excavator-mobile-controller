package com.capstone.excavator;

import java.util.Locale;

import android.content.Context;
import android.util.AttributeSet;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;

import org.json.JSONObject;

public class ExcavatorWebAppSettingView extends ExcavatorPostureView {
    private static final String WEB_APP_BASE_URL = "https://appassets.androidplatform.net/assets/web/excavator-web-app/index.html";
    private static final String[] SETTINGS_ROUTES = {
            "#/settings/imu_setting",
            "#/settings/size_setting",
            "#/settings/joystick_setting",
            "#/settings/common_setting"
    };
    private static int nextInitialPage = 0;

    public ExcavatorWebAppSettingView(Context context) {
        super(context);
    }

    public ExcavatorWebAppSettingView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ExcavatorWebAppSettingView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    static void setNextInitialPage(int page) {
        if (page < 0 || page >= SETTINGS_ROUTES.length) {
            page = 0;
        }
        nextInitialPage = page;
    }

    @Override
    protected String getWebEntryUrl() {
        return WEB_APP_BASE_URL + SETTINGS_ROUTES[nextInitialPage];
    }

    @Override
    protected boolean shouldShowWebLoadingOverlay() {
        return true;
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

    public void sendMessageToWeb(String messageJson) {
        String quoted = JSONObject.quote(messageJson);
        String js = "(function(){"
                + "var raw=" + quoted + ";"
                + "var msg;try{msg=JSON.parse(raw);}catch(e){msg=raw;}"
                + "if(window.receiveMessage){window.receiveMessage(msg);}"
                + "if(window.onNativeMessage){window.onNativeMessage(msg);}"
                + "window.dispatchEvent(new MessageEvent('message',{data:msg}));"
                + "window.dispatchEvent(new CustomEvent('nativeMessage',{detail:msg}));"
                + "})();";
        postJavascriptToWebView(js);
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
