package com.capstone.excavator;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Persists all IMU angle-calculation parameters to SharedPreferences.
 * Default values match the hard-coded values in MainActivity.initImuAngleConfig().
 */
public final class ImuPreferences {

    private static final String PREFS_NAME = "imu_config_prefs";

    // ── Defaults (exact values from initImuAngleConfig) ──────────────────────

    public static final double D_BOOM_LENGTH              = 2207.86;
    public static final double D_STICK_LENGTH             = 1261.0;
    public static final double D_BUCKET_LENGTH            = 709.164447;
    public static final double D_BUCKET_ANGLE_OFFSET_DEG  = Math.toDegrees(-1.566985);

    public static final double D_BOOM_L2 = 1191.0;
    public static final double D_BOOM_L3 = 308.0;
    public static final double D_BOOM_L4 = 275.0;
    public static final double D_BOOM_L5 = 140.0;
    public static final double D_BOOM_L6 = 2207.86;
    public static final double D_BOOM_L7 = 1079.0;

    public static final double D_STICK_L2 = 1104.0;
    public static final double D_STICK_L3 = 286.0;
    public static final double D_STICK_L4 = 1464.0;
    public static final double D_STICK_L5 = 2207.86;
    public static final double D_STICK_L6 = 1261.0;
    public static final double D_STICK_L7 = 1521.37;

    public static final double D_BUCKET_L2  = 281.0;
    public static final double D_BUCKET_L3  = 1082.0;
    public static final double D_BUCKET_L4  = 1085.0;
    public static final double D_BUCKET_L5  = 261.57;
    public static final double D_BUCKET_L6  = 1261.0;
    public static final double D_BUCKET_L7  = 176.0;
    public static final double D_BUCKET_L9  = 256.0;
    public static final double D_BUCKET_L10 = 200.0;

    public static final double D_BOOM_IMU_OFFSET_DEG   = Math.toDegrees(-0.4578);
    public static final double D_STICK_IMU_OFFSET_DEG  = 0.0;
    public static final double D_BUCKET_IMU_OFFSET_DEG = 0.0;

    // ── Param container ──────────────────────────────────────────────────────

    public static final class Params {
        public double boomLength             = D_BOOM_LENGTH;
        public double stickLength            = D_STICK_LENGTH;
        public double bucketLength           = D_BUCKET_LENGTH;
        public double bucketAngleOffsetDeg   = D_BUCKET_ANGLE_OFFSET_DEG;

        public double boomL2 = D_BOOM_L2;
        public double boomL3 = D_BOOM_L3;
        public double boomL4 = D_BOOM_L4;
        public double boomL5 = D_BOOM_L5;
        public double boomL6 = D_BOOM_L6;
        public double boomL7 = D_BOOM_L7;

        public double stickL2 = D_STICK_L2;
        public double stickL3 = D_STICK_L3;
        public double stickL4 = D_STICK_L4;
        public double stickL5 = D_STICK_L5;
        public double stickL6 = D_STICK_L6;
        public double stickL7 = D_STICK_L7;

        public double bucketL2  = D_BUCKET_L2;
        public double bucketL3  = D_BUCKET_L3;
        public double bucketL4  = D_BUCKET_L4;
        public double bucketL5  = D_BUCKET_L5;
        public double bucketL6  = D_BUCKET_L6;
        public double bucketL7  = D_BUCKET_L7;
        public double bucketL9  = D_BUCKET_L9;
        public double bucketL10 = D_BUCKET_L10;

        public double boomImuOffsetDeg   = D_BOOM_IMU_OFFSET_DEG;
        public double stickImuOffsetDeg  = D_STICK_IMU_OFFSET_DEG;
        public double bucketImuOffsetDeg = D_BUCKET_IMU_OFFSET_DEG;
    }

    private ImuPreferences() {}

    // ── Load / Save ──────────────────────────────────────────────────────────

