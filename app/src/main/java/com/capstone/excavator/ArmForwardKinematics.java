package com.capstone.excavator;

/**
 * 臂正运动学：按 {@code excavator_kinematics_knowledge_base.md} §5 计算斗尖在
 * 驾驶舱本地坐标系下的 (x, z)。所有输入角度均为「相对水平面的绝对俯仰角」（度）。
 *
 * 对应公式（θ1 = boom_abs, θ1+θ2 = stick_abs, θ1+θ2+θ3 = bucket_abs）：
 * <pre>
 * x = L1·cos(θ1) + L2·cos(θ1+θ2) + L3·cos(θ1+θ2+θ3)
 * z = L1·sin(θ1) + L2·sin(θ1+θ2) + L3·sin(θ1+θ2+θ3)
 * </pre>
 * <p>
 * 连杆长度 {@code boomLength}/{@code stickLength}/{@code bucketLength} 单位为米。
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

    private ArmForwardKinematics() {
    }

    /** 仅计算斗尖 z（垂直高度，相对大臂铰接点）。 */
    public static double bucketTipZ(double boomAbsDeg,
                                    double stickAbsDeg,
                                    double bucketAbsDeg,
                                    double boomLength,
                                    double stickLength,
                                    double bucketLength) {
        double a1 = Math.toRadians(boomAbsDeg);
        double a12 = Math.toRadians(stickAbsDeg);
        double a123 = Math.toRadians(bucketAbsDeg);
        return boomLength * Math.sin(a1)
                + stickLength * Math.sin(a12)
                + bucketLength * Math.sin(a123);
    }

    /** 同时返回 (x, z)。 */
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
