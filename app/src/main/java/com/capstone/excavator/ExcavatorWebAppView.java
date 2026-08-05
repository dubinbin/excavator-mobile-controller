package com.capstone.excavator;

import android.content.Context;
import android.util.AttributeSet;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;

public class ExcavatorWebAppView extends ExcavatorPostureView {
    public static final String ROUTE_LEVELING_TASK_STEP1 = "#/leveling-task/step1";
    public static final String ROUTE_DIG_TASK_STEP1 = "#/dig-task/step1";
    public static final String ROUTE_REPAIR_SLOPE_STEP1 = "#/repair-slope/step1";
    private static final String[] SETTINGS_ROUTES = {
            "#/settings/imu_setting",
            "#/settings/size_setting",
            "#/settings/joystick_setting",
            "#/settings/common_setting"
    };

    private static final String WEB_APP_BASE_URL = BuildConfig.WEB_APP_BASE_URL;
    private static final String DEFAULT_ROUTE = "";
    private static String nextInitialRoute = DEFAULT_ROUTE;

    private String initialRoute = DEFAULT_ROUTE;
    private String pendingRoute;
    private boolean routeWarmupRequested;
    private boolean routeWarmupStarted;
    private boolean routeWarmupComplete;
    private int routeWarmupGeneration;
    private int routeNavigationGeneration;
    private Runnable routeWarmupCompleteListener;
    private String warmupRoutesJson = "[]";

    public ExcavatorWebAppView(Context context) {
        super(context);
        initialRoute = nextInitialRoute;
    }

    ExcavatorWebAppView(Context context, boolean bypassInitialCache) {
        super(context, null, 0, bypassInitialCache);
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
        // index.html 自带与页面同色的 Loading 动画。原生白色 overlay 会把它完全遮住，
        // 在极早点击、预热尚未完成时表现成数秒纯白屏。
        return false;
    }

    static void setNextInitialRoute(String route) {
        nextInitialRoute = normalizeRoute(route);
    }

    static void clearNextInitialRoute() {
        nextInitialRoute = DEFAULT_ROUTE;
    }

    static String getSettingsRoute(int page) {
        int normalizedPage = page < 0 || page >= SETTINGS_ROUTES.length ? 0 : page;
        return SETTINGS_ROUTES[normalizedPage];
    }

    public void loadRoute(String route) {
        initialRoute = normalizeRoute(route);
        routeWarmupRequested = false;
        routeWarmupCompleteListener = null;
        cancelRouteWarmup();
        if (!isWebPageReady()) {
            pendingRoute = initialRoute;
            return;
        }
        navigateRouteWithoutReload(initialRoute);
    }

    void prewarmRoutes(String[] routes, Runnable onComplete) {
        routeWarmupRequested = true;
        routeWarmupStarted = false;
        routeWarmupComplete = false;
        routeWarmupCompleteListener = onComplete;
        warmupRoutesJson = new JSONArray(Arrays.asList(routes)).toString();
        if (isWebPageReady()) {
            startRouteWarmup();
        }
    }

    boolean isRouteWarmupComplete() {
        return routeWarmupComplete;
    }

    /**
     * 只有文档和整轮路由预热都结束后，WebView 才能安全地从 MainActivity 移到新 Activity。
     * 在加载或路由切换中途 reparent WebView，部分设备的 Chromium renderer 会偶发停在一个
     * 看似已渲染、但不再处理输入的状态。
     */
    boolean isReadyForReuse() {
        return isWebPageReady() && routeWarmupComplete;
    }

    void cancelRouteWarmup() {
        int generation = ++routeWarmupGeneration;
        if (routeWarmupStarted && !routeWarmupComplete) {
            evaluateWebJavascript(
                    "window.__excavatorWarmupGeneration = " + generation + ";"
            );
        }
    }

