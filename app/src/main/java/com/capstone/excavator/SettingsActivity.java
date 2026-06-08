package com.capstone.excavator;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

public class SettingsActivity extends ScaledAppCompatActivity {
    private static final String TAG = "SettingsActivity";
    private static final String WEB_EVENT_READY = "WEBVIEW_READY";
    private static final String WEB_EVENT_CLOSE_WEBVIEW = "CLOSE_WEBVIEW";
    private static final String NATIVE_EVENT_JOYSTICK_MAPPING = "JOYSTICK_MAPPING";
    private static final String WEB_EVENT_JOYSTICK_MAPPING_SAVED = "JOYSTICK_MAPPING_SAVED";

    public static final String EXTRA_INITIAL_PAGE = "initial_page";
    public static final int PAGE_GENERAL = 3;

    private ExcavatorWebAppSettingView settingView;
    private Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setFullScreenMode();
        setResult(RESULT_OK);
        mainHandler = new Handler(Looper.getMainLooper());
        ExcavatorWebAppBridge.setMessageListener(this::onWebAppMessage);

        int initialPage = getIntent().getIntExtra(EXTRA_INITIAL_PAGE, 0);
        ExcavatorWebAppSettingView.setNextInitialPage(initialPage);
        settingView = new ExcavatorWebAppSettingView(UiScaleConfig.unscaledContext(this));
        setContentView(settingView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (settingView != null) {
            settingView.resume();
        }
    }

    @Override
    protected void onPause() {
        if (settingView != null) {
            settingView.pause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        ExcavatorWebAppBridge.setMessageListener(null);
        settingView = null;
        super.onDestroy();
    }

    private void onWebAppMessage(String message) {
        String type = parseMessageType(message);
        if (WEB_EVENT_CLOSE_WEBVIEW.equals(type)) {
            runOnUiThread(this::finish);
        } else if (WEB_EVENT_READY.equals(type)) {
            sendJoystickMappingMessage();
        } else if (WEB_EVENT_JOYSTICK_MAPPING_SAVED.equals(type)) {
            onWebAppJoystickMappingSaved(message);
        }
    }

    private void onWebAppJoystickMappingSaved(String message) {
        try {
            JSONObject json = new JSONObject(message);
            JSONObject payload = json.optJSONObject("payload");
            if (payload == null) return;

            ControllerLocalSettings.Snapshot local = new ControllerLocalSettings.Snapshot();
            local.joystickLeftAb = payload.optString("leftAb", "");
            local.joystickLeftAbReverse = payload.optBoolean("leftAbReverse", false);
            local.joystickLeftCd = payload.optString("leftCd", "");
            local.joystickLeftCdReverse = payload.optBoolean("leftCdReverse", false);
            local.joystickRightEf = payload.optString("rightEf", "");
            local.joystickRightEfReverse = payload.optBoolean("rightEfReverse", false);
            local.joystickRightGh = payload.optString("rightGh", "");
            local.joystickRightGhReverse = payload.optBoolean("rightGhReverse", false);

            // 保存到本地
            ControllerLocalSettings.save(this, local);
            
            onJoystickMappingSaved(local);
        } catch (JSONException e) {
            Log.w(TAG, "parse joystick mapping saved message failed", e);
        }
    }

    private static String parseMessageType(String message) {
        if (message == null) {
            return "";
        }
        String trimmed = message.trim();
        if (WEB_EVENT_READY.equals(trimmed)) {
            return WEB_EVENT_READY;
        }
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

    private void sendJoystickMappingMessage() {
        if (settingView == null) {
            return;
        }
        try {
            JSONObject message = new JSONObject();
            message.put("type", NATIVE_EVENT_JOYSTICK_MAPPING);
            message.put("payload", buildJoystickMappingPayload());
            settingView.sendMessageToWeb(message.toString());
        } catch (JSONException e) {
            Log.w(TAG, "build joystick mapping message failed", e);
        }
    }

    private JSONObject buildJoystickMappingPayload() throws JSONException {
        ControllerLocalSettings.Snapshot s = ControllerLocalSettings.load(this);
        if (s.joystickLeftAb.isEmpty()
                && s.joystickLeftCd.isEmpty()
                && s.joystickRightEf.isEmpty()
                && s.joystickRightGh.isEmpty()) {
            s = ControllerLocalSettings.createDefaultJoystickMappingSnapshot();
        }

        JSONObject payload = new JSONObject();
        payload.put("axes", new JSONObject()
                .put("leftAb", buildJoystickAxis(s.joystickLeftAb, s.joystickLeftAbReverse))
                .put("leftCd", buildJoystickAxis(s.joystickLeftCd, s.joystickLeftCdReverse))
                .put("rightEf", buildJoystickAxis(s.joystickRightEf, s.joystickRightEfReverse))
                .put("rightGh", buildJoystickAxis(s.joystickRightGh, s.joystickRightGhReverse)));

        JSONArray options = new JSONArray();
        for (String label : ControllerLocalSettings.JOYSTICK_MOTION_LABELS) {
            options.put(new JSONObject()
                    .put("label", label)
                    .put("key", ControllerLocalSettings.motionLabelToKey(label)));
        }
        payload.put("options", options);
        return payload;
    }

    private static JSONObject buildJoystickAxis(String label, boolean reverse) throws JSONException {
        return new JSONObject()
                .put("label", label)
                .put("key", ControllerLocalSettings.motionLabelToKey(label))
                .put("reverse", reverse)
                .put("display", ControllerLocalSettings.formatJoystickDisplay(label, reverse));
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

    /**
     * 摇杆四轴映射已成功写入 {@link ControllerLocalSettings} 后调用：
     * 1. 打印铲斗模式映射便于调试；
     * 2. 通过 {@link JoystickChannelMappingApplier} 把新映射下发到遥控器的 {@code ChannelSettings}。
     */
    private void onJoystickMappingSaved(ControllerLocalSettings.Snapshot local) {
        if (local == null) return;
        String line = String.format(Locale.US,
                "joystick saved: AB=%s%s(%s) CD=%s%s(%s) EF=%s%s(%s) GH=%s%s(%s)",
                local.joystickLeftAb,
                local.joystickLeftAbReverse ? "(R)" : "(F)",
                ControllerLocalSettings.motionLabelToKey(local.joystickLeftAb),
                local.joystickLeftCd,
                local.joystickLeftCdReverse ? "(R)" : "(F)",
                ControllerLocalSettings.motionLabelToKey(local.joystickLeftCd),
                local.joystickRightEf,
                local.joystickRightEfReverse ? "(R)" : "(F)",
                ControllerLocalSettings.motionLabelToKey(local.joystickRightEf),
                local.joystickRightGh,
                local.joystickRightGhReverse ? "(R)" : "(F)",
                ControllerLocalSettings.motionLabelToKey(local.joystickRightGh));

        Log.i(TAG, line);

        // 异步下发到遥控器；使用 applicationContext 让 Toast 在 finish() 之后仍可展示。
        final android.content.Context appCtx = getApplicationContext();
        JoystickChannelMappingApplier.applyUserMapping(appCtx, local, e -> {
            mainHandler.post(() -> {
                if (e == null) {
                    Toast.makeText(appCtx,
                            "铲斗模式摇杆通道映射已下发到遥控器", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(appCtx,
                            "摇杆通道下发失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        });
    }
}
