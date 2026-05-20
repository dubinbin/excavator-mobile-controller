package com.capstone.excavator;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.core.content.ContextCompat;
import androidx.webkit.WebViewAssetLoader;

import java.util.Locale;

public class ExcavatorPostureView extends FrameLayout {
    protected static final String WEB_ENTRY_URL =
            "https://appassets.androidplatform.net/assets/web/index.html";

    /** 与 gauge_card_bg 一致，便于与主界面侧栏卡片统一 */
    private static final float CORNER_RADIUS_DP = 10f;

    private final WebView webView;
    private final WebViewAssetLoader assetLoader;

    private float boomAngle = 0f;
    private float stickAngle = 0f;
    private float bucketAngle = 0f;
    private float cabinPitchAngle = 0f;
    private float cabinRollAngle = 0f;

    private float boomLengthScale = 1f;
    private float stickLengthScale = 1f;
    private float bucketAngleOffsetDeg = 0f;
    private boolean displayMode3D = true;
    private boolean pageReady = false;
    /**
     * 由 {@link #pause()} / {@link #resume()} 控制，用于在外部容器（如卡片切到 2D 时）暂停 3D
     * WebView 的 {@code requestAnimationFrame}+{@code evaluateJavascript}，避免后台空转抢占 FPV 的 GPU/CPU 预算。
     */
    private boolean paused = false;

    /**
     * 复用的 payload 拼装缓冲，避免每帧 {@code postCurrentPayload} 触发 5 个 JSONObject + 多次字符串拷贝的 GC。
     * 只在主线程访问。
     */
    private final StringBuilder payloadBuilder = new StringBuilder(256);

    public ExcavatorPostureView(Context context) {
        this(context, null);
    }