    @Override
    protected void onWebPageFinished(WebView webView, String url) {
        if (pendingRoute != null) {
            String route = pendingRoute;
            pendingRoute = null;
            navigateRouteWithoutReload(route);
        } else if (routeWarmupRequested) {
            startRouteWarmup();
        }
    }

    private void navigateRouteWithoutReload(String route) {
        String quotedRoute = JSONObject.quote(normalizeRoute(route));
        int warmupGeneration = routeWarmupGeneration;
        int navigationGeneration = ++routeNavigationGeneration;
        evaluateWebJavascript(
                "window.__excavatorWarmupGeneration = " + warmupGeneration + ";"
                        + "window.__excavatorNavigationGeneration = "
                        + navigationGeneration + ";"
                        + "if (window.location.hash === " + quotedRoute + ") {"
                        + "window.location.hash = '#/';"
                        + "requestAnimationFrame(() => requestAnimationFrame(() => {"
                        + "if (window.__excavatorNavigationGeneration === "
                        + navigationGeneration + ") {"
                        + "window.location.hash = " + quotedRoute + ";"
                        + "}"
                        + "}));"
                        + "} else {"
                        + "window.location.hash = " + quotedRoute + ";"
                        + "}"
        );
    }

    private void startRouteWarmup() {
        if (routeWarmupStarted || !routeWarmupRequested || !isWebPageReady()) {
            return;
        }
        routeWarmupStarted = true;
        int generation = ++routeWarmupGeneration;
        evaluateWebJavascript(
                "(() => {"
                        + "const generation = " + generation + ";"
                        + "window.__excavatorWarmupGeneration = generation;"
                        + "const isCancelled = () => "
                        + "window.__excavatorWarmupGeneration !== generation;"
                        + "const routes = " + warmupRoutesJson + ";"
                        + "const frames = () => new Promise(resolve => "
                        + "requestAnimationFrame(() => requestAnimationFrame(resolve)));"
                        + "const settle = async () => {"
                        + "await new Promise(resolve => setTimeout(resolve, 80));"
                        + "await frames();"
                        + "const images = Array.from(document.images);"
                        + "const decoded = Promise.all(images.map(image => {"
                        + "if (image.decode) return image.decode().catch(() => {});"
                        + "if (image.complete) return Promise.resolve();"
                        + "return new Promise(resolve => {"
                        + "image.addEventListener('load', resolve, {once:true});"
                        + "image.addEventListener('error', resolve, {once:true});"
                        + "});"
                        + "}));"
                        + "await Promise.race([decoded, "
                        + "new Promise(resolve => setTimeout(resolve, 1200))]);"
                        + "await frames();"
                        + "};"
                        + "(async () => {"
                        + "for (const route of routes) {"
                        + "if (isCancelled()) return;"
                        + "window.location.hash = route;"
                        + "await settle();"
                        + "}"
                        + "if (isCancelled()) return;"
                        + "window.location.hash = '#/';"
                        + "await frames();"
                        + "if (!isCancelled()) {"
                        + "AndroidWebViewBridge.onPreloadReady(generation);"
                        + "}"
                        + "})().catch(() => {});"
                        + "})();"
        );
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

    private final class NativeBridge {
        @JavascriptInterface
        public void postMessage(String message) {
            if (!routeWarmupRequested) {
                ExcavatorWebAppBridge.dispatchMessage(message);
            }
        }

        @JavascriptInterface
        public void sendMessage(String message) {
            if (!routeWarmupRequested) {
                ExcavatorWebAppBridge.dispatchMessage(message);
            }
        }

        @JavascriptInterface
        public void onPreloadReady(int generation) {
            post(() -> {
                if (!routeWarmupRequested
                        || routeWarmupComplete
                        || generation != routeWarmupGeneration) {
                    return;
                }
                routeWarmupComplete = true;
                Runnable listener = routeWarmupCompleteListener;
                routeWarmupCompleteListener = null;
                if (listener != null) {
                    listener.run();
                }
            });
        }
    }
}
