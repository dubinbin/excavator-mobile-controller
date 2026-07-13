package com.capstone.excavator;

/** 原生作业引导使用的挖沟设计沟底高程快照。 */
public final class DitchTaskState {

    private static volatile double guidanceTrenchBottomElevationM = Double.NaN;

    private DitchTaskState() {
    }

    public static void setGuidanceTrenchBottomElevationM(double elevationM) {
        guidanceTrenchBottomElevationM = elevationM;
    }

    public static double getGuidanceTrenchBottomElevationM() {
        return guidanceTrenchBottomElevationM;
    }

    public static void reset() {
        guidanceTrenchBottomElevationM = Double.NaN;
    }
}
