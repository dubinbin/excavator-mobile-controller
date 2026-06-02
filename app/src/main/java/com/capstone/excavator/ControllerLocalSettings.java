package com.capstone.excavator;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * 控制器侧本地持久化（SharedPreferences）。集中管理键名，便于后续扩展更多字段。
 * <p>
 * 当前包含：直播流地址、铲斗模式摇杆通道→动作映射（AB/CD/EF/GH）。新增配置时请在本类增加常量与
 * {@link Snapshot} 字段，并更新 {@link #load} / {@link #save}。
 */
public final class ControllerLocalSettings {

    private static final String PREFS_NAME = "controller_local_settings";

    private static final String KEY_SCHEMA_VERSION = "schema_version";
    private static final int SCHEMA_VERSION = 1;

    private static final String KEY_VIDEO_STREAM_URL = "video_stream_url";

    private static final String KEY_JOY_LEFT_AB = "joystick_map_left_ab";
    private static final String KEY_JOY_LEFT_CD = "joystick_map_left_cd";
    private static final String KEY_JOY_RIGHT_EF = "joystick_map_right_ef";
    private static final String KEY_JOY_RIGHT_GH = "joystick_map_right_gh";

    private static final String KEY_JOY_LEFT_AB_REVERSE = "joystick_map_left_ab_reverse";
    private static final String KEY_JOY_LEFT_CD_REVERSE = "joystick_map_left_cd_reverse";
    private static final String KEY_JOY_RIGHT_EF_REVERSE = "joystick_map_right_ef_reverse";
    private static final String KEY_JOY_RIGHT_GH_REVERSE = "joystick_map_right_gh_reverse";

    public static final String JOYSTICK_KEY_BOOM = "boom";
    public static final String JOYSTICK_KEY_STICK = "stick";
    public static final String JOYSTICK_KEY_BUCKET = "bucket";
    public static final String JOYSTICK_KEY_SWING = "swing";

    /** 与设置页下拉一致，用于校验持久化数据。 */
    public static final String[] JOYSTICK_MOTION_LABELS =
            new String[] { "大臂", "小臂", "铲斗", "回旋" };

    /** 下拉中方向标签（用「中文括号」与 motion 拼接，区别于 ASCII 括号方便兼容旧值）。 */
    public static final String JOYSTICK_DIRECTION_FORWARD = "正向";
    public static final String JOYSTICK_DIRECTION_REVERSE = "反向";

    public static final class Snapshot {
        public String videoStreamUrl = "";
        public String joystickLeftAb = "";
        public String joystickLeftCd = "";
        public String joystickRightEf = "";
        public String joystickRightGh = "";
        public boolean joystickLeftAbReverse = false;
        public boolean joystickLeftCdReverse = false;
        public boolean joystickRightEfReverse = false;
        public boolean joystickRightGhReverse = false;
    }

    /**
     * 把 motion 标签与方向拼成下拉里展示的字符串，如「大臂（正向）」。空 motion 直接返回空串。
     */
    public static String formatJoystickDisplay(String motionLabel, boolean reverse) {
        if (motionLabel == null || motionLabel.isEmpty()) return "";
        return motionLabel + "（"
                + (reverse ? JOYSTICK_DIRECTION_REVERSE : JOYSTICK_DIRECTION_FORWARD)
                + "）";
    }

    /** 从「大臂（正向）」之类显示串中提取 motion 标签；不带方向时原样返回。 */
    public static String parseJoystickMotionLabel(String display) {
        if (display == null) return "";
        String s = display.trim();
        int p = s.indexOf('（');
        if (p < 0) return s;
        return s.substring(0, p).trim();
    }

    /** 从「大臂（反向）」之类显示串中解析是否反向；未带方向后缀按默认 false 处理。 */
    public static boolean parseJoystickReverse(String display) {
        if (display == null) return false;
        return display.contains(JOYSTICK_DIRECTION_REVERSE);
    }

    public static String motionLabelToKey(String displayLabel) {
        if (displayLabel == null) return "";
        switch (displayLabel) {
            case "大臂":
                return JOYSTICK_KEY_BOOM;
            case "小臂":
                return JOYSTICK_KEY_STICK;
            case "铲斗":
                return JOYSTICK_KEY_BUCKET;
            case "回旋":
                return JOYSTICK_KEY_SWING;
            default:
                return "";
        }
    }

    public static String motionKeyToLabel(String key) {
        if (key == null) return "";
        switch (key) {
            case JOYSTICK_KEY_BOOM:
                return "大臂";
            case JOYSTICK_KEY_STICK:
                return "小臂";
            case JOYSTICK_KEY_BUCKET:
                return "铲斗";
            case JOYSTICK_KEY_SWING:
                return "回旋";
            default:
                return "";
        }
    }

    private ControllerLocalSettings() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static Snapshot load(Context context) {
        SharedPreferences sp = prefs(context);
        Snapshot s = new Snapshot();
        s.videoStreamUrl = sp.getString(KEY_VIDEO_STREAM_URL, "");

        String ab = sp.getString(KEY_JOY_LEFT_AB, "");
        String cd = sp.getString(KEY_JOY_LEFT_CD, "");
        String ef = sp.getString(KEY_JOY_RIGHT_EF, "");
        String gh = sp.getString(KEY_JOY_RIGHT_GH, "");
        String[] fixed = sanitizeJoystickMappings(ab, cd, ef, gh);
        s.joystickLeftAb = fixed[0];
        s.joystickLeftCd = fixed[1];
        s.joystickRightEf = fixed[2];
        s.joystickRightGh = fixed[3];

        s.joystickLeftAbReverse  = sp.getBoolean(KEY_JOY_LEFT_AB_REVERSE, false);
        s.joystickLeftCdReverse  = sp.getBoolean(KEY_JOY_LEFT_CD_REVERSE, false);
        s.joystickRightEfReverse = sp.getBoolean(KEY_JOY_RIGHT_EF_REVERSE, false);
        s.joystickRightGhReverse = sp.getBoolean(KEY_JOY_RIGHT_GH_REVERSE, false);
        return s;
    }

    /**
     * 系统默认通道布局：ch1/GH=铲斗、ch2/EF=大臂、ch3/AB=小臂、ch4/CD=回旋。
     */
    public static Snapshot createDefaultJoystickMappingSnapshot() {
        Snapshot s = new Snapshot();
        s.joystickLeftAb = "小臂";
        s.joystickLeftCd = "回旋";
        s.joystickRightEf = "大臂";
        s.joystickRightGh = "铲斗";
        s.joystickLeftAbReverse = false;
        s.joystickLeftCdReverse = false;
        s.joystickRightEfReverse = false;
        s.joystickRightGhReverse = false;
        return s;
    }

    public static void save(Context context, Snapshot snapshot) {
        if (snapshot == null) return;
        SharedPreferences.Editor ed = prefs(context).edit();
        ed.putInt(KEY_SCHEMA_VERSION, SCHEMA_VERSION);
        ed.putString(KEY_VIDEO_STREAM_URL, nz(snapshot.videoStreamUrl));
        ed.putString(KEY_JOY_LEFT_AB, nz(snapshot.joystickLeftAb));
        ed.putString(KEY_JOY_LEFT_CD, nz(snapshot.joystickLeftCd));
        ed.putString(KEY_JOY_RIGHT_EF, nz(snapshot.joystickRightEf));
        ed.putString(KEY_JOY_RIGHT_GH, nz(snapshot.joystickRightGh));
        ed.putBoolean(KEY_JOY_LEFT_AB_REVERSE,  snapshot.joystickLeftAbReverse);
        ed.putBoolean(KEY_JOY_LEFT_CD_REVERSE,  snapshot.joystickLeftCdReverse);
        ed.putBoolean(KEY_JOY_RIGHT_EF_REVERSE, snapshot.joystickRightEfReverse);
        ed.putBoolean(KEY_JOY_RIGHT_GH_REVERSE, snapshot.joystickRightGhReverse);
        ed.apply();
    }

    public static void saveVideoStreamUrl(Context context, String url) {
        prefs(context).edit()
                .putInt(KEY_SCHEMA_VERSION, SCHEMA_VERSION)
                .putString(KEY_VIDEO_STREAM_URL, url != null ? url.trim() : "")
                .apply();
    }

    private static String nz(String s) {
        return s != null ? s.trim() : "";
    }

    private static boolean isKnownMotion(String v) {
        if (v == null || v.isEmpty()) return false;
        for (String m : JOYSTICK_MOTION_LABELS) {
            if (m.equals(v)) return true;
        }
        return false;
    }

    /**
     * 非法值清空；同一动作多轴重复时保留顺序靠前者（AB→CD→EF→GH），与设置页互斥语义一致。
     */
    public static String[] sanitizeJoystickMappings(String ab, String cd, String ef, String gh) {
        String[] raw = new String[] { nz(ab), nz(cd), nz(ef), nz(gh) };
        String[] out = new String[4];
        Set<String> used = new HashSet<>();
        for (int i = 0; i < 4; i++) {
            String v = raw[i];
            if (v.isEmpty()) {
                out[i] = "";
                continue;
            }
            if (!isKnownMotion(v)) {
                out[i] = "";
                continue;
            }
            if (used.contains(v)) {
                out[i] = "";
                continue;
            }
            used.add(v);
            out[i] = v;
        }
        return out;
    }

    /**
     * 在 {@link #sanitizeJoystickMappings} 之后，按轴顺序把空轴依次补上未占用的动作（顺序同 {@link #JOYSTICK_MOTION_LABELS}）。
     */
    public static String[] ensureFullJoystickMappings(String ab, String cd, String ef, String gh) {
        String[] s = sanitizeJoystickMappings(ab, cd, ef, gh);
        Set<String> assigned = new HashSet<>();
        for (String v : s) {
            if (v != null && !v.isEmpty()) {
                assigned.add(v);
            }
        }
        ArrayList<String> missing = new ArrayList<>();
        for (String m : JOYSTICK_MOTION_LABELS) {
            if (!assigned.contains(m)) {
                missing.add(m);
            }
        }
        int next = 0;
        for (int i = 0; i < 4; i++) {
            if ((s[i] == null || s[i].isEmpty()) && next < missing.size()) {
                s[i] = missing.get(next++);
            }
        }
        return s;
    }
}
