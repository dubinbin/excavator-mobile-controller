package com.capstone.excavator;

/**
 * 挖掘机臂正运动学。
 *
 * <p>原始输入不是三个构件相对地面的绝对角，而是串联关节角：大臂相对驾驶室、小臂
 * 相对大臂（180° 为参考线）以及铲斗相对小臂（90° 为参考线）。本类先将其递推为
 * 构件绝对俯仰角，再计算斗尖在大臂根铰点坐标系下的 (x, z)。详细约定见
 * {@code excavator_kinematics_knowledge_base.md}。</p>
 *
 * <p>长度输入的单位为米；角度输入的单位为度。</p>
 */
public final class ArmForwardKinematics {

    public static final class TipPosition {
        public final double x;
        public final double z;
        public TipPosition(double x, double z) {
            this.x = x;
            this.z = z;
        }
    }

    /** 一次解算得到的中间角度和斗尖位置，便于日志、标定和单元测试。 */
    public static final class Solution {
        public final double boomAbsDeg;
        public final double stickAbsDeg;
        public final double bucketAbsDeg;
        public final double tipAbsDeg;
        public final TipPosition tip;

        private Solution(double boomAbsDeg, double stickAbsDeg,
                         double bucketAbsDeg, double tipAbsDeg,
                         TipPosition tip) {
            this.boomAbsDeg = boomAbsDeg;
            this.stickAbsDeg = stickAbsDeg;
            this.bucketAbsDeg = bucketAbsDeg;
            this.tipAbsDeg = tipAbsDeg;
            this.tip = tip;
        }
    }

    private ArmForwardKinematics() {
    }

    /** 将角度归一化为 (-180, 180]，用于小臂的 180° 参考线跨象限转换。 */
    public static double wrap180(double angleDeg) {
        double result = angleDeg % 360.0;
        if (result <= -180.0) result += 360.0;
        if (result > 180.0) result -= 360.0;
        return result;
    }

    /**
     * 根据实时相对关节角计算斗尖位置。
     *
     * <pre>
     * boomJoint   = rawBoom + boomSensorOffset
     * stickJoint  = wrap180(rawStick - 180 + stickSensorOffset)
     * bucketJoint = rawBucket - 90 + bucketSensorOffset
     * boomAbs     = cabinPitch + boomJoint
     * stickAbs    = boomAbs + stickJoint
     * bucketAbs   = stickAbs + bucketJoint
     * tipAbs      = bucketAbs + bucketTipAngleOffset
     * </pre>
     *
     * {@code bucketTipAngleOffsetDeg} 是斗轴到斗尖向量的固定几何夹角，不能与铲斗
     * IMU 安装偏移混用。
     */
    public static Solution solveRelativeAngles(
            double cabinPitchDeg,
            double rawBoomDeg,
            double rawStickDeg,
            double rawBucketDeg,
            double boomSensorOffsetDeg,
            double stickSensorOffsetDeg,
            double bucketSensorOffsetDeg,
            double bucketTipAngleOffsetDeg,
            double boomLength,
            double stickLength,
            double bucketTipLength) {
        double boomJointDeg = rawBoomDeg + boomSensorOffsetDeg;
        double stickJointDeg = wrap180(rawStickDeg - 180.0 + stickSensorOffsetDeg);
        double bucketJointDeg = rawBucketDeg - 90.0 + bucketSensorOffsetDeg;

        double boomAbsDeg = cabinPitchDeg + boomJointDeg;
        double stickAbsDeg = boomAbsDeg + stickJointDeg;
        double bucketAbsDeg = stickAbsDeg + bucketJointDeg;
        double tipAbsDeg = bucketAbsDeg + bucketTipAngleOffsetDeg;

        TipPosition tip = bucketTip(
                boomAbsDeg, stickAbsDeg, tipAbsDeg,
                boomLength, stickLength, bucketTipLength);
        return new Solution(boomAbsDeg, stickAbsDeg, bucketAbsDeg, tipAbsDeg, tip);
    }

    /**
     * 仅计算斗尖 z（垂直高度，相对大臂铰接点）。
     *
     * @deprecated 参数是构件/斗尖的绝对角；实时数据请使用
     * {@link #solveRelativeAngles(double, double, double, double, double, double, double, double, double, double, double)}。
     */
    @Deprecated
    public static double bucketTipZ(double boomAbsDeg,
                                    double stickAbsDeg,
                                    double bucketAbsDeg,
                                    double boomLength,
                                    double stickLength,
                                    double bucketLength) {
        double boomAbsRad = Math.toRadians(boomAbsDeg);
        double stickAbsRad = Math.toRadians(stickAbsDeg);
        double bucketAbsRad = Math.toRadians(bucketAbsDeg);
        return boomLength * Math.sin(boomAbsRad)
                + stickLength * Math.sin(stickAbsRad)
                + bucketLength * Math.sin(bucketAbsRad);
    }

    /**
     * 根据构件/斗尖的绝对角同时返回 (x, z)。
     * 实时数据请先通过 {@link #solveRelativeAngles(double, double, double, double, double, double, double, double, double, double, double)} 转换。
     */
    public static TipPosition bucketTip(double boomAbsDeg,
                                        double stickAbsDeg,
                                        double bucketAbsDeg,
                                        double boomLength,
                                        double stickLength,
                                        double bucketLength) {
        double a1 = Math.toRadians(boomAbsDeg);
        double a12 = Math.toRadians(stickAbsDeg);
        double a123 = Math.toRadians(bucketAbsDeg);
        double x = boomLength * Math.cos(a1)
                + stickLength * Math.cos(a12)
                + bucketLength * Math.cos(a123);
        double z = boomLength * Math.sin(a1)
                + stickLength * Math.sin(a12)
                + bucketLength * Math.sin(a123);
        return new TipPosition(x, z);
    }
}