    public static Params load(Context context) {
        SharedPreferences sp = prefs(context);
        Params p = new Params();

        p.boomLength           = getDouble(sp, "boom_length",            D_BOOM_LENGTH);
        p.stickLength          = getDouble(sp, "stick_length",           D_STICK_LENGTH);
        p.bucketLength         = getDouble(sp, "bucket_length",          D_BUCKET_LENGTH);
        p.bucketAngleOffsetDeg = getDouble(sp, "bucket_angle_offset_deg",D_BUCKET_ANGLE_OFFSET_DEG);

        p.boomL2 = getDouble(sp, "boom_l2", D_BOOM_L2);
        p.boomL3 = getDouble(sp, "boom_l3", D_BOOM_L3);
        p.boomL4 = getDouble(sp, "boom_l4", D_BOOM_L4);
        p.boomL5 = getDouble(sp, "boom_l5", D_BOOM_L5);
        p.boomL6 = getDouble(sp, "boom_l6", D_BOOM_L6);
        p.boomL7 = getDouble(sp, "boom_l7", D_BOOM_L7);

        p.stickL2 = getDouble(sp, "stick_l2", D_STICK_L2);
        p.stickL3 = getDouble(sp, "stick_l3", D_STICK_L3);
        p.stickL4 = getDouble(sp, "stick_l4", D_STICK_L4);
        p.stickL5 = getDouble(sp, "stick_l5", D_STICK_L5);
        p.stickL6 = getDouble(sp, "stick_l6", D_STICK_L6);
        p.stickL7 = getDouble(sp, "stick_l7", D_STICK_L7);

        p.bucketL2  = getDouble(sp, "bucket_l2",  D_BUCKET_L2);
        p.bucketL3  = getDouble(sp, "bucket_l3",  D_BUCKET_L3);
        p.bucketL4  = getDouble(sp, "bucket_l4",  D_BUCKET_L4);
        p.bucketL5  = getDouble(sp, "bucket_l5",  D_BUCKET_L5);
        p.bucketL6  = getDouble(sp, "bucket_l6",  D_BUCKET_L6);
        p.bucketL7  = getDouble(sp, "bucket_l7",  D_BUCKET_L7);
        p.bucketL9  = getDouble(sp, "bucket_l9",  D_BUCKET_L9);
        p.bucketL10 = getDouble(sp, "bucket_l10", D_BUCKET_L10);

        p.boomImuOffsetDeg   = getDouble(sp, "boom_imu_offset_deg",   D_BOOM_IMU_OFFSET_DEG);
        p.stickImuOffsetDeg  = getDouble(sp, "stick_imu_offset_deg",  D_STICK_IMU_OFFSET_DEG);
        p.bucketImuOffsetDeg = getDouble(sp, "bucket_imu_offset_deg", D_BUCKET_IMU_OFFSET_DEG);

        return p;
    }

    public static void save(Context context, Params p) {
        SharedPreferences.Editor e = prefs(context).edit();

        putDouble(e, "boom_length",            p.boomLength);
        putDouble(e, "stick_length",           p.stickLength);
        putDouble(e, "bucket_length",          p.bucketLength);
        putDouble(e, "bucket_angle_offset_deg",p.bucketAngleOffsetDeg);

        putDouble(e, "boom_l2", p.boomL2);
        putDouble(e, "boom_l3", p.boomL3);
        putDouble(e, "boom_l4", p.boomL4);
        putDouble(e, "boom_l5", p.boomL5);
        putDouble(e, "boom_l6", p.boomL6);
        putDouble(e, "boom_l7", p.boomL7);

        putDouble(e, "stick_l2", p.stickL2);
        putDouble(e, "stick_l3", p.stickL3);
        putDouble(e, "stick_l4", p.stickL4);
        putDouble(e, "stick_l5", p.stickL5);
        putDouble(e, "stick_l6", p.stickL6);
        putDouble(e, "stick_l7", p.stickL7);

        putDouble(e, "bucket_l2",  p.bucketL2);
        putDouble(e, "bucket_l3",  p.bucketL3);
        putDouble(e, "bucket_l4",  p.bucketL4);
        putDouble(e, "bucket_l5",  p.bucketL5);
        putDouble(e, "bucket_l6",  p.bucketL6);
        putDouble(e, "bucket_l7",  p.bucketL7);
        putDouble(e, "bucket_l9",  p.bucketL9);
        putDouble(e, "bucket_l10", p.bucketL10);

        putDouble(e, "boom_imu_offset_deg",   p.boomImuOffsetDeg);
        putDouble(e, "stick_imu_offset_deg",  p.stickImuOffsetDeg);
        putDouble(e, "bucket_imu_offset_deg", p.bucketImuOffsetDeg);

        e.apply();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static double getDouble(SharedPreferences sp, String key, double def) {
        String v = sp.getString(key, null);
        if (v == null) return def;
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static void putDouble(SharedPreferences.Editor e, String key, double val) {
        e.putString(key, String.valueOf(val));
    }

    /** Parse a user-entered string to double; returns {@code def} on failure. */
    public static double parseOrDefault(String s, double def) {
        if (s == null) return def;
        String t = s.trim().replace(',', '.');
        if (t.isEmpty()) return def;
        try {
            double v = Double.parseDouble(t);
            return Double.isFinite(v) ? v : def;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** {@link Params#boomLength} 等连杆长度在偏好设置里以毫米存储，正运动学使用米。 */
    public static double lengthMmToMeters(double lengthMm) {
        return lengthMm / 1000.0;
    }

    /** Format a double for display in an EditText (no unnecessary trailing zeros). */
    public static String fmt(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) {
            return String.valueOf((long) v);
        }
        return String.valueOf(v);
    }
}
