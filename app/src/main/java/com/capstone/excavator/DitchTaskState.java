package com.capstone.excavator;

import java.util.Locale;

/**
 * 挖沟任务本地状态：A/B 点定点、填挖/目标高度、沟型与侧向参数、A'/B' 建模点。
 */
public final class DitchTaskState {

    public static final int DITCH_SQUARE = 0;
    public static final int DITCH_TRAPEZOID = 1;

    public static final int REF_LEFT = 0;
    public static final int REF_MIDDLE = 1;
    public static final int REF_RIGHT = 2;

    private static volatile int ditchType = DITCH_SQUARE;
    private static volatile int refA = REF_MIDDLE;
    private static volatile int refB = REF_MIDDLE;
    private static volatile boolean heightMode = true;
    private static volatile String abDistance = "";
    private static volatile boolean abDistanceManual;

    private static volatile String targetHeightA = "";
    private static volatile String fillCutA = "";
    private static volatile String targetLonA = "";
    private static volatile String targetLatA = "";
    private static volatile String fillCutCoordA = "";

    private static volatile String targetHeightB = "";
    private static volatile String fillCutB = "";
    private static volatile String targetLonB = "";
    private static volatile String targetLatB = "";
    private static volatile String fillCutCoordB = "";

    private static volatile double targetHeightAM = Double.NaN;
    private static volatile double fillCutAM = Double.NaN;
    private static volatile double targetHeightBM = Double.NaN;
    private static volatile double fillCutBM = Double.NaN;
    private static volatile double fillCutCoordAM = Double.NaN;
    private static volatile double fillCutCoordBM = Double.NaN;

    private static volatile int surveyAHeightTenthCm = Integer.MIN_VALUE;
    private static volatile double surveyALat = Double.NaN;
    private static volatile double surveyALon = Double.NaN;
    private static volatile double surveyATipZLocalM = Double.NaN;
    private static volatile int surveyBHeightTenthCm = Integer.MIN_VALUE;
    private static volatile double surveyBLat = Double.NaN;
    private static volatile double surveyBLon = Double.NaN;
    private static volatile double surveyBTipZLocalM = Double.NaN;

    private static volatile String longitudinalParam1 = "";
    private static volatile String longitudinalParam2 = "";
    private static volatile String longitudinalParam3 = "";
    private static volatile String longitudinalParam4 = "";
    private static volatile String sideParam1 = "";
    private static volatile String sideParam2 = "";
    private static volatile String sideParam3 = "";
    private static volatile String sideParam4 = "";
    private static volatile double guidanceDesignZLocalM = Double.NaN;

    private static volatile boolean tcuParamsAccepted;
    private static volatile boolean tcuTaskActive;

    private DitchTaskState() {
    }

    public static void updateBase(int type, int selectedRefA, int selectedRefB, String distance) {
        ditchType = normalizeDitchType(type);
        refA = normalizeRef(selectedRefA);
        refB = normalizeRef(selectedRefB);
        abDistance = safe(distance);
    }

    public static void setHeightMode(boolean height) {
        heightMode = height;
    }

    public static void setAbDistanceManual(boolean manual) {
        abDistanceManual = manual;
    }

    public static void updatePointA(
            int ref,
            String targetHeight,
            String fillCut,
            String lon,
            String lat,
            String fillCoord) {
        refA = normalizeRef(ref);
        targetHeightA = safe(targetHeight);
        fillCutA = safe(fillCut);
        targetLonA = safe(lon);
        targetLatA = safe(lat);
        fillCutCoordA = safe(fillCoord);
        targetHeightAM = parseMeters(targetHeightA);
        fillCutAM = parseMeters(fillCutA);
        fillCutCoordAM = parseMeters(fillCutCoordA);
        recomputeGuidanceDesignZLocal();
    }

    public static void updatePointB(
            int ref,
            String targetHeight,
            String fillCut,
            String lon,
            String lat,
            String fillCoord) {
        refB = normalizeRef(ref);
        targetHeightB = safe(targetHeight);
        fillCutB = safe(fillCut);
        targetLonB = safe(lon);
        targetLatB = safe(lat);
        fillCutCoordB = safe(fillCoord);
        targetHeightBM = parseMeters(targetHeightB);
        fillCutBM = parseMeters(fillCutB);
        fillCutCoordBM = parseMeters(fillCutCoordB);
        recomputeGuidanceDesignZLocal();
    }

    public static void updateSurveyA(int heightTenthCm, double lat, double lon) {
        surveyAHeightTenthCm = heightTenthCm;
        surveyALat = lat;
        surveyALon = lon;
        if (!abDistanceManual) {
            recomputeAbDistance();
        }
        recomputeGuidanceDesignZLocal();
    }

