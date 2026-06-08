package com.capstone.excavator;

import android.os.Bundle;
import android.view.ViewGroup;

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
        ExcavatorWebAppView webAppView = ExcavatorWebAppPreloader.takeWarmedView(this);
        if (webAppView == null) {
            webAppView = new ExcavatorWebAppView(UiScaleConfig.unscaledContext(this));
        } else {
            webAppView.loadRoute(initialRoute);
        }
        ExcavatorWebAppView.clearNextInitialRoute();
        setContentView(webAppView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
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
