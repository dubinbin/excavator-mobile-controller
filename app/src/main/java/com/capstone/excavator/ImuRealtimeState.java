package com.capstone.excavator;

import android.content.Context;

public final class ImuRealtimeState {

    private static volatile boolean valid = false;
    private static volatile float boomAngleDeg;
    private static volatile float stickAngleDeg;
    private static volatile float bucketAngleDeg;
    private static volatile float cabinPitchDeg;
    private static volatile float cabinRollDeg;
    private static volatile long updateTimeMs;

    private ImuRealtimeState() {
    }

    public static void update(float boomDeg, float stickDeg, float bucketDeg,
                              float cabinPitch, float cabinRoll) {
        boomAngleDeg = boomDeg;
        stickAngleDeg = stickDeg;
        bucketAngleDeg = bucketDeg;
        cabinPitchDeg = cabinPitch;
        cabinRollDeg = cabinRoll;
        updateTimeMs = System.currentTimeMillis();
        valid = true;
    }

    public static void clear() {
        valid = false;
        boomAngleDeg = 0f;
        stickAngleDeg = 0f;
        bucketAngleDeg = 0f;
        cabinPitchDeg = 0f;
        cabinRollDeg = 0f;
        updateTimeMs = 0L;
    }

    public static boolean isValid() {
        return valid;
    }

    public static float getBoomAngleDeg() {
        return boomAngleDeg;
    }

    public static float getStickAngleDeg() {
        return stickAngleDeg;
    }

    public static float getBucketAngleDeg() {
        return bucketAngleDeg;
    }

    public static float getCabinPitchDeg() {
        return cabinPitchDeg;
    }

    public static float getCabinRollDeg() {
        return cabinRollDeg;
    }

    public static long getUpdateTimeMs() {
        return updateTimeMs;
    }

    public static Double currentBucketTipZLocalM(Context context) {
        if (!isValid() || context == null) {
            return null;
        }
        ImuPreferences.Params p = ImuPreferences.load(context);
        if (p.boomLength <= 0 || p.stickLength <= 0 || p.bucketLength <= 0) {
            return null;
        }
        double boomAbsDeg = boomAngleDeg + p.boomImuOffsetDeg;
        double stickAbsDeg = stickAngleDeg + p.stickImuOffsetDeg;
        double bucketAbsDeg = bucketAngleDeg + p.bucketImuOffsetDeg;
        return ArmForwardKinematics.bucketTipZ(
                boomAbsDeg,
                stickAbsDeg,
                bucketAbsDeg,
                ImuPreferences.lengthMmToMeters(p.boomLength),
                ImuPreferences.lengthMmToMeters(p.stickLength),
                ImuPreferences.lengthMmToMeters(p.bucketLength));
    }
}
