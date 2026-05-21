package com.capstone.excavator;

import java.util.Locale;

public final class SlopeRepairTaskState {

    public static final int TYPE_TOP_LINE = 0;
    public static final int TYPE_BOTTOM_LINE = 1;

    public static final int REF_LEFT = 0;
    public static final int REF_MIDDLE = 1;
    public static final int REF_RIGHT = 2;

    private static volatile int repairType = TYPE_TOP_LINE;
    private static volatile int refA = REF_MIDDLE;
    private static volatile int refB = REF_MIDDLE;
    private static volatile int refC = REF_MIDDLE;
    private static volatile boolean heightMode = true;

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
    private static volatile double fillCutCoordAM = Double.NaN;
    private static volatile double targetHeightBM = Double.NaN;
    private static volatile double fillCutCoordBM = Double.NaN;

    private static volatile int surveyAHeightTenthCm = Integer.MIN_VALUE;
    private static volatile double surveyALat = Double.NaN;
    private static volatile double surveyALon = Double.NaN;
    private static volatile int surveyBHeightTenthCm = Integer.MIN_VALUE;
    private static volatile double surveyBLat = Double.NaN;
    private static volatile double surveyBLon = Double.NaN;
    private static volatile int surveyCHeightTenthCm = Integer.MIN_VALUE;
    private static volatile double surveyCLat = Double.NaN;
    private static volatile double surveyCLon = Double.NaN;

    private static volatile String abDistance = "";
    private static volatile String abLift = "";
    private static volatile String abHeightDiff = "";
    private static volatile String slopeRatio = "";
    private static volatile String verticalHeight = "";
    private static volatile String horizontalDistance = "";
    private static volatile boolean slopeDirectionRight = true;

    private static volatile boolean tcuParamsAccepted;
    private static volatile boolean tcuTaskActive;

    private SlopeRepairTaskState() {
    }

    public static void updateRepairType(int type) {
        repairType = type == TYPE_BOTTOM_LINE ? TYPE_BOTTOM_LINE : TYPE_TOP_LINE;
        tcuParamsAccepted = false;
    }

    public static void setHeightMode(boolean height) {
        heightMode = height;
        tcuParamsAccepted = false;
    }

    public static void updatePointA(int ref, String targetHeight, String fillCut,
                                    String lon, String lat, String fillCoord) {
        refA = normalizeRef(ref);
        targetHeightA = safe(targetHeight);
        fillCutA = safe(fillCut);
        targetLonA = safe(lon);
        targetLatA = safe(lat);
        fillCutCoordA = safe(fillCoord);
        targetHeightAM = parseMetersValue(targetHeightA);
        fillCutCoordAM = parseMetersValue(fillCutCoordA);
        tcuParamsAccepted = false;
    }

    public static void updatePointB(int ref, String targetHeight, String fillCut,
                                    String lon, String lat, String fillCoord) {
        refB = normalizeRef(ref);
        targetHeightB = safe(targetHeight);
        fillCutB = safe(fillCut);
        targetLonB = safe(lon);
        targetLatB = safe(lat);
        fillCutCoordB = safe(fillCoord);
        targetHeightBM = parseMetersValue(targetHeightB);
        fillCutCoordBM = parseMetersValue(fillCutCoordB);
        tcuParamsAccepted = false;
    }

    public static void updateSecondStep(int selectedRefA, int selectedRefB,
                                        String distance, String lift, String heightDiff) {
        refA = normalizeRef(selectedRefA);
        refB = normalizeRef(selectedRefB);
        abDistance = safe(distance);
        abLift = safe(lift);
        abHeightDiff = safe(heightDiff);
        tcuParamsAccepted = false;
    }

    public static void updateThirdStep(int selectedRefC, String ratio, String height,
                                       String horizontal, boolean directionRight) {
        int normalizedRefC = normalizeRef(selectedRefC);
        String nextRatio = safe(ratio);
        String nextHeight = safe(height);
        String nextHorizontal = safe(horizontal);
        boolean changed = refC != normalizedRefC
                || !slopeRatio.equals(nextRatio)
                || !verticalHeight.equals(nextHeight)
                || !horizontalDistance.equals(nextHorizontal)
                || slopeDirectionRight != directionRight;
        refC = normalizedRefC;
        slopeRatio = nextRatio;
        verticalHeight = nextHeight;
        horizontalDistance = nextHorizontal;
        slopeDirectionRight = directionRight;
        if (changed) {
            tcuParamsAccepted = false;
        }
    }

