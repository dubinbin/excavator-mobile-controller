package com.capstone.excavator;

/** 原生作业引导使用的修坡设计高程快照。 */
public final class SlopeRepairTaskState {

    private static volatile double guidanceDesignElevationM = Double.NaN;

    private SlopeRepairTaskState() {
    }

    public static void setGuidanceDesignElevationM(double elevationM) {
        guidanceDesignElevationM = elevationM;
    }

    public static double getGuidanceDesignElevationM() {
        return guidanceDesignElevationM;
    }

    public static void reset() {
        guidanceDesignElevationM = Double.NaN;
    }
}