    public ExcavatorPostureView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ExcavatorPostureView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        float cornerRadiusPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                CORNER_RADIUS_DP,
                context.getResources().getDisplayMetrics()
        );
        Drawable cardBg = ContextCompat.getDrawable(context, R.drawable.gauge_card_bg);
        if (cardBg != null) {
            setBackground(cardBg.mutate());
        } else {
            setBackgroundColor(Color.TRANSPARENT);
        }
        setClipToOutline(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                int w = view.getWidth();
                int h = view.getHeight();
                if (w <= 0 || h <= 0) {
                    return;
                }
                outline.setRoundRect(0, 0, w, h, cornerRadiusPx);
            }
        });

        assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(context))
                .addPathHandler("/res/", new WebViewAssetLoader.ResourcesPathHandler(context))
                .build();

        webView = new WebView(context);
        initWebView();
        addView(webView, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);

        applyExtraWebSettings(settings);

        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.setOverScrollMode(OVER_SCROLL_NEVER);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(
                    WebView view, WebResourceRequest request
            ) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
                postCurrentPayload();
                postDisplayMode();
            }
        });

        webView.loadUrl(getWebEntryUrl());
    }

    protected String getWebEntryUrl() {
        return WEB_ENTRY_URL;
    }

    /**
     * 子类可覆写以调整 {@link WebSettings}（例如天地图页需允许 HTTPS 页加载 HTTP 子资源）。
     */
    protected void applyExtraWebSettings(WebSettings settings) {
        // default: no-op
    }

    /**
     * 只更新动臂相对角度；座舱 pitch/roll 保持当前值（默认均为 0）。
     */
    public void setAngles(float boom, float stick, float bucket) {
        this.boomAngle = boom;
        this.stickAngle = stick;
        this.bucketAngle = bucket;
        postCurrentPayload();
    }

    /**
     * 座舱姿态（IMU）+ 动臂相对角度，与 {@link MainActivity#updateAngles()} 入参顺序一致。
     */
    public void setAngles(float cabinPitch, float cabinRoll, float boom, float stick, float bucket) {
        this.cabinPitchAngle = cabinPitch;
        this.cabinRollAngle = cabinRoll;
        this.boomAngle = boom;
        this.stickAngle = stick;
        this.bucketAngle = bucket;
        postCurrentPayload();
    }

    public void setArmLengthsFromMm(double boomMm, double stickMm, double bucketMm) {
        if (boomMm <= 0.0 || stickMm <= 0.0 || bucketMm <= 0.0) {
            return;
        }
        setLengthScales(1f, (float) (stickMm / boomMm));
    }

    /**
     * 与 Web 端 {@code state.lengths} 一致（对应 {@code setLengths} / {@code applyExcavatorPayload}）。
     */
    public void setLengthScales(float boomScale, float stickScale) {
        boomLengthScale = clampLengthScale(boomScale);
        stickLengthScale = clampLengthScale(stickScale);
        postCurrentPayload();
    }

    private static float clampLengthScale(float v) {
        if (!Float.isFinite(v)) {
            return 1f;
        }
        return Math.max(0.1f, Math.min(10f, v));
    }

    public void setBucketAngleOffsetDeg(float offsetDeg) {
        this.bucketAngleOffsetDeg = offsetDeg;
        postCurrentPayload();
    }

    private void postCurrentPayload() {
        if (!pageReady || paused) {
            return;
        }
        if (!webView.isAttachedToWindow()) {
            return;
        }

        // 把 payload 直接拼成 JS 对象字面量，避免 JSONObject + replace + String#concat 的逐帧 GC。
        // 角度都是 float 标量，没有任何字符串字段，因此不存在引号/反斜杠转义风险。
        StringBuilder sb = payloadBuilder;
        sb.setLength(0);
        sb.append("window.applyExcavatorPayload&&window.applyExcavatorPayload({main:{pitch:");
        appendFloat(sb, cabinPitchAngle);
        sb.append(",roll:");
        appendFloat(sb, cabinRollAngle);
        sb.append("},lengths:{boom:");
        appendFloat(sb, boomLengthScale);
        sb.append(",stick:");
        appendFloat(sb, stickLengthScale);
        sb.append("},joints:{boom:{z:");
        appendFloat(sb, boomAngle);
        sb.append("},stick:{z:");
        appendFloat(sb, stickAngle);
        sb.append("},bucket:{z:");
        appendFloat(sb, bucketAngle);
        sb.append("}}});");
        final String js = sb.toString();
        webView.post(() -> webView.evaluateJavascript(js, null));
    }

    /** NaN/Infinity 兜底成 0，避免 JS 端 {@code applyExcavatorPayload} 收到 {@code NaN} 字面量解析失败。 */
    private static void appendFloat(StringBuilder sb, float v) {
        if (Float.isNaN(v) || Float.isInfinite(v)) {
            sb.append('0');
            return;
        }
        sb.append(String.format(Locale.US, "%.4f", v));
    }

    /**
     * 嵌入卡片时调用，禁用自身的卡片背景和圆角裁切，由父容器统一提供。
     */
    public void setEmbedded(boolean embedded) {
        if (embedded) {
            setBackgroundColor(android.graphics.Color.TRANSPARENT);
            setClipToOutline(false);
            setOutlineProvider(null);
        }
    }

    /**
     * 切换 2D/3D 显示模式。若 Web 页面实现了 {@code window.setDisplayMode(mode)} 则生效。
     */
    public void setDisplayMode(boolean is3D) {
        displayMode3D = is3D;
        postDisplayMode();
    }

    private void postDisplayMode() {
        if (!pageReady) return;
        String mode = displayMode3D ? "3d" : "2d";
        String js = "window.setDisplayMode && window.setDisplayMode('" + mode + "');";
        webView.post(() -> webView.evaluateJavascript(js, null));
    }

    /**
     * 子类（如天地图页）在页面未实现 {@code applyExcavatorPayload} 时也可安全注入脚本。
     */
    protected void postJavascriptToWebView(String script) {
        if (paused) return;
        webView.post(() -> {
            if (webView.isAttachedToWindow()) {
                webView.evaluateJavascript(script, null);
            }
        });
    }

    /**
     * 把 WebView 挂起：停止 {@code requestAnimationFrame}/JS 计时器，并停止 {@link #postCurrentPayload}
     * 的下发。用于父容器（如 PostureCardView 切到 2D 时）让 3D WebView 不再空转抢 GPU。
     *
     * <p>幂等。
     */
    public void pause() {
        if (paused) return;
        paused = true;
        // 通知页面侧暂停（main.js 会监听 __pauseAnim/visibilitychange）
        if (pageReady && webView.isAttachedToWindow()) {
            webView.evaluateJavascript(
                    "window.__pauseAnim && window.__pauseAnim();", null);
        }
        webView.onPause();
    }

    /** 与 {@link #pause()} 配对；resume 后会立刻补一次最新 payload。 */
    public void resume() {
        if (!paused) return;
        paused = false;
        webView.onResume();
        if (pageReady && webView.isAttachedToWindow()) {
            webView.evaluateJavascript(
                    "window.__resumeAnim && window.__resumeAnim();", null);
        }
        postCurrentPayload();
        postDisplayMode();
    }

    /**
     * Activity onPause 时调用：暂停 JS 定时器（进程级，慎用）。当前实现复用 {@link #pause()} 即可。
     */
    public void onActivityPause() {
        pause();
    }

    /** Activity onResume 时调用。 */
    public void onActivityResume() {
        resume();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        pageReady = false;
        webView.stopLoading();
        webView.destroy();
    }
}
