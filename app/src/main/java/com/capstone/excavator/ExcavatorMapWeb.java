package com.capstone.excavator;

import android.content.Context;
import android.util.AttributeSet;
import android.webkit.WebSettings;

import java.util.Locale;

public class ExcavatorMapWeb extends ExcavatorPostureView {
    private static final String WEB_ENTRY_URL_2D =
            "https://appassets.androidplatform.net/assets/web/excavator-map/index.html";

    /**
     * 与上次下发坐标的最小差值（度）。模拟 RTK 每秒原样回灌，去掉重复下发以避免 1Hz
     * {@code evaluateJavascript} → 天地图 SDK 内部 layout/瓦片可见性扫描，挤占 FPV 的 GPU/合成预算。
     * 1e-7 度 ≈ 1cm，远小于车辆有效位移。
     */
    private static final double MIN_DELTA_DEG = 1e-7;

    private double lastLat = Double.NaN;
    private double lastLon = Double.NaN;
    private double lastHeading = Double.NaN;
    
    public ExcavatorMapWeb(Context context) {
        this(context, null);
    }

    public ExcavatorMapWeb(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ExcavatorMapWeb(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        // 与 PostureCardView 内 Web 一致：去掉 gauge_card_bg 叠层，让底层 BlurView 毛玻璃露在留白处
        setEmbedded(true);
    }

    @Override
    protected String getWebEntryUrl() {
        return WEB_ENTRY_URL_2D;
    }

    @Override
    protected void applyExtraWebSettings(WebSettings settings) {
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
    }

    /**
     * 通知 assets 内天地图页更新车辆位置（{@code TiandituBridge.setVehiclePose}）。
     *
     * 同坐标重复调用会被丢弃；坐标真正变化（&gt; {@link #MIN_DELTA_DEG}）才下发，避免每秒固定下发触发
     * 天地图 {@code centerAndZoom} 内部的 layout/瓦片刷新。
     */
    public void setFixedLocation(double lat, double lon, double headingDeg) {
        if (!Double.isFinite(lat) || !Double.isFinite(lon)) {
            return;
        }
        if (!Double.isNaN(lastLat) && !Double.isNaN(lastLon)
                && Math.abs(lat - lastLat) < MIN_DELTA_DEG
                && Math.abs(lon - lastLon) < MIN_DELTA_DEG
                && !Double.isNaN(lastHeading) && !Double.isNaN(headingDeg)
                && Math.abs(headingDeg - lastHeading) < 0.01) {
            return;
        }
        lastLat = lat;
        lastLon = lon;
        lastHeading = headingDeg;
        String js = String.format(Locale.US,
                "(function(){if(window.TiandituBridge&&window.TiandituBridge.setVehiclePose){"
                        + "window.TiandituBridge.setVehiclePose(%f,%f,%f);}})();",
                lat, lon, headingDeg);
        postJavascriptToWebView(js);
    }
}
