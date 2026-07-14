package com.capstone.excavator;

/**
 * 修坡任务在原生层需要保留的进程内全局数据。
 *
 * <p>与 {@link LevelTaskState}、{@link DitchTaskState} 一致，任务参数使用不可变快照整体替换。</p>
 */
public final class SlopeRepairTaskState {

    /** A/B/C 点快照，坐标单位为度，高程单位为米。 */
    public static final class Point {
        public final double latitude;
        public final double longitude;
        public final double heightM;

        public Point(double latitude, double longitude, double heightM) {
            if (!areFinite(latitude, longitude, heightM)) {
                throw new IllegalArgumentException("修坡测点参数无效");
            }
            this.latitude = latitude;
            this.longitude = longitude;
            this.heightM = heightM;
        }
    }

    /** WebView 提交的修坡任务参数快照，长度和高程单位均为米。 */
    public static final class TaskParameters {
        public final int repairType;
        public final Point pointA;
        public final Point pointB;
        public final Point pointC;
        public final double verticalHeightM;
        public final double horizontalDistanceM;
        public final double abDistanceM;
        public final double abHeightDifferenceM;
        public final double slopeAngle;
        public final String slopeDirection;

        /** TCU 需要的坡比：水平距离 / 垂直高度。 */
        public final double slopeRatio;
        /** TCU 需要的 AB 高差：B 点高程 - A 点高程。 */
        public final double abLiftM;

        public TaskParameters(
                int repairType,
                Point pointA,
                Point pointB,
                Point pointC,
                double verticalHeightM,
                double horizontalDistanceM,
                double abDistanceM,
                double abHeightDifferenceM,
                double slopeRatio,
                double slopeAngle,
                String slopeDirection) {
            if (repairType != SlopeRepairTcuWorkflow.TYPE_TOP_LINE
                    && repairType != SlopeRepairTcuWorkflow.TYPE_BOTTOM_LINE) {
                throw new IllegalArgumentException("修坡类型无效");
            }
            if (pointA == null || pointB == null || pointC == null) {
                throw new IllegalArgumentException("修坡 A/B/C 点缺失");
            }
            if (!areFinite(verticalHeightM, horizontalDistanceM, abDistanceM,
                    abHeightDifferenceM, slopeRatio, slopeAngle)) {
                throw new IllegalArgumentException("修坡尺寸参数无效");
            }
            this.repairType = repairType;
            this.pointA = pointA;
            this.pointB = pointB;
            this.pointC = pointC;
            this.verticalHeightM = verticalHeightM;
            this.horizontalDistanceM = horizontalDistanceM;
            this.abDistanceM = abDistanceM;
            this.abHeightDifferenceM = abHeightDifferenceM;
            this.slopeAngle = slopeAngle;
            this.slopeDirection = slopeDirection == null ? "" : slopeDirection.trim();
            this.slopeRatio = slopeRatio;
            this.abLiftM = abHeightDifferenceM;
        }
    }

    private static volatile TaskParameters taskParameters;

    private SlopeRepairTaskState() {}

    public static void update(TaskParameters parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException("修坡任务参数缺失");
        }
        taskParameters = parameters;
    }

    public static boolean hasTaskParameters() {
        return taskParameters != null;
    }

    public static TaskParameters getTaskParameters() {
        return taskParameters;
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