    public static void updateSurvey(int pointId, int heightTenthCm, double lat, double lon) {
        if (pointId == TcuBusinessCodec.POINT_A) {
            surveyAHeightTenthCm = heightTenthCm;
            surveyALat = lat;
            surveyALon = lon;
        } else if (pointId == TcuBusinessCodec.POINT_B) {
            surveyBHeightTenthCm = heightTenthCm;
            surveyBLat = lat;
            surveyBLon = lon;
        } else if (pointId == TcuBusinessCodec.POINT_C) {
            surveyCHeightTenthCm = heightTenthCm;
            surveyCLat = lat;
            surveyCLon = lon;
        }
        recomputeAbMetrics();
        tcuParamsAccepted = false;
    }

    public static void clearSurvey(int pointId) {
        if (pointId == TcuBusinessCodec.POINT_A) {
            surveyAHeightTenthCm = Integer.MIN_VALUE;
            surveyALat = Double.NaN;
            surveyALon = Double.NaN;
        } else if (pointId == TcuBusinessCodec.POINT_B) {
            surveyBHeightTenthCm = Integer.MIN_VALUE;
            surveyBLat = Double.NaN;
            surveyBLon = Double.NaN;
        } else if (pointId == TcuBusinessCodec.POINT_C) {
            surveyCHeightTenthCm = Integer.MIN_VALUE;
            surveyCLat = Double.NaN;
            surveyCLon = Double.NaN;
        }
        recomputeAbMetrics();
        tcuParamsAccepted = false;
    }

    public static void recomputeAbMetrics() {
        if (!hasSurvey(TcuBusinessCodec.POINT_A) || !hasSurvey(TcuBusinessCodec.POINT_B)) {
            return;
        }
        double distance = TcuBusinessCodec.horizontalDistanceM(surveyALat, surveyALon, surveyBLat, surveyBLon);
        double lift = getSurveyHeightM(TcuBusinessCodec.POINT_B) - getSurveyHeightM(TcuBusinessCodec.POINT_A);
        abDistance = formatMeters(distance);
        abLift = formatMeters(lift);
        abHeightDiff = formatMeters(Math.abs(lift));
    }

    public static void clearTcuSession() {
        clearSurvey(TcuBusinessCodec.POINT_A);
        clearSurvey(TcuBusinessCodec.POINT_B);
        clearSurvey(TcuBusinessCodec.POINT_C);
        tcuParamsAccepted = false;
        tcuTaskActive = false;
    }

    public static void reset() {
        repairType = TYPE_TOP_LINE;
        refA = REF_MIDDLE;
        refB = REF_MIDDLE;
        refC = REF_MIDDLE;
        heightMode = true;
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
        fillCutCoordAM = Double.NaN;
        targetHeightBM = Double.NaN;
        fillCutCoordBM = Double.NaN;
        abDistance = "";
        abLift = "";
        abHeightDiff = "";
        slopeRatio = "";
        verticalHeight = "";
        horizontalDistance = "";
        slopeDirectionRight = true;
        clearTcuSession();
    }

    public static boolean isHeightMode() {
        return heightMode;
    }

    public static boolean hasSurvey(int pointId) {
        if (pointId == TcuBusinessCodec.POINT_A) return surveyAHeightTenthCm != Integer.MIN_VALUE;
        if (pointId == TcuBusinessCodec.POINT_B) return surveyBHeightTenthCm != Integer.MIN_VALUE;
        if (pointId == TcuBusinessCodec.POINT_C) return surveyCHeightTenthCm != Integer.MIN_VALUE;
        return false;
    }

    public static double getSurveyHeightM(int pointId) {
        if (pointId == TcuBusinessCodec.POINT_A && hasSurvey(pointId)) {
            return TcuBusinessCodec.tenthCmToMeters(surveyAHeightTenthCm);
        }
        if (pointId == TcuBusinessCodec.POINT_B && hasSurvey(pointId)) {
            return TcuBusinessCodec.tenthCmToMeters(surveyBHeightTenthCm);
        }
        if (pointId == TcuBusinessCodec.POINT_C && hasSurvey(pointId)) {
            return TcuBusinessCodec.tenthCmToMeters(surveyCHeightTenthCm);
        }
        return Double.NaN;
    }

    public static PrimePoint computePrimeA() {
        if (heightMode) {
            if (!hasSurvey(TcuBusinessCodec.POINT_A) || Double.isNaN(targetHeightAM)) return null;
            return new PrimePoint(surveyALat, surveyALon, targetHeightAM);
        }
        Double lat = parseMeters(targetLatA);
        Double lon = parseMeters(targetLonA);
        if (lat == null || lon == null || Double.isNaN(fillCutCoordAM)) return null;
        return new PrimePoint(lat, lon, fillCutCoordAM);
    }

