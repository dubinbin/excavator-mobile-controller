package com.capstone.excavator;

import android.content.Context;
import android.util.AttributeSet;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;

public class ExcavatorWebAppView extends ExcavatorPostureView {
    public static final String ROUTE_LEVELING_TASK_STEP1 = "#/leveling-task/step1";
    public static final String ROUTE_DIG_TASK_STEP1 = "#/dig-task/step1";
    public static final String ROUTE_REPAIR_SLOPE_STEP1 = "#/repair-slope/step1";

    private static final String WEB_APP_BASE_URL = "http://192.168.20.146:5173/";
    private static final String DEFAULT_ROUTE = "";
    private static String nextInitialRoute = DEFAULT_ROUTE;

    private String initialRoute = DEFAULT_ROUTE;

    public ExcavatorWebAppView(Context context) {
        super(context);
        initialRoute = nextInitialRoute;
    }

    public ExcavatorWebAppView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialRoute = nextInitialRoute;
    }

    public ExcavatorWebAppView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialRoute = nextInitialRoute;
    }

    @Override
    protected String getWebEntryUrl() {
        String route = initialRoute == null ? nextInitialRoute : initialRoute;
        return WEB_APP_BASE_URL + route;
    }

    @Override
    protected boolean shouldShowWebLoadingOverlay() {
        return true;
    }

    static void setNextInitialRoute(String route) {
        nextInitialRoute = normalizeRoute(route);
    }

    static void clearNextInitialRoute() {
        nextInitialRoute = DEFAULT_ROUTE;
    }

    public void loadRoute(String route) {
        initialRoute = normalizeRoute(route);
        loadWebEntryUrl();
    }

    private static String normalizeRoute(String route) {
        if (route == null) {
            return DEFAULT_ROUTE;
        }

        String normalized = route.trim();
        if (normalized.isEmpty()) {
            return DEFAULT_ROUTE;
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isEmpty()) {
            return DEFAULT_ROUTE;
        }
        if (normalized.startsWith("#")) {
            return normalized;
        }
        return "#/" + normalized;
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
