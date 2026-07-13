package com.capstone.excavator;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
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
    private static final String NATIVE_EVENT_SAVED_CONFIG_SIGNAL = "GET_SAVED_CONFIG_SINGAL";
    private static final String NATIVE_EVENT_SAVED_CONFIG = "GET_SAVED_CONFIG";
    
    private static final String WEB_EVENT_JOYSTICK_MAPPING_SAVED = "JOYSTICK_MAPPING_SAVED";
    private static final String SAVE_CONFIG_FROM_WEBVIEW = "SAVE_CONFIG_SIGNAL";
    private static final String MODIFY_SYSYEM_BRIGHT_EVENT = "MODIFIY_BRIGHT_IMMEDIATELY";
    private static final String PREFS_GENERAL_UI = "general_ui_prefs";
    private static final String KEY_BRIGHTNESS_PERCENT = "brightness_percent";
    private static final String NATIVE_EVENT_CURRENT_STATUS = "GET_CURRENT_STATUS";

    public static final String EXTRA_INITIAL_PAGE = "initial_page";
    public static final int PAGE_GENERAL = 3;

    private ExcavatorWebAppView settingView;
    private Handler mainHandler;
    private long createStartedAtMs;
    private final GlobalStatus.OnMotionModeChangeListener motionModeChangeListener =
            motionMode -> runOnUiThread(this::sendCurrentStatusMessageToWebview);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        createStartedAtMs = SystemClock.elapsedRealtime();
        super.onCreate(savedInstanceState);
        setFullScreenMode();
        applyStoredBrightnessPercent();
        setResult(RESULT_OK);
        mainHandler = new Handler(Looper.getMainLooper());
        ExcavatorWebAppBridge.setMessageListener(this::onWebAppMessage);

        int initialPage = getIntent().getIntExtra(EXTRA_INITIAL_PAGE, 0);
        String initialRoute = ExcavatorWebAppView.getSettingsRoute(initialPage);
        ExcavatorWebAppView.setNextInitialRoute(initialRoute);
        settingView = ExcavatorWebAppPreloader.takeWarmedSettingsView(this);
        boolean reusedWarmedView = settingView != null;
        if (settingView == null) {
            settingView = new ExcavatorWebAppView(UiScaleConfig.unscaledContext(this));
        }
        ExcavatorWebAppView.clearNextInitialRoute();
        setContentView(settingView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        GlobalStatus.getInstance().addMotionModeChangeListener(motionModeChangeListener);
        if (reusedWarmedView) {
            settingView.loadRoute(initialRoute);
        }
        configureKeyboardInsets();
    }

    private void configureKeyboardInsets() {
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (settingView == null) {
            return;
        }

        ViewCompat.setOnApplyWindowInsetsListener(settingView, (view, insets) -> {
            Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());
            int bottomPadding = insets.isVisible(WindowInsetsCompat.Type.ime()) ? imeInsets.bottom : 0;
            view.setPadding(0, 0, 0, bottomPadding);
            return insets;
        });
        ViewCompat.requestApplyInsets(settingView);
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
        GlobalStatus.getInstance().removeMotionModeChangeListener(motionModeChangeListener);
        ExcavatorWebAppBridge.setMessageListener(null);
        settingView = null;
        super.onDestroy();
    }

    private void onWebAppMessage(String message) {
        String type = parseMessageType(message);
        if (WEB_EVENT_CLOSE_WEBVIEW.equals(type)) {
            runOnUiThread(this::finish);
        } else if (WEB_EVENT_READY.equals(type)) {
            Log.i(TAG, "web settings ready after "
                    + (SystemClock.elapsedRealtime() - createStartedAtMs) + " ms");
            sendJoystickMappingMessage();
            sendCurrentStatusMessageToWebview();
        } else if (WEB_EVENT_JOYSTICK_MAPPING_SAVED.equals(type)) {
            onWebAppJoystickMappingSaved(message);
        } else if (SAVE_CONFIG_FROM_WEBVIEW.equals(type)) {
            onWebAppSaveConfig(message);
        } else if (MODIFY_SYSYEM_BRIGHT_EVENT.equals(type)) {
            onWebAppModifyBrightnessImmediately(message);
        } else if (NATIVE_EVENT_SAVED_CONFIG_SIGNAL.equals(type)) {
            sendSavedConfigMessage();
        }
    }

    private void sendCurrentStatusMessageToWebview() {
        if (settingView == null) {
            return;
        }
        try {
            JSONObject message = new JSONObject();
            message.put("type", NATIVE_EVENT_CURRENT_STATUS);
            message.put("payload", GlobalStatus.getInstance().getMotionMode());
            settingView.sendMessageToWeb(message.toString());
        } catch (JSONException e) {
            Log.w(TAG, "build current status message failed", e);
        }
    }

    private void onWebAppSaveConfig(String message) {
        JSONObject payload = parsePayload(message);
        if (payload == null) {
            Log.w(TAG, "SAVE_CONFIG payload missing");
            return;
        }
        System.out.println("onWebAppSaveConfig payload " + payload);
        String language = payload.optString("language", "");
        if (!language.isEmpty()) {
            LanguageManager.setLanguage(this, normalizeLanguage(language));
        }

        if (payload.has("brightness")) {
            int brightness = clampBrightness(Math.round((float) payload.optDouble("brightness", 50)));
            saveBrightnessPercent(brightness);
            applyBrightnessPercent(brightness);
        }

        String videoStreamUrl = payload.optString("videoStreamUrl", "");
        ControllerLocalSettings.saveVideoStreamUrl(this, videoStreamUrl);

        Intent resultIntent = new Intent();
        if (!videoStreamUrl.trim().isEmpty()) {
            resultIntent.putExtra("video_url", videoStreamUrl.trim());
        }
        setResult(RESULT_OK, resultIntent);
        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
    }

    private void onWebAppModifyBrightnessImmediately(String message) {
        JSONObject payload = parsePayload(message);
        if (payload == null || !payload.has("brightness")) {
            Log.w(TAG, "MODIFIY_BRIGHT_IMMEDIATELY brightness missing");
            return;
        }

        int brightness = clampBrightness(Math.round((float) payload.optDouble("brightness", 50)));
        saveBrightnessPercent(brightness);
        applyBrightnessPercent(brightness);
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

    private static String normalizeLanguage(String language) {
        if (language == null) {
            return LanguageManager.DEFAULT_LANGUAGE;
        }
        String normalized = language.trim();
        switch (normalized) {
            case LanguageManager.LANG_ZH_HANS:
            case "zh_CN":
            case "zh-CN":
            case "zh":
            case "zhHans":
            case "zh_hans":
                return LanguageManager.LANG_ZH_HANS;
            case LanguageManager.LANG_ZH_HANT:
            case "zh_TW":
            case "zh-TW":
            case "zh_HK":
            case "zh-HK":
            case "zhHant":
            case "zh_hant":
                return LanguageManager.LANG_ZH_HANT;
            case LanguageManager.LANG_EN:
            case "en_US":
            case "en-US":
                return LanguageManager.LANG_EN;
            default:
                return LanguageManager.DEFAULT_LANGUAGE;
        }
    }

    private static int clampBrightness(int percent) {
        return Math.max(0, Math.min(100, percent));
    }

    private void saveBrightnessPercent(int percent) {
        SharedPreferences sp = getSharedPreferences(PREFS_GENERAL_UI, MODE_PRIVATE);
        sp.edit().putInt(KEY_BRIGHTNESS_PERCENT, clampBrightness(percent)).apply();
    }

    private void applyStoredBrightnessPercent() {
        SharedPreferences sp = getSharedPreferences(PREFS_GENERAL_UI, MODE_PRIVATE);
        applyBrightnessPercent(sp.getInt(KEY_BRIGHTNESS_PERCENT, 50));
    }

    private void applyBrightnessPercent(int percent) {
        Window window = getWindow();
        if (window == null) {
            return;
        }
        WindowManager.LayoutParams lp = window.getAttributes();
        float brightness = Math.max(0.05f, Math.min(1f, clampBrightness(percent) / 100f));
        lp.screenBrightness = brightness;
        window.setAttributes(lp);
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

    private void sendSavedConfigMessage() {
        if (settingView == null) {
            return;
        }
        try {
            JSONObject message = new JSONObject();
            message.put("type", NATIVE_EVENT_SAVED_CONFIG);
            message.put("payload", buildSavedConfigPayload());
            settingView.sendMessageToWeb(message.toString());
        } catch (JSONException e) {
            Log.w(TAG, "build saved config message failed", e);
        }
    }

    private JSONObject buildSavedConfigPayload() throws JSONException {
        SharedPreferences sp = getSharedPreferences(PREFS_GENERAL_UI, MODE_PRIVATE);
        ControllerLocalSettings.Snapshot local = ControllerLocalSettings.load(this);
        return new JSONObject()
                .put("language", LanguageManager.getLanguage(this))
                .put("brightness", clampBrightness(sp.getInt(KEY_BRIGHTNESS_PERCENT, 50)))
                .put("videoStreamUrl", local.videoStreamUrl != null ? local.videoStreamUrl : "");
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
