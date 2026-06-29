package com.capstone.excavator;

import android.os.Bundle;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import org.json.JSONException;
import org.json.JSONObject;

public class ExcavatorWebAppActivity extends ScaledAppCompatActivity {
    public static final String EXTRA_INITIAL_ROUTE = "initial_route";
    private static final String WEB_EVENT_CLOSE_WEBVIEW = "CLOSE_WEBVIEW";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setFullScreenMode();
        ExcavatorWebAppBridge.setMessageListener(this::onWebAppMessage);

        String initialRoute = getIntent().getStringExtra(EXTRA_INITIAL_ROUTE);
        ExcavatorWebAppView.setNextInitialRoute(initialRoute);
        ExcavatorWebAppView webAppView = ExcavatorWebAppPreloader.takeWarmedTaskView(this);
        boolean reusedWarmedView = webAppView != null;
        if (webAppView == null) {
            webAppView = new ExcavatorWebAppView(UiScaleConfig.unscaledContext(this));
        }
        ExcavatorWebAppView.clearNextInitialRoute();
        setContentView(webAppView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        if (reusedWarmedView) {
            // 只切换 SPA hash，保留已解析的 JS、React runtime 和图片解码结果。
            webAppView.loadRoute(initialRoute);
        }
        configureKeyboardInsets(webAppView);
    }

    private void configureKeyboardInsets(ExcavatorWebAppView webAppView) {
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (webAppView == null) {
            return;
        }

        ViewCompat.setOnApplyWindowInsetsListener(webAppView, (view, insets) -> {
            Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());
            int bottomPadding = insets.isVisible(WindowInsetsCompat.Type.ime()) ? imeInsets.bottom : 0;
            view.setPadding(0, 0, 0, bottomPadding);
            return insets;
        });
        ViewCompat.requestApplyInsets(webAppView);
    }

    @Override
    protected void onDestroy() {
        ExcavatorWebAppBridge.setMessageListener(null);
        super.onDestroy();
    }

    private void onWebAppMessage(String message) {
        if (WEB_EVENT_CLOSE_WEBVIEW.equals(parseMessageType(message))) {
            runOnUiThread(this::finish);
        }
    }

    private static String parseMessageType(String message) {
        if (message == null) {
            return "";
        }
        String trimmed = message.trim();
        if (WEB_EVENT_CLOSE_WEBVIEW.equals(trimmed)) {
            return WEB_EVENT_CLOSE_WEBVIEW;
        }
        try {
            JSONObject json = new JSONObject(trimmed);
            String type = json.optString("type", "");
            if (type.isEmpty()) {
                type = json.optString("event", "");
            }
            return type;
        } catch (JSONException e) {
            return "";
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            setFullScreenMode();
        }
    }

    private void setFullScreenMode() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            controller.hide(WindowInsetsCompat.Type.systemBars());
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }
}