    public static PrimePoint computePrimeB() {
        if (heightMode) {
            if (!hasSurvey(TcuBusinessCodec.POINT_B) || Double.isNaN(targetHeightBM)) return null;
            return new PrimePoint(surveyBLat, surveyBLon, targetHeightBM);
        }
        Double lat = parseMeters(targetLatB);
        Double lon = parseMeters(targetLonB);
        if (lat == null || lon == null || Double.isNaN(fillCutCoordBM)) return null;
        return new PrimePoint(lat, lon, fillCutCoordBM);
    }

    public static PrimePoint computePrimeC() {
        if (!hasSurvey(TcuBusinessCodec.POINT_C)) return null;
        return new PrimePoint(surveyCLat, surveyCLon, getSurveyHeightM(TcuBusinessCodec.POINT_C));
    }

    public static boolean isPointReady(int pointId) {
        if (pointId == TcuBusinessCodec.POINT_A) {
            return computePrimeA() != null;
        }
        if (pointId == TcuBusinessCodec.POINT_B) {
            return computePrimeB() != null;
        }
        if (pointId == TcuBusinessCodec.POINT_C) {
            return computePrimeC() != null;
        }
        return false;
    }

    public static boolean canSubmitSlopeParams() {
        return computePrimeA() != null
                && computePrimeB() != null
                && computePrimeC() != null
                && !Double.isNaN(parseMetersValue(abDistance))
                && !Double.isNaN(parseMetersValue(abLift))
                && !Double.isNaN(parseMetersValue(slopeRatio))
                && !Double.isNaN(parseMetersValue(verticalHeight))
                && !Double.isNaN(parseMetersValue(horizontalDistance));
    }

    public static double getGuidanceDesignElevationM() {
        PrimePoint a = computePrimeA();
        PrimePoint b = computePrimeB();
        PrimePoint c = computePrimeC();
        double sum = 0.0;
        int count = 0;
        if (a != null) { sum += a.heightM; count++; }
        if (b != null) { sum += b.heightM; count++; }
        if (c != null) { sum += c.heightM; count++; }
        return count > 0 ? sum / count : Double.NaN;
    }

    public static void setTcuParamsAccepted(boolean accepted) {
        tcuParamsAccepted = accepted;
    }

    public static boolean isTcuParamsAccepted() {
        return tcuParamsAccepted;
    }

    public static void setTcuTaskActive(boolean active) {
        tcuTaskActive = active;
    }

    public static boolean isTcuTaskActive() {
        return tcuTaskActive;
    }

    public static int getRepairType() {
        return repairType;
    }

    public static String getRepairTypeText() {
        return repairType == TYPE_TOP_LINE ? "上开口线" : "下开口线";
    }

    public static int getRefA() { return refA; }
    public static int getRefB() { return refB; }
    public static int getRefC() { return refC; }
    public static String getRefAText() { return refText(refA); }
    public static String getRefBText() { return refText(refB); }
    public static String getRefCText() { return refText(refC); }
    public static String getTargetHeightA() { return targetHeightA; }
    public static String getFillCutA() { return fillCutA; }
    public static String getTargetLonA() { return targetLonA; }
    public static String getTargetLatA() { return targetLatA; }
    public static String getFillCutCoordA() { return fillCutCoordA; }
    public static String getTargetHeightB() { return targetHeightB; }
    public static String getFillCutB() { return fillCutB; }
    public static String getTargetLonB() { return targetLonB; }
    public static String getTargetLatB() { return targetLatB; }
    public static String getFillCutCoordB() { return fillCutCoordB; }
    public static String getAbDistance() { return abDistance; }
    public static String getAbLift() { return abLift; }
    public static String getAbHeightDiff() { return abHeightDiff; }
    public static String getSlopeRatio() { return slopeRatio; }
    public static String getVerticalHeight() { return verticalHeight; }
    public static String getHorizontalDistance() { return horizontalDistance; }
    public static boolean isSlopeDirectionRight() { return slopeDirectionRight; }
    public static String getSlopeDirectionText() { return slopeDirectionRight ? "右侧" : "左侧"; }

    public static String formatMeters(double v) {
        String s = String.format(Locale.US, "%.2f", v);
        return s.startsWith("-") ? "−" + s.substring(1) : s;
    }

    public static Double parseMeters(String value) {
        if (value == null) return null;
        String s = value.trim().replace('−', '-');
        if (s.isEmpty() || s.equals("--")) return null;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    static double parseMetersValue(String value) {
        Double v = parseMeters(value);
        return v == null ? Double.NaN : v;
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

    private static int normalizeRef(int ref) {
        return ref < REF_LEFT || ref > REF_RIGHT ? REF_MIDDLE : ref;
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
}