    public static void updateSurveyB(int heightTenthCm, double lat, double lon) {
        surveyBHeightTenthCm = heightTenthCm;
        surveyBLat = lat;
        surveyBLon = lon;
        if (!abDistanceManual) {
            recomputeAbDistance();
        }
        recomputeGuidanceDesignZLocal();
    }

    public static void setSurveyTipZLocal(int pointId, double zLocalM) {
        if (pointId == TcuBusinessCodec.POINT_A) {
            surveyATipZLocalM = zLocalM;
        } else if (pointId == TcuBusinessCodec.POINT_B) {
            surveyBTipZLocalM = zLocalM;
        }
        recomputeGuidanceDesignZLocal();
    }

    public static void recomputeAbDistance() {
        PrimePoint a = computePrimeA();
        PrimePoint b = computePrimeB();
        if (a == null || b == null) {
            return;
        }
        double dist = TcuBusinessCodec.horizontalDistanceM(a.lat, a.lon, b.lat, b.lon);
        abDistance = formatMeters(dist);
    }

    public static void updateLongitudinalParams(String param1, String param2, String param3, String param4) {
        longitudinalParam1 = safe(param1);
        longitudinalParam2 = safe(param2);
        longitudinalParam3 = safe(param3);
        longitudinalParam4 = safe(param4);
    }

    public static void updateSideParams(String param1, String param2, String param3, String param4) {
        sideParam1 = safe(param1);
        sideParam2 = safe(param2);
        sideParam3 = safe(param3);
        sideParam4 = safe(param4);
        recomputeGuidanceDesignZLocal();
    }

    public static void setTcuParamsAccepted(boolean accepted) {
        tcuParamsAccepted = accepted;
    }

    public static void setTcuTaskActive(boolean active) {
        tcuTaskActive = active;
    }

    public static void clearSurveyA() {
        surveyAHeightTenthCm = Integer.MIN_VALUE;
        surveyALat = Double.NaN;
        surveyALon = Double.NaN;
        surveyATipZLocalM = Double.NaN;
        recomputeGuidanceDesignZLocal();
    }

    public static void clearSurveyB() {
        surveyBHeightTenthCm = Integer.MIN_VALUE;
        surveyBLat = Double.NaN;
        surveyBLon = Double.NaN;
        surveyBTipZLocalM = Double.NaN;
        recomputeGuidanceDesignZLocal();
    }

    public static void clearTcuSession() {
        clearSurveyA();
        clearSurveyB();
        tcuParamsAccepted = false;
        tcuTaskActive = false;
    }

    public static void reset() {
        ditchType = DITCH_SQUARE;
        refA = REF_MIDDLE;
        refB = REF_MIDDLE;
        heightMode = true;
        abDistance = "";
        abDistanceManual = false;
        targetHeightA = "";
        fillCutA = "";
        targetLonA = "";
        targetLatA = "";
        fillCutCoordA = "";
        targetHeightB = "";
        fillCutB = "";
        targetLonB = "";
        targetLatB = "";
        fillCutCoordB = "";
        targetHeightAM = Double.NaN;
        fillCutAM = Double.NaN;
        targetHeightBM = Double.NaN;
        fillCutBM = Double.NaN;
        fillCutCoordAM = Double.NaN;
        fillCutCoordBM = Double.NaN;
        longitudinalParam1 = "";
        longitudinalParam2 = "";
        longitudinalParam3 = "";
        longitudinalParam4 = "";
        sideParam1 = "";
        sideParam2 = "";
        sideParam3 = "";
        sideParam4 = "";
        guidanceDesignZLocalM = Double.NaN;
        clearTcuSession();
    }

    public static boolean isHeightMode() {
        return heightMode;
    }

    public static String getModeText() {
        return heightMode ? "高度定点" : "坐标定点";
    }

    public static boolean hasSurveyA() {
        return surveyAHeightTenthCm != Integer.MIN_VALUE;
    }

    public static boolean hasSurveyB() {
        return surveyBHeightTenthCm != Integer.MIN_VALUE;
    }

    public static double getSurveyAHeightM() {
        return hasSurveyA() ? TcuBusinessCodec.tenthCmToMeters(surveyAHeightTenthCm) : Double.NaN;
    }

    public static double getSurveyBHeightM() {
        return hasSurveyB() ? TcuBusinessCodec.tenthCmToMeters(surveyBHeightTenthCm) : Double.NaN;
    }

