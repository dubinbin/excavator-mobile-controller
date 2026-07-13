package com.capstone.excavator;

public final class ImuAngleConverter {
    private static final double PI = Math.PI;
    private static final double TWO_PI = Math.PI * 2.0;
    private static final double EPS = 1e-9;

    public static final class CylinderJointMapDimensions {
        public double boomL2;
        public double boomL3;
        public double boomL4;
        public double boomL5;
        public double boomL6;
        public double boomL7;

        public double stickL2;
        public double stickL3;
        public double stickL4;
        public double stickL5;
        public double stickL6;
        public double stickL7;

        public double bucketL2;
        public double bucketL3;
        public double bucketL4;
        public double bucketL5;
        public double bucketL6;
        public double bucketL7;
        public double bucketL9;
        public double bucketL10;

        public boolean isValidForBucket() {
            return bucketL2 > 0.0
                    && bucketL3 > 0.0
                    && bucketL4 > 0.0
                    && bucketL5 > 0.0
                    && bucketL6 > 0.0
                    && bucketL7 > 0.0
                    && bucketL9 > 0.0
                    && bucketL10 > 0.0;
        }
    }

    public static final class ImuInstallationOffset {
        public double boomImuOffsetDeg;
        public double stickImuOffsetDeg;
        public double bucketImuOffsetDeg;
    }

    public static final class Dimensions {
        public double chassisWidth;
        public double chassisLength;
        public double trackWidth;
        public double boomLength;
        public double stickLength;
        public double bucketLength;
        public double bucketAngleOffsetDeg;
    }

    public static final class Config {
        public String name = "";
        public String type = "";
        public final CylinderJointMapDimensions cylinder = new CylinderJointMapDimensions();
        public final ImuInstallationOffset imuOffsets = new ImuInstallationOffset();
        public final Dimensions dimensions = new Dimensions();

        // Matches the 0.1s compensation used in the C++ implementation.
        public double integrationDtSec = 0.1;
    }

    public static final class RelativeAngles {
        public final float boomDeg;
        public final float stickDeg;
        public final float bucketDeg;

        public RelativeAngles(float boomDeg, float stickDeg, float bucketDeg) {
            this.boomDeg = boomDeg;
            this.stickDeg = stickDeg;
            this.bucketDeg = bucketDeg;
        }
    }

    private ImuAngleConverter() {
    }

    public static Config createDefaultConfig() {
        return new Config();
    }

    /**
     * 将 UDP 中的原始串联关节角转换为统一关节角。
     * 大臂以 0° 为参考，小臂以 180° 为参考且跨 ±180°，铲斗以 90° 为参考。
     */
    public static RelativeAngles toRelativeAngles(
            float rawBoomDeg,
            float rawStickDeg,
            float rawBucketDeg,
            Config config) {
        return toRelativeAngles(rawBoomDeg, rawStickDeg, rawBucketDeg, 0f, 0f, config);
    }

    /**
     * {@code cabinPitchDeg/cabinRollDeg} 属于驾驶室姿态，不属于关节角；保留参数以保持
     * 既有调用兼容，三维坐标解算时由运动学层使用。
     */
    public static RelativeAngles toRelativeAngles(
            float rawBoomDeg,
            float rawStickDeg,
            float rawBucketDeg,
            float cabinPitchDeg,
            float cabinRollDeg,
            Config config) {
        Config cfg = (config != null) ? config : new Config();
        return new RelativeAngles(
                rawBoomDeg + (float) cfg.imuOffsets.boomImuOffsetDeg,
                (float) ArmForwardKinematics.wrap180(
                        rawStickDeg - 180.0 + cfg.imuOffsets.stickImuOffsetDeg),
                rawBucketDeg - 90.0f + (float) cfg.imuOffsets.bucketImuOffsetDeg
        );
    }

    private static double computeBucketAngleRad(double bucketLinkAbsRad, double stickAbsRad, Config cfg) {
        if (cfg == null || cfg.cylinder == null || !cfg.cylinder.isValidForBucket()) {
            return Double.NaN;
        }

        double l2 = cfg.cylinder.bucketL2;
        double l3 = cfg.cylinder.bucketL3;
        double l4 = cfg.cylinder.bucketL4;
        double l5 = cfg.cylinder.bucketL5;
        double l6 = cfg.cylinder.bucketL6;
        double l7 = cfg.cylinder.bucketL7;
        double l9 = cfg.cylinder.bucketL9;
        double l10 = cfg.cylinder.bucketL10;

        double a1 = bucketLinkAbsRad - stickAbsRad;

        double a2 = safeAcos((l4 * l4 + l3 * l3 - l5 * l5) / safeDenom(2.0 * l4 * l3));
        double a3 = safeAcos((l4 * l4 + l6 * l6 - l7 * l7) / safeDenom(2.0 * l4 * l6));
        double a6 = safeAcos((l6 * l6 + l7 * l7 - l4 * l4) / safeDenom(2.0 * l6 * l7));

        if (Double.isNaN(a2) || Double.isNaN(a3) || Double.isNaN(a6)) {
            return Double.NaN;
        }

        double a8 = a1 + a6;
        double l8Squared = l2 * l2 + l7 * l7 - 2.0 * l2 * l7 * Math.cos(a8);
        if (l8Squared <= EPS) {
            return Double.NaN;
        }
        double l8 = Math.sqrt(l8Squared);

        double a9 = safeAcos((l7 * l7 + l8 * l8 - l2 * l2) / safeDenom(2.0 * l7 * l8));
        double a10 = safeAcos((l8 * l8 + l10 * l10 - l9 * l9) / safeDenom(2.0 * l8 * l10));

        if (Double.isNaN(a9) || Double.isNaN(a10)) {
            return Double.NaN;
        }

        return PI - (a9 + a10 + a6);
    }

    private static double safeDenom(double value) {
        return Math.abs(value) < EPS ? Double.NaN : value;
    }

    private static double safeAcos(double value) {
        if (Double.isNaN(value)) {
            return Double.NaN;
        }
        if (value >= 1.0) {
            return 0.0;
        }
        if (value <= -1.0) {
            return PI;
        }
        return Math.acos(value);
    }

    private static double degToRad(double deg) {
        return deg * PI / 180.0;
    }

    private static double normalizeAngleRad(double angle) {
        while (angle < -PI || angle > PI) {
            if (angle < -PI) {
                angle += TWO_PI;
            } else if (angle > PI) {
                angle -= TWO_PI;
            }
        }
        return angle;
    }
}
