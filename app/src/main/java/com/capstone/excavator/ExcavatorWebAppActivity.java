package com.capstone.excavator;

import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;

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

    private ExcavatorWebAppView webAppView;
    private WebTaskTcuController taskController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setFullScreenMode();
        ExcavatorWebAppBridge.setMessageListener(this::onWebAppMessage);

        String initialRoute = getIntent().getStringExtra(EXTRA_INITIAL_ROUTE);
        ExcavatorWebAppView.setNextInitialRoute(initialRoute);
        webAppView = ExcavatorWebAppPreloader.takeWarmedTaskView(this);
        boolean reusedWarmedView = webAppView != null;
        if (webAppView == null) {
            webAppView = new ExcavatorWebAppView(UiScaleConfig.unscaledContext(this));
        }
        taskController = new WebTaskTcuController(new WebTaskTcuController.Host() {
            @Override
            public void sendToWeb(String messageJson) {
                if (webAppView != null) {
                    webAppView.sendMessageToWeb(messageJson);
                }
            }

            @Override
            public void showError(String message) {
                runOnUiThread(() ->
                        Toast.makeText(ExcavatorWebAppActivity.this, message, Toast.LENGTH_LONG).show());
            }

            @Override
            public void onTaskActivated(TaskTypeState.Type taskType) {
                runOnUiThread(() -> {
                    Toast.makeText(
                            ExcavatorWebAppActivity.this,
                            taskName(taskType) + "任务已激活",
                            Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(ExcavatorWebAppActivity.this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    finish();
                });
            }
        }, initialRoute);
        ExcavatorWebAppView.clearNextInitialRoute();
        setContentView(webAppView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        if (reusedWarmedView) {
            // 只切换 SPA hash，保留已解析的 JS、React runtime 和图片解码结果。
            webAppView.loadRoute(initialRoute);
            // 预热阶段的 WEBVIEW_READY 会被 NativeBridge 主动抑制，复用时需显式启动 TCU 流程。
            taskController.onWebReady();
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
        if (taskController != null) {
            taskController.destroy();
            taskController = null;
        }
        webAppView = null;
        super.onDestroy();
    }

    private void onWebAppMessage(String message) {
        String type = parseMessageType(message);
        if (WEB_EVENT_CLOSE_WEBVIEW.equals(type)) {
            runOnUiThread(this::finish);
            return;
        }
        if (WebTaskTcuController.WEB_EVENT_READY.equals(type)) {
            if (taskController != null) {
                taskController.onWebReady();
            }
            return;
        }
        if (taskController != null) {
            taskController.onWebMessage(type, parsePayload(message));
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

    private static JSONObject parsePayload(String message) {
        if (message == null) {
            return null;
        }
        try {
            JSONObject json = new JSONObject(message.trim());
            JSONObject payload = json.optJSONObject("payload");
            return payload != null ? payload : json;
        } catch (JSONException e) {
            return null;
        }
    }

    private static String taskName(TaskTypeState.Type taskType) {
        switch (taskType) {
            case LEVEL:
                return "找平";
            case DITCH:
                return "挖沟";
            case SLOPE:
                return "修坡";
            default:
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