    /**
     * 主界面填挖引导：A'/B' 建模设计面高程（米），坐标/高度模式均可用。
     */
    public static double getGuidanceDesignElevationM() {
        PrimePoint a = computePrimeA();
        PrimePoint b = computePrimeB();
        if (a != null && b != null) {
            return (a.heightM + b.heightM) / 2.0;
        }
        if (a != null) {
            return a.heightM;
        }
        if (b != null) {
            return b.heightM;
        }
        return Double.NaN;
    }

    /**
     * 与找平一致的填挖偏移（米）：快照时 {@code designZ = zTip - offset}。
     * 高度定点：A/B「目标高度−测点高度」的平均；坐标定点：A/B 填挖量（tvCoordZ）的平均。
     * 均为相对量，不可与运动学 zTip 的绝对高程混用。
     */
    public static double getGuidanceFillOffsetM() {
        double sum = 0.0;
        int count = 0;
        if (heightMode) {
            if (hasSurveyA() && !Double.isNaN(targetHeightAM)) {
                sum += targetHeightAM - getSurveyAHeightM();
                count++;
            }
            if (hasSurveyB() && !Double.isNaN(targetHeightBM)) {
                sum += targetHeightBM - getSurveyBHeightM();
                count++;
            }
        } else {
            if (!Double.isNaN(fillCutCoordAM)) {
                sum += fillCutCoordAM;
                count++;
            }
            if (!Double.isNaN(fillCutCoordBM)) {
                sum += fillCutCoordBM;
                count++;
            }
        }
        return count > 0 ? sum / count : Double.NaN;
    }

    public static boolean hasGuidanceDesignData() {
        return hasGuidanceDesignZLocal() || !Double.isNaN(getGuidanceFillOffsetM());
    }

    public static double getGuidanceTrenchBottomElevationM() {
        double design = getGuidanceDesignElevationM();
        Double depth = parseMeters(sideParam3);
        if (Double.isNaN(design) || depth == null || Double.isNaN(depth)) {
            return Double.NaN;
        }
        return design - depth;
    }

    public static boolean hasGuidanceDesignZLocal() {
        return !Double.isNaN(guidanceDesignZLocalM);
    }

    public static double getGuidanceDesignZLocalM() {
        return guidanceDesignZLocalM;
    }

    private static void recomputeGuidanceDesignZLocal() {
        Double depthM = parseMeters(sideParam3);
        if (depthM == null || Double.isNaN(depthM)) {
            guidanceDesignZLocalM = Double.NaN;
            return;
        }
        double sum = 0.0;
        int count = 0;
        Double a = computeGuidancePointDesignZLocal(
                surveyATipZLocalM,
                hasSurveyA(),
                getSurveyAHeightM(),
                targetHeightAM,
                fillCutCoordAM,
                depthM);
        if (a != null && !Double.isNaN(a)) {
            sum += a;
            count++;
        }
        Double b = computeGuidancePointDesignZLocal(
                surveyBTipZLocalM,
                hasSurveyB(),
                getSurveyBHeightM(),
                targetHeightBM,
                fillCutCoordBM,
                depthM);
        if (b != null && !Double.isNaN(b)) {
            sum += b;
            count++;
        }
        guidanceDesignZLocalM = count > 0 ? sum / count : Double.NaN;
    }

    private static Double computeGuidancePointDesignZLocal(
            double surveyTipZLocalM,
            boolean hasSurvey,
            double surveyHeightM,
            double targetHeightM,
            double coordFillOffsetM,
            double depthM) {
        if (Double.isNaN(surveyTipZLocalM)) {
            return null;
        }
        double verticalOffsetM;
        if (heightMode) {
            if (!hasSurvey || Double.isNaN(surveyHeightM) || Double.isNaN(targetHeightM)) {
                return null;
            }
            verticalOffsetM = targetHeightM - surveyHeightM;
        } else {
            if (Double.isNaN(coordFillOffsetM)) {
                return null;
            }
            verticalOffsetM = coordFillOffsetM;
        }
        return surveyTipZLocalM + verticalOffsetM - depthM;
    }

    public static boolean isPointAReady() {
        if (heightMode) {
            return hasSurveyA() && !Double.isNaN(targetHeightAM);
        }
        return isFilledNumeric(targetLonA) && isFilledNumeric(targetLatA) && isFilledNumeric(fillCutCoordA);
    }

    public static boolean isPointBReady() {
        if (heightMode) {
            return hasSurveyB() && !Double.isNaN(targetHeightBM);
        }
        return isFilledNumeric(targetLonB) && isFilledNumeric(targetLatB) && isFilledNumeric(fillCutCoordB);
    }

    public static boolean canSubmitDitchParams() {
        return isPointAReady() && isPointBReady()
                && computePrimeA() != null && computePrimeB() != null
                && !Double.isNaN(parseMeters(sideParam3));
    }

