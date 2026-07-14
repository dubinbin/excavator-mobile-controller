package com.capstone.excavator;

/**
 * 挖沟任务在原生层需要保留的进程内全局数据。
 *
 * <p>与 {@link LevelTaskState} 一致，任务参数使用不可变快照整体替换。</p>
 */
public final class DitchTaskState {

    /** A/B 点快照，坐标单位为度，高程单位为米。 */
    public static final class Point {
        public final double latitude;
        public final double longitude;
        public final double heightM;

        public Point(double latitude, double longitude, double heightM) {
            if (!areFinite(latitude, longitude, heightM)) {
                throw new IllegalArgumentException("挖沟测点参数无效");
            }
            this.latitude = latitude;
            this.longitude = longitude;
            this.heightM = heightM;
        }
    }

    /** WebView 提交的挖沟任务参数快照，长度单位均为米。 */
    public static final class TaskParameters {
        public final int ditchType;
        public final Point pointA;
        public final Point pointB;
        public final double abDistanceM;
        public final double depthM;
        public final double leftWidthM;
        public final double rightWidthM;
        public final double topWidthM;

        public TaskParameters(
                int ditchType,
                Point pointA,
                Point pointB,
                double abDistanceM,
                double depthM,
                double leftWidthM,
                double rightWidthM,
                double topWidthM) {
            if (ditchType != DitchTcuWorkflow.DITCH_SQUARE
                    && ditchType != DitchTcuWorkflow.DITCH_TRAPEZOID) {
                throw new IllegalArgumentException("挖沟沟型无效");
            }
            if (pointA == null || pointB == null) {
                throw new IllegalArgumentException("挖沟 A/B 点缺失");
            }
            if (!areFinite(abDistanceM, depthM, leftWidthM, rightWidthM, topWidthM)) {
                throw new IllegalArgumentException("挖沟尺寸参数无效");
            }
            this.ditchType = ditchType;
            this.pointA = pointA;
            this.pointB = pointB;
            this.abDistanceM = abDistanceM;
            this.depthM = depthM;
            this.leftWidthM = leftWidthM;
            this.rightWidthM = rightWidthM;
            this.topWidthM = topWidthM;
        }

    }

    private static volatile TaskParameters taskParameters;

    private DitchTaskState() {}

    public static void update(TaskParameters parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException("挖沟任务参数缺失");
        }
        taskParameters = parameters;
    }

    public static boolean hasTaskParameters() {
        return taskParameters != null;
    }

    public static TaskParameters getTaskParameters() {
        return taskParameters;
    }

    /**
     * 当前临时二维引导使用的沟底高程：A/B 点平均高程减去设计深度。
     */
    public static double getGuidanceTrenchBottomElevationM() {
        TaskParameters parameters = taskParameters;
        if (parameters == null) {
            return Double.NaN;
        }
        return (parameters.pointA.heightM + parameters.pointB.heightM) / 2.0
                - parameters.depthM;
    }

    public static void resetAll() {
        taskParameters = null;
    }

    /** 保留旧调用名。 */
    public static void reset() {
        resetAll();
    }

    private static boolean areFinite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                return false;
            }
        }
        return true;
    }
}
