package com.capstone.excavator;

public final class BucketTipHeightState {

    private static volatile double tipHeightM = Double.NaN;
    private static volatile long updateTimeMs;

    private BucketTipHeightState() {
    }

    public static void updateTipHeightM(double heightM) {
        tipHeightM = heightM;
        updateTimeMs = System.currentTimeMillis();
    }

    public static void clear() {
        tipHeightM = Double.NaN;
        updateTimeMs = 0L;
    }

    public static boolean hasTipHeight() {
        return !Double.isNaN(tipHeightM) && !Double.isInfinite(tipHeightM);
    }

    public static double getTipHeightM() {
        return tipHeightM;
    }

    public static long getUpdateTimeMs() {
        return updateTimeMs;
    }
}
