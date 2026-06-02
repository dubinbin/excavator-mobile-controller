package com.capstone.excavator;

import android.content.Context;
import android.util.AttributeSet;
import android.webkit.WebSettings;

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
}
