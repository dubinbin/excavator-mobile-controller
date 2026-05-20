package com.capstone.excavator;

import java.util.Locale;

public final class LevelTaskState {

    public static final int REF_LEFT = 0;
    public static final int REF_MIDDLE = 1;
    public static final int REF_RIGHT = 2;

    private static volatile int referencePoint = REF_MIDDLE;
    private static volatile boolean heightMode = true;
    private static volatile String targetHeight = "";
    private static volatile String fillCut = "";
    private static volatile String targetLon = "";
    private static volatile String targetLat = "";
    private static volatile String targetZ = "";

    private static volatile double targetHeightM = Double.NaN;
    private static volatile double fillCutM = Double.NaN;

    // ── TCU 找平会话（0x90 / 0x91 / 0xC0）────────────────────────────
    private static volatile int surveyHeightTenthCm = Integer.MIN_VALUE;
    private static volatile double surveyLat = Double.NaN;
    private static volatile double surveyLon = Double.NaN;
    private static volatile int pendingTargetHeightTenthCm = Integer.MIN_VALUE;
    private static volatile int acceptedTargetHeightTenthCm = Integer.MIN_VALUE;
    private static volatile boolean tcuTaskActive = false;

    private LevelTaskState() {
    }

    public static void update(int ref, boolean isHeightMode, String height, String fill,
                              String lon, String lat, String z) {
        referencePoint = normalizeRef(ref);
        heightMode = isHeightMode;
        targetHeight = safe(height);
        fillCut = safe(fill);
        targetLon = safe(lon);
        targetLat = safe(lat);
        targetZ = safe(z);

        targetHeightM = parseMeters(targetHeight);
        fillCutM = parseMeters(fillCut);
    }

    public static void updateSurveyResult(int heightTenthCm, double lat, double lon) {
        surveyHeightTenthCm = heightTenthCm;
        surveyLat = lat;
        surveyLon = lon;
    }

    public static void setPendingTargetHeightTenthCm(int tenthCm) {
        pendingTargetHeightTenthCm = tenthCm;
    }

    public static void setAcceptedTargetHeightTenthCm(int tenthCm) {
        acceptedTargetHeightTenthCm = tenthCm;
    }

    public static void setTcuTaskActive(boolean active) {
        tcuTaskActive = active;
    }

    public static void clearTcuSession() {
        surveyHeightTenthCm = Integer.MIN_VALUE;
        surveyLat = Double.NaN;
        surveyLon = Double.NaN;
        pendingTargetHeightTenthCm = Integer.MIN_VALUE;
        acceptedTargetHeightTenthCm = Integer.MIN_VALUE;
        tcuTaskActive = false;
    }

    public static void resetAll() {
        referencePoint = REF_MIDDLE;
        heightMode = true;
        targetHeight = "";
        fillCut = "";
        targetLon = "";
        targetLat = "";
        targetZ = "";
        targetHeightM = Double.NaN;
        fillCutM = Double.NaN;
        clearTcuSession();
    }

    public static int getReferencePoint() {
        return referencePoint;
    }

    public static String getReferencePointText() {
        switch (referencePoint) {
            case REF_LEFT:
                return "左斗尖";
            case REF_RIGHT:
                return "右斗尖";
            case REF_MIDDLE:
            default:
                return "中斗尖";
        }
    }

    public static boolean isHeightMode() {
        return heightMode;
    }

    public static String getModeText() {
        return heightMode ? "高度定点" : "坐标定点";
    }

    public static String getTargetHeight() {
        return targetHeight;
    }

    public static String getFillCut() {
        return fillCut;
    }

    public static String getTargetLon() {
        return targetLon;
    }

    public static String getTargetLat() {
        return targetLat;
    }

    public static String getTargetZ() {
        return targetZ;
    }

    public static double getTargetHeightM() {
        return targetHeightM;
    }

    public static double getFillCutM() {
        return fillCutM;
    }

    /** @deprecated 设计高程请用 {@link #getDesignElevationM()}；TCU 偏移请用 {@link #getTargetHeightM()}（填挖量）。 */
    public static double getReferenceSumM() {
        return getDesignElevationM();
    }

    /** 高度定点：至少已填「填挖量」；设计高程由 UI 自动 = 测量值 + 填挖量。 */
    public static boolean hasNumericValues() {
        return !Double.isNaN(targetHeightM) && hasSurveyHeight();
    }

    /** 设计高程（米）= 填挖量 + 测量值，与 UI tvFillCut 一致。 */
    public static double getDesignElevationM() {
        if (Double.isNaN(targetHeightM) || !hasSurveyHeight()) {
            return Double.NaN;
        }
        return getSurveyHeightM() + targetHeightM;
    }

    public static boolean hasSurveyHeight() {
        return surveyHeightTenthCm != Integer.MIN_VALUE;
    }

    public static int getSurveyHeightTenthCm() {
        return surveyHeightTenthCm;
    }

    public static double getSurveyHeightM() {
        return hasSurveyHeight()
                ? TcuBusinessCodec.tenthCmToMeters(surveyHeightTenthCm)
                : Double.NaN;
    }

    public static double getSurveyLat() {
        return surveyLat;
    }

    public static double getSurveyLon() {
        return surveyLon;
    }

    public static boolean hasAcceptedTargetHeight() {
        return acceptedTargetHeightTenthCm != Integer.MIN_VALUE;
    }

    /** TCU 0x91 确认的设计面高度（米）；无则 NaN。 */
    public static double getAcceptedTargetHeightM() {
        return hasAcceptedTargetHeight()
                ? TcuBusinessCodec.tenthCmToMeters(acceptedTargetHeightTenthCm)
                : Double.NaN;
    }

    public static String getAcceptedTargetHeightText() {
        if (!hasAcceptedTargetHeight()) {
            return "--";
        }
        return String.format(Locale.US, "%.3f", getAcceptedTargetHeightM());
    }

    public static boolean isTcuTaskActive() {
        return tcuTaskActive;
    }

    private static int normalizeRef(int ref) {
        if (ref < REF_LEFT || ref > REF_RIGHT) {
            return REF_MIDDLE;
        }
        return ref;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static double parseMeters(String value) {
        if (value == null) return Double.NaN;
        String s = value.trim().replace('−', '-');
        if (s.isEmpty() || s.equals("--")) return Double.NaN;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