    public static boolean isTcuParamsAccepted() {
        return tcuParamsAccepted;
    }

    public static boolean isTcuTaskActive() {
        return tcuTaskActive;
    }

    /** A' 建模点；无法计算时返回 {@code null}。 */
    public static PrimePoint computePrimeA() {
        if (heightMode) {
            if (!hasSurveyA() || Double.isNaN(targetHeightAM)) {
                return null;
            }
            return new PrimePoint(surveyALat, surveyALon, targetHeightAM);
        }
        Double lat = parseMeters(targetLatA);
        Double lon = parseMeters(targetLonA);
        Double h = fillCutCoordAM;
        if (lat == null || lon == null || h == null) {
            return null;
        }
        return new PrimePoint(lat, lon, h);
    }

    public static PrimePoint computePrimeB() {
        if (heightMode) {
            if (!hasSurveyB() || Double.isNaN(targetHeightBM)) {
                return null;
            }
            return new PrimePoint(surveyBLat, surveyBLon, targetHeightBM);
        }
        Double lat = parseMeters(targetLatB);
        Double lon = parseMeters(targetLonB);
        Double h = fillCutCoordBM;
        if (lat == null || lon == null || h == null) {
            return null;
        }
        return new PrimePoint(lat, lon, h);
    }

    public static int getDitchType() {
        return ditchType;
    }

    public static int getRefA() {
        return refA;
    }

    public static int getRefB() {
        return refB;
    }

    public static boolean isSquareDitch() {
        return ditchType == DITCH_SQUARE;
    }

    public static String getDitchTypeText() {
        return isSquareDitch() ? "方形沟" : "梯形沟";
    }

    public static String getRefAText() {
        return refText(refA);
    }

    public static String getRefBText() {
        return refText(refB);
    }

    public static String getAbDistance() {
        return abDistance;
    }

    public static String getTargetHeightA() {
        return targetHeightA;
    }

    public static String getFillCutA() {
        return fillCutA;
    }

    public static String getTargetLonA() {
        return targetLonA;
    }

    public static String getTargetLatA() {
        return targetLatA;
    }

    public static String getFillCutCoordA() {
        return fillCutCoordA;
    }

    public static String getTargetHeightB() {
        return targetHeightB;
    }

    public static String getFillCutB() {
        return fillCutB;
    }

    public static String getTargetLonB() {
        return targetLonB;
    }

    public static String getTargetLatB() {
        return targetLatB;
    }

    public static String getFillCutCoordB() {
        return fillCutCoordB;
    }

    public static String getLongitudinalParam1() {
        return longitudinalParam1;
    }

    public static String getLongitudinalParam2() {
        return longitudinalParam2;
    }

    public static String getLongitudinalParam3() {
        return longitudinalParam3;
    }

    public static String getLongitudinalParam4() {
        return longitudinalParam4;
    }

    public static String getSideParam1() {
        return sideParam1;
    }

    public static String getSideParam2() {
        return sideParam2;
    }

    public static String getSideParam3() {
        return sideParam3;
    }

    public static String getSideParam4() {
        return sideParam4;
    }

    public static String formatMeters(double v) {
        String s = String.format(Locale.US, "%.2f", v);
        return s.startsWith("-") ? "−" + s.substring(1) : s;
    }

    public static final class PrimePoint {
        public final double lat;
        public final double lon;
        public final double heightM;

        PrimePoint(double lat, double lon, double heightM) {
            this.lat = lat;
            this.lon = lon;
            this.heightM = heightM;
        }
    }

    private static int normalizeDitchType(int type) {
        return type == DITCH_TRAPEZOID ? DITCH_TRAPEZOID : DITCH_SQUARE;
    }

    private static int normalizeRef(int ref) {
        if (ref < REF_LEFT || ref > REF_RIGHT) {
            return REF_MIDDLE;
        }
        return ref;
    }

    private static String refText(int ref) {
        switch (ref) {
            case REF_LEFT:
                return "左斗尖";
            case REF_RIGHT:
                return "右斗尖";
            case REF_MIDDLE:
            default:
                return "中斗尖";
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isFilledNumeric(String value) {
        if (!isUserEntered(value)) {
            return false;
        }
        return !Double.isNaN(parseMeters(value));
    }

    private static boolean isUserEntered(String value) {
        if (value == null) {
            return false;
        }
        String s = value.trim();
        return !s.isEmpty() && !"--".equals(s) && !"0".equals(s);
    }

    static Double parseMeters(String value) {
        if (value == null) {
            return null;
        }
        String s = value.trim().replace('−', '-');
        if (s.isEmpty() || s.equals("--")) {
            return null;
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
