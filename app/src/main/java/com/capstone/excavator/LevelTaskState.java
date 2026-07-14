package com.capstone.excavator;

/**
 * 找平任务在原生层需要保留的进程内全局数据。
 *
 * <p>任务参数使用不可变快照整体替换，保证其他页面不会读到只更新了一半的数据。</p>
 */
public final class LevelTaskState {

    public static final int REF_LEFT = 0;
    public static final int REF_MIDDLE = 1;
    public static final int REF_RIGHT = 2;

    /** WebView 提交的找平任务参数快照，长度和高程单位均为米。 */
    public static final class TaskParameters {
        public final int referencePoint;
        public final boolean heightMode;
        public final double currentReferencePointM;
        public final double targetAltitudeM;
        public final double digSizeM;
        public final String currentLongitudeAndLatitude;
        public final double targetLongitude;
        public final double targetLatitude;

        public TaskParameters(
                int referencePoint,
                boolean heightMode,
                double currentReferencePointM,
                double targetAltitudeM,
                double digSizeM,
                String currentLongitudeAndLatitude,
                double targetLongitude,
                double targetLatitude) {
            if (!Double.isFinite(targetAltitudeM)) {
                throw new IllegalArgumentException("目标高度无效");
            }
            if (!Double.isFinite(digSizeM)) {
                throw new IllegalArgumentException("填挖量无效");
            }
            this.referencePoint = normalizeReferencePoint(referencePoint);
            this.heightMode = heightMode;
            this.currentReferencePointM = currentReferencePointM;
            this.targetAltitudeM = targetAltitudeM;
            this.digSizeM = digSizeM;
            this.currentLongitudeAndLatitude = currentLongitudeAndLatitude == null
                    ? ""
                    : currentLongitudeAndLatitude.trim();
            this.targetLongitude = targetLongitude;
            this.targetLatitude = targetLatitude;
        }
    }

    private static volatile TaskParameters taskParameters;

    /** TCU 找平会话数据，不属于 Web 参数，但 workflow 需要跨应答保存。 */
    private static volatile int surveyHeightTenthCm = Integer.MIN_VALUE;
    private static volatile int acceptedTargetHeightTenthCm = Integer.MIN_VALUE;

    private LevelTaskState() {}

    public static void update(TaskParameters parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException("找平任务参数缺失");
        }
        taskParameters = parameters;
    }

    public static boolean hasTaskParameters() {
        return taskParameters != null;
    }

    public static TaskParameters getTaskParameters() {
        return taskParameters;
    }

    public static void updateSurveyResult(int heightTenthCm) {
        surveyHeightTenthCm = heightTenthCm;
    }

    public static boolean hasSurveyHeight() {
        return surveyHeightTenthCm != Integer.MIN_VALUE;
    }

    public static double getSurveyHeightM() {
        return hasSurveyHeight()
                ? TcuBusinessCodec.tenthCmToMeters(surveyHeightTenthCm)
                : Double.NaN;
    }

    public static void setAcceptedTargetHeightTenthCm(int tenthCm) {
        acceptedTargetHeightTenthCm = tenthCm;
    }

    public static boolean hasAcceptedTargetHeight() {
        return acceptedTargetHeightTenthCm != Integer.MIN_VALUE;
    }

    public static double getAcceptedTargetHeightM() {
        return hasAcceptedTargetHeight()
                ? TcuBusinessCodec.tenthCmToMeters(acceptedTargetHeightTenthCm)
                : Double.NaN;
    }

    /** 仅清理 TCU 会话，保留 Web 已提交的任务参数。 */
    public static void clearTcuSession() {
        surveyHeightTenthCm = Integer.MIN_VALUE;
        acceptedTargetHeightTenthCm = Integer.MIN_VALUE;
    }

    /** 任务结束时清理任务参数和 TCU 会话。 */
    public static void resetAll() {
        taskParameters = null;
        clearTcuSession();
    }

    private static int normalizeReferencePoint(int referencePoint) {
        if (referencePoint < REF_LEFT || referencePoint > REF_RIGHT) {
            return REF_MIDDLE;
        }
        return referencePoint;
    }
}
